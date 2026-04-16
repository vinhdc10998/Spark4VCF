error id: file://<WORKSPACE>/src/main/scala/extprg/pypgx/pypgx.scala:deleteRecursively.
file://<WORKSPACE>/src/main/scala/extprg/pypgx/pypgx.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/sys/process.
	 -scala/sys/process#
	 -scala/sys/process().
	 -scala/Predef.
	 -scala/Predef#
	 -scala/Predef().
offset: 7118
uri: file://<WORKSPACE>/src/main/scala/extprg/pypgx/pypgx.scala
text:
```scala
package extprg.pypgx

import org.apache.spark.SparkContext
import org.apache.hadoop.fs.{FileSystem, Path}
import java.io.{File, PrintWriter}
import utils.CustomOperators.RDDOperators

object PYPGX {

  /**
   * Parses the value of a named flag from an argument string.
   * e.g. parseFlag("--variants", "--variants /data/sample.vcf.gz --assembly GRCh38") => Some("/data/sample.vcf.gz")
   */
  private def parseFlag(flag: String, args: String): Option[String] = {
    val tokens = args.split("\\s+")
    val idx = tokens.indexOf(flag)
    if (idx >= 0 && idx + 1 < tokens.length) Some(tokens(idx + 1)) else None
  }

  /**
   * Parses the 2nd positional argument (output directory) from the tool args.
   * PyPGX syntax: pypgx run-ngs-pipeline <GENE> <OUTPUT_DIR> --variants ...
   * TOOL_ARGS received here (tool name stripped): run-ngs-pipeline <GENE> <OUTPUT_DIR> --variants ...
   * So index 2 (0-based) is the output dir.
   */
  private def parsePositionalOutput(args: String): Option[String] = {
    // Positional tokens: those that don't start with '--' and are not values of a flag
    val tokens = args.split("\\s+").toList
    val positionals = tokens.zipWithIndex.filterNot { case (t, i) =>
      t.startsWith("-") || (i > 0 && tokens(i - 1).startsWith("-"))
    }.map(_._1)
    // positionals(0) = "run-ngs-pipeline", positionals(1) = GENE, positionals(2) = OUTPUT_DIR
    if (positionals.length >= 3) Some(positionals(2)) else None
  }

  /**
   * Parses the 3rd positional argument (VCF file) from the tool args.
   */
  private def parsePositionalVcf(args: String): Option[String] = {
    val tokens = args.split("\\s+").toList
    val positionals = tokens.zipWithIndex.filterNot { case (t, i) =>
      t.startsWith("-") || (i > 0 && tokens(i - 1).startsWith("-"))
    }.map(_._1)
    // positionals(0) = pipeline, positionals(1) = GENE, positionals(2) = OUTPUT_DIR, positionals(3) = VCF
    if (positionals.length >= 4) Some(positionals(3)) else None
  }

  def annotateByPypgx(sc: SparkContext, pyPGXArgs: String, execDir: String): Unit = {

    // --- Parse local input/output from tool args ---
    val localVariantsPath = parsePositionalVcf(pyPGXArgs)
      .getOrElse {
        throw new IllegalArgumentException("[PyPGX] Missing positional VCF path in tool args")
      }
    val localOutputDir = parsePositionalOutput(pyPGXArgs).getOrElse {
      throw new IllegalArgumentException("[PyPGX] Could not determine output directory from positional args")
    }

    println(s"[PyPGX] Local variants input: $localVariantsPath")
    println(s"[PyPGX] Local output dir:     $localOutputDir")
    new File(localOutputDir).mkdirs()

    // --- HDFS paths ---
    val ts = System.currentTimeMillis()
    val hdfs = FileSystem.get(sc.hadoopConfiguration)
    val variantsFileName = new File(localVariantsPath).getName
    val hdfsVariantsPath = new Path(s"/tmp/spark4vcf_pypgx_$ts/$variantsFileName")

    println(s"[PyPGX] Uploading $localVariantsPath -> hdfs:${hdfsVariantsPath.toString}")
    hdfs.copyFromLocalFile(
      false, true,
      new Path("file://" + new File(localVariantsPath).getAbsolutePath),
      hdfsVariantsPath
    )

    // Also upload the .tbi index if present
    val localIndexPath = localVariantsPath + ".csi"
    val hdfsHasIndex = new File(localIndexPath).exists()
    if (hdfsHasIndex) {
      val hdfsIndexPath = new Path(hdfsVariantsPath.toString + ".csi")
      println(s"[PyPGX] Uploading index $localIndexPath -> hdfs:${hdfsIndexPath.toString}")
      hdfs.copyFromLocalFile(false, true,
        new Path("file://" + new File(localIndexPath).getAbsolutePath),
        hdfsIndexPath)
    }

    // --- Get sample list from the local VCF using bcftools ---
    import scala.sys.process._
    println(s"[PyPGX] Extracting sample list from $localVariantsPath using bcftools...")
    val samplesCommand = s"bcftools query -l $localVariantsPath"
    val samples = samplesCommand.!!.split("\n").filter(_.nonEmpty)
    println(s"[PyPGX] Found ${samples.length} samples.")

    val numberOfSamples: Long = 2
    val numberOfPartitions = math.max(1, (samples.length / numberOfSamples).toInt)

    // Write per-batch sample lists locally
    val localTmpDir = s"/data/tmp/spark4vcf_pypgx_local_$ts"
    new File(localTmpDir).mkdirs()

    val batches = samples.sliding(numberOfSamples.toInt, numberOfSamples.toInt).toList
    batches.zipWithIndex.foreach { case (batch, i) =>
      val pw = new PrintWriter(new File(s"$localTmpDir/tmpSample_$i.txt"))
      pw.write(batch.mkString("\n"))
      pw.close()
    }

    // Each element: (partitionIndex, sampleFilePath)
    val indexedSampleFiles = List.range(0, batches.size).map(i => (i, s"$localTmpDir/tmpSample_$i.txt"))
    val p = sc.makeRDD(indexedSampleFiles).repartition(numberOfPartitions)

    // --- Build per-task unique output dirs to avoid concurrent writes to localOutputDir ---
    // localOutputDir is a driver-side path; passing it verbatim to all executors would cause
    // every task to write to the same directory simultaneously.
    // Instead, each task writes to its own tmp dir; results are merged on the driver afterwards.
    val taskOutputBase = s"${localOutputDir}_task_$ts"

    println(s"[PyPGX] Executing pypgx on ${batches.size} partition(s); per-task tmp base: $taskOutputBase")
    new File(localOutputDir).mkdirs()

    // Run pypgx on each partition, replacing localOutputDir with a unique per-task path.
    val taskOutputDirs: Array[String] = p.mapPartitionsWithIndex { case (partIdx, iter) =>
      import scala.sys.process._
      val taskOutDir = s"$taskOutputBase/$partIdx"
      new File(taskOutDir).mkdirs()

      iter.flatMap { case (batchIdx, sampleFile) =>
        // Replace the shared localOutputDir token in the args with the per-task dir.
        val taskArgs = pyPGXArgs.replace(localOutputDir, taskOutDir)
        val cmd = s"/spark4vcf/.pixi/envs/default/bin/pypgx $taskArgs --samples $sampleFile"
        val exitCode = Process(cmd).!
        if (exitCode != 0)
          throw new RuntimeException(s"[PyPGX] Task $partIdx (batch $batchIdx) failed with exit code $exitCode")
        Iterator(taskOutDir)
      }
    }.distinct().collect()

    // --- Merge per-task outputs into the final localOutputDir ---
    println(s"[PyPGX] Merging ${taskOutputDirs.length} task output(s) into $localOutputDir")
    taskOutputDirs.foreach { taskDir =>
      val src = new File(taskDir)
      if (src.exists()) {
        src.listFiles().foreach { f =>
          import java.nio.file.{Files, StandardCopyOption}
          val dest = new File(localOutputDir, f.getName)
          Files.move(f.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }

    // Cleanup per-task tmp base dir
    import scala.reflect.io.Directory
    new Directory(new File(taskOutputBase)).deleteRecursively()

    // --- Cleanup HDFS tmp ---
    println(s"[PyPGX] Cleaning up HDFS tmp at /tmp/spark4vcf_pypgx_$ts")
    hdfs.delete(new Path(s"/tmp/spark4vcf_pypgx_$ts"), true)

    // Cleanup local tmp (sample list files)
    new scala.reflect.io.Directory(new File(localTmpDir)).d@@eleteRecursively()

    println(s"[PyPGX] Done. Output at: $localOutputDir")
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 