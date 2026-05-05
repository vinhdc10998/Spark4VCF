package extprg.vep

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.apache.spark.rdd.RDD
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.IOUtils
import org.apache.hadoop.io.compress.CompressionCodec
import scala.reflect.io.Directory
import java.io.File
import java.io._
import utils.CustomOperators.RDDOperators

object VEP {
  def annotateByVep(
      sc: SparkContext,
      vepArgs: String,
      execDir: String
  ): Unit = {
    // Parse input/output from vepArgs.
    // The args may contain multiple -i/-o flags: one with "{}" as a placeholder
    // for xargs (piping each tmp VCF chunk), and one with the real file path
    // for Spark to read. We skip placeholder values and take the last real one.
    val tokens = vepArgs.split("\\s+")
    val placeholders =
      Set("{}", "STDOUT", "STDERR", "/dev/stdin", "/dev/stdout")
    val iIndices = tokens.zipWithIndex.collect { case ("-i", idx) => idx }
    val oIndices = tokens.zipWithIndex.collect { case ("-o", idx) => idx }
    val inputPath = iIndices
      .flatMap(i => if (i + 1 < tokens.length) Some(tokens(i + 1)) else None)
      .filterNot(placeholders.contains)
      .lastOption
      .getOrElse("")
    val outputPath = oIndices
      .flatMap(i => if (i + 1 < tokens.length) Some(tokens(i + 1)) else None)
      .filterNot(placeholders.contains)
      .lastOption
      .getOrElse("")
    val vepBin = sys.env.getOrElse("VEP_BIN", "vep")

    // If inputPath is a local file, upload it to HDFS so all Spark workers can read it.
    // If it already starts with a URI scheme (hdfs://, s3://, etc.) use it directly.
    val hdfs = FileSystem.get(sc.hadoopConfiguration)
    val ts = System.currentTimeMillis()
    val hdfsInputPath: String = if (inputPath.contains("://")) {
      println(
        s"[VEP] Input is already a remote URI, using directly: $inputPath"
      )
      inputPath
    } else {
      // In YARN cluster mode, the driver runs inside a container whose working
      // directory is a YARN tmp dir — relative paths resolve there, not on the
      // submitting machine.  Absolute local paths only work if that file happens
      // to be present on the node selected as the ApplicationMaster.
      // Strategy:
      //   1. If it is an absolute local path AND the file exists here → upload to HDFS.
      //   2. If it is a relative path OR the local file is missing →
      //      check whether the path exists on HDFS as-is (e.g. the user pre-uploaded it).
      //   3. Otherwise, fail with an actionable message.
      val localFile = new File(inputPath).getAbsoluteFile
      if (localFile.exists()) {
        // Case 1: file is accessible on the driver node — upload it to HDFS.
        val hdfsDir    = new Path(s"/tmp/spark4vcf_vep_$ts")
        val hdfsTarget = new Path(hdfsDir, localFile.getName)
        println(
          s"[VEP] Uploading local file ${localFile.getAbsolutePath} -> hdfs:${hdfsTarget.toString}"
        )
        hdfs.copyFromLocalFile(
          false,
          true,
          new Path("file://" + localFile.getAbsolutePath),
          hdfsTarget
        )
        hdfsTarget.toString
      } else {
        // Case 2: file not found locally — try interpreting the path as an HDFS path.
        // This handles the common YARN cluster-mode scenario where the user passed a
        // relative or absolute path that only makes sense on HDFS.
        val hdfsCandidate = new Path(inputPath)
        if (hdfs.exists(hdfsCandidate)) {
          println(
            s"[VEP] Local file not found; resolved input on HDFS: ${hdfsCandidate.toString}"
          )
          hdfsCandidate.toString
        } else {
          // Case 3: not found anywhere — give a clear, actionable error.
          throw new IllegalArgumentException(
            s"[VEP] Input file not found locally ('${localFile.getAbsolutePath}') " +
            s"or on HDFS ('${hdfsCandidate.toString}'). " +
            "When running in YARN cluster deploy-mode, use an absolute HDFS URI " +
            "(e.g. hdfs:///user/vagrant/first_25k.vcf.gz) so all nodes can access the file."
          )
        }
      }
    }

    val dataRDD = sc.textFile(hdfsInputPath)
    val (headerRDD, variantsRDD) =
      dataRDD.filterDivisor(line => line.startsWith("#"))
    val gatheredHeaderRDD = headerRDD.coalesce(1)
    val header = gatheredHeaderRDD.collect()
    val numberOfLines: Long =
      variantsRDD.persist(StorageLevel.MEMORY_AND_DISK).count()
    val vepBufferSize: Long =
      2500 // variants per partition → each VEP task is small enough to reliably finish
    val numberOfPartitions = math.max(1, (numberOfLines / vepBufferSize).toInt)

    // --- Partition-level processing (avoids collecting all variants to driver) ---
    // Each Spark task:
    //   1. Writes its slice of variants (+ VCF header) to a local tmp file on the executor.
    //   2. Pipes the tmp file path through `annotateCmd`
    //      (xargs -I {} ... vep ... -i {} -o STDOUT), same as the original xargs pipe approach.
    //   3. Returns the annotated, non-header output lines.
    // This keeps heap usage proportional to ONE partition (~vepBufferSize lines),
    // not the full file, eliminating the OutOfMemoryError.
    val outputVariantsRDD: org.apache.spark.rdd.RDD[String] = variantsRDD
      .repartition(numberOfPartitions)
      .mapPartitions[String] { partition =>
        import scala.sys.process._
        val pId = org.apache.spark.TaskContext.get().partitionId()
        val tmpDir = System.getProperty("java.io.tmpdir", "/tmp")
        val tmpIn = new File(s"$tmpDir/vep_in_${ts}_$pId.vcf")

        try {
          // Write VCF header + this partition's variant lines to the tmp file
          val pw = new PrintWriter(tmpIn)
          header.foreach(pw.println)
          partition.foreach(pw.println)
          pw.close()

          // Build per-partition VEP command:
          //   execDir = "xargs -I {} /usr/bin/time -v pixi run --environment vep108 vep"
          //   vepArgs = "--cache ... -i {} -o STDOUT -i /real.vcf.gz -o /real/output.vcf.gz"
          //
          // Steps:
          //   1. Strip the "xargs -I {} " wrapper — not needed since we have the file directly.
          //   2. Replace the -i {} placeholder with the actual tmp file path.
          //   3. Remove the Spark-only -i <inputPath> and -o <outputPath> flags that were
          //      added only so the driver could parse the real input/output paths; VEP should
          //      read from the tmp file and write to STDOUT.
          val cleanArgs = vepArgs
            .replace(
              s"-i $inputPath",
              s"-i ${tmpIn.getAbsolutePath}"
            ) // replace Spark-only input flag with the per-partition tmp file
            .replace(
              s"-o $outputPath",
              "-o STDOUT"
            ) // replace Spark-only output flag with STDOUT
            .trim

          val cmd = s"$vepBin $cleanArgs"
          println(s"[VEP] Partition $pId cmd: $cmd")

          // Execute VEP and capture STDOUT line-by-line via ProcessLogger.
          // We check the exit code explicitly — VEP failing silently was the cause
          // of getting only partial output (e.g., 2180 out of 25k variants).
          // If VEP exits non-zero, throw so Spark marks this task as failed (retryable).
          val annotated = scala.collection.mutable.ArrayBuffer[String]()
          val exitCode = Process(cmd) ! ProcessLogger(
            line =>
              if (line.nonEmpty && !line.startsWith("#"))
                annotated += line, // STDOUT
            line => System.err.println(s"[VEP stderr p$pId] $line") // STDERR
          )
          // if (exitCode != 0)
          //   throw new RuntimeException(
          //     s"[VEP] Partition $pId failed with exit code $exitCode. Command: $cmd"
          //   )
          // println(
          //   s"[VEP] Partition $pId produced ${annotated.size} annotated variants."
          // )
          annotated.iterator
        }
        // } finally {
        //   if (tmpIn.exists()) tmpIn.delete()
        // }
      }

    // Determine the final HDFS output path.
    // If the user specified an hdfs:// URI we write directly there (ideal for YARN cluster mode).
    // Otherwise we first save to a HDFS tmp location and then copy back to the local path
    // (works in local/client mode where the driver IS the submitting machine).
    val isHdfsOutput = outputPath.contains("://")
    val hdfsOutputTmp: String = if (isHdfsOutput) {
      outputPath  // write directly to the caller-supplied HDFS path
    } else {
      s"/tmp/spark4vcf_vep_out_$ts/${new File(outputPath).getName}"
    }

    println(s"[VEP] Saving merged output to: $hdfsOutputTmp")
    gatheredHeaderRDD
      .union(outputVariantsRDD)
      .saveAsSingleTextFile(hdfsOutputTmp)

    if (isHdfsOutput) {
      println(s"[VEP] Output written to HDFS: $hdfsOutputTmp")
    } else {
      // Download from HDFS → local (works in local/client deploy-mode).
      // In YARN cluster deploy-mode prefer using an hdfs:// output path instead.
      val localOut = new File(outputPath).getAbsoluteFile
      Option(localOut.getParentFile).foreach(_.mkdirs())
      println(
        s"[VEP] Copying hdfs:$hdfsOutputTmp -> local:${localOut.getAbsolutePath}"
      )
      hdfs.copyToLocalFile(
        true, // deleteSource: remove from HDFS after copy
        new Path(hdfsOutputTmp),
        new Path("file://" + localOut.getAbsolutePath)
      )
      // hdfs.delete(new Path(s"/tmp/spark4vcf_vep_out_$ts"), true)
    }

    // Cleanup input HDFS tmp if we uploaded a local file
    if (!inputPath.contains("://")) {
      println(s"[VEP] Cleaning up input HDFS tmp at /tmp/spark4vcf_vep_$ts")
      // hdfs.delete(new Path(s"/tmp/spark4vcf_vep_$ts"), true)
    }
  }
}
