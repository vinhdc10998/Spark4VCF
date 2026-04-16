error id: file://<WORKSPACE>/src/main/scala/extprg/pypgx/pypgx.scala:
file://<WORKSPACE>/src/main/scala/extprg/pypgx/pypgx.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -numberOfPartitions.
	 -numberOfPartitions#
	 -numberOfPartitions().
	 -scala/Predef.numberOfPartitions.
	 -scala/Predef.numberOfPartitions#
	 -scala/Predef.numberOfPartitions().
offset: 3846
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

  def annotateByPypgx(sc: SparkContext, pyPGXArgs: String, execDir: String): Unit = {

    // --- Parse local input/output from tool args ---
    val localVariantsPath = parseFlag("--variants", pyPGXArgs).getOrElse {
      throw new IllegalArgumentException("[PyPGX] Missing --variants <path> in tool args")
    }
    val localOutputDir = parsePositionalOutput(pyPGXArgs).getOrElse {
      throw new IllegalArgumentException("[PyPGX] Could not determine output directory from positional args")
    }

    println(s"[PyPGX] Local variants input: $localVariantsPath")
    println(s"[PyPGX] Local output dir:     $localOutputDir")

    // --- HDFS paths ---
    val ts = System.currentTimeMillis()
    val hdfs = FileSystem.get(sc.hadoopConfiguration)
    val variantsFileName = new File(localVariantsPath).getName
    val hdfsVariantsPath = new Path(s"/tmp/spark4vcf_pypgx_$ts/$variantsFileName")

    // --- Upload local variants VCF to HDFS ---
    println(s"[PyPGX] Uploading $localVariantsPath → hdfs:${hdfsVariantsPath.toString}")
    hdfs.copyFromLocalFile(
      false, true,
      new Path("file://" + new File(localVariantsPath).getAbsolutePath),
      hdfsVariantsPath
    )

    // --- Rewrite --variants to HDFS path in args ---
    val adjustedArgs = pyPGXArgs.replace(
      s"--variants $localVariantsPath",
      s"--variants ${hdfsVariantsPath.toString}"
    )
    val annotateCmd = execDir + " " + adjustedArgs

    // --- Read variants VCF from HDFS to get sample list ---
    val dataRDD = sc.textFile(hdfsVariantsPath.toString)
    val (headerRDD, _) = dataRDD.filterDivisor(line => line.startsWith("#"))
    val header = headerRDD.coalesce(1).collect().last
    val samples = header.split("\t").drop(9)

    val numberOfSamples: Long = 10
    val numberOfPartitions = math.max(1, (samples.length / numberOfSamples).toInt)

    // Write per-batch sample lists locally
    val localTmpDir = s"/tmp/spark4vcf_pypgx_local_$ts"
    new File(localTmpDir).mkdirs()

    val batches = samples.sliding(numberOfSamples.toInt, numberOfSamples.toInt).toList
    batches.zipWithIndex.foreach { case (batch, i) =>
      val pw = new PrintWriter(new File(s"$localTmpDir/tmpSample_$i.txt"))
      pw.write(batch.mkString("\n"))
      pw.close()
    }

    val p = sc.makeRDD(List.range(0, batches.size).map(i => s"$localTmpDir/tmpSample_$i.txt"))
               .repartition(numberOfPartitio@@ns)

    // Collect output locally — PyPGX writes its own output directory per run
    new File(localOutputDir).mkdirs()
    p.pipe(annotateCmd).collect()

    // --- Cleanup HDFS tmp ---
    println(s"[PyPGX] Cleaning up HDFS tmp at /tmp/spark4vcf_pypgx_$ts")
    hdfs.delete(new Path(s"/tmp/spark4vcf_pypgx_$ts"), true)

    // Cleanup local tmp
    import scala.reflect.io.Directory
    new Directory(new File(localTmpDir)).deleteRecursively()

    println(s"[PyPGX] Done. Output at: $localOutputDir")
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 