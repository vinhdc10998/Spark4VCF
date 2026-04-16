error id: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala:
file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/sys/process/hdfsTmpRoot.
	 -scala/sys/process/hdfsTmpRoot#
	 -scala/sys/process/hdfsTmpRoot().
	 -hdfsTmpRoot.
	 -hdfsTmpRoot#
	 -hdfsTmpRoot().
	 -scala/Predef.hdfsTmpRoot.
	 -scala/Predef.hdfsTmpRoot#
	 -scala/Predef.hdfsTmpRoot().
offset: 7655
uri: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
text:
```scala
package extprg.vep

import org.apache.spark.SparkContext
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import scala.reflect.io.Directory
import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import java.io.{File, PrintWriter}
import scala.sys.process._
import utils.CustomOperators.RDDOperators
import utils.CustomOperators.IteratorStringOperators

object VEP {

  private val DefaultBcftoolsExecutable = "/spark4vcf/.pixi/envs/default/bin/bcftools"

  /**
   * Parses the value of a named flag from an argument string.
   * e.g. parseFlag("-i", "-i /data/input.vcf --format vcf") => Some("/data/input.vcf")
   */
  private def parseFlag(flag: String, args: String): Option[String] = {
    val tokens = args.split("\\s+")
    val idx = tokens.indexOf(flag)
    if (idx >= 0 && idx + 1 < tokens.length) Some(tokens(idx + 1)) else None
  }

  /**
   * Replaces the value of a named flag in an argument string.
   * e.g. replaceFlag("-i", "/old/path", "/new/path", args)
   */
  private def replaceFlag(flag: String, oldVal: String, newVal: String, args: String): String =
    args.replace(s"$flag $oldVal", s"$flag $newVal")

  private def writeTargetBatches(
      localInputPath: String,
      localTmpDir: File,
      vepBufferSize: Int,
      bcftoolsExecutable: String
  ): Seq[(Int, File)] = {
    val targetFiles = ArrayBuffer[(Int, File)]()
    var currentWriter: Option[PrintWriter] = None
    var currentCount = 0
    var batchIndex = 0

    def rotateWriter(): Unit = {
      currentWriter.foreach(_.close())
      val targetFile = new File(localTmpDir, s"targets_$batchIndex.tsv")
      targetFiles += ((batchIndex, targetFile))
      currentWriter = Some(new PrintWriter(targetFile))
      currentCount = 0
      batchIndex += 1
    }

    val io = new ProcessIO(
      _.close(),
      out => {
        val lines = Source.fromInputStream(out).getLines()
        lines.foreach { line =>
          if (currentWriter.isEmpty || currentCount >= vepBufferSize) {
            rotateWriter()
          }
          currentWriter.foreach(_.println(line))
          currentCount += 1
        }
        currentWriter.foreach(_.close())
      },
      err => Source.fromInputStream(err).getLines().foreach(System.err.println(_: String))
    )

    val exitCode = Process(Seq(bcftoolsExecutable, "query", "-f", "%CHROM\t%POS\n", localInputPath)).run(io).exitValue()
    if (exitCode != 0) {
      throw new RuntimeException(s"[VEP] bcftools query failed with exit code $exitCode")
    }

    targetFiles.toSeq
  }

  def annotateByVep(sc: SparkContext, vepArgs: String, execDir: String): Unit = {

    val localInputPath = parseFlag("-i", vepArgs).getOrElse {
      throw new IllegalArgumentException("[VEP] Missing -i <input_file> in tool args")
    }
    val localOutputPath = parseFlag("-o", vepArgs).getOrElse {
      throw new IllegalArgumentException("[VEP] Missing -o <output_file> in tool args")
    }

    println(s"[VEP] Local input:  $localInputPath")
    println(s"[VEP] Local output: $localOutputPath")

    val ts = System.currentTimeMillis()
    val hdfs = FileSystem.get(sc.hadoopConfiguration)
    val hdfsTmpRoot = s"/tmp/spark4vcf_vep_$ts"
    val hdfsChunksDir = new Path(s"$hdfsTmpRoot/chunks")
    val hdfsOutputPath = new Path(s"$hdfsTmpRoot/output.vcf")
    val hdfsDefaultFs = sc.hadoopConfiguration.get("fs.defaultFS")

    val vepBufferSize = 20000
    val bcftoolsExecutable = DefaultBcftoolsExecutable
    val localTmpDir = new File(s"/tmp/spark4vcf_vep_local_$ts")
    localTmpDir.mkdirs()

    println(s"[VEP] Splitting local input into chunks of $vepBufferSize variants with bcftools...")
    val targetBatches = writeTargetBatches(localInputPath, localTmpDir, vepBufferSize, bcftoolsExecutable)
    println(s"[VEP] Created ${targetBatches.length} target batch file(s).")

    val chunkSpecs = targetBatches.map { case (batchIdx, targetFile) =>
      val localChunkFile = new File(localTmpDir, f"chunk_$batchIdx%05d.vcf")
      val splitExitCode = Process(Seq(
        bcftoolsExecutable,
        "view",
        "-T", targetFile.getAbsolutePath,
        "-Ov",
        "-o", localChunkFile.getAbsolutePath,
        localInputPath
      )).!
      if (splitExitCode != 0) {
        throw new RuntimeException(s"[VEP] bcftools view failed for batch $batchIdx with exit code $splitExitCode")
      }

      val hdfsChunkPath = new Path(s"${hdfsChunksDir.toString}/chunk_$batchIdx.vcf")
      println(s"[VEP] Uploading chunk $batchIdx -> hdfs:${hdfsChunkPath.toString}")
      hdfs.copyFromLocalFile(
        false,
        true,
        new Path("file://" + localChunkFile.getAbsolutePath),
        hdfsChunkPath
      )

      (batchIdx, hdfsChunkPath.toString)
    }

    val distributedVepArgs = replaceFlag("-o", localOutputPath, "stdout", vepArgs)
    val annotateCmd = s"xargs -I {} $execDir ${replaceFlag("-i", localInputPath, "{}", distributedVepArgs)}"
    val originalHeader = Process(Seq(bcftoolsExecutable, "view", "-h", localInputPath)).!!.split("\n").toSeq.filter(_.startsWith("#"))
    val finalHeader = Process(Seq(bcftoolsExecutable, "view", "-H", localInputPath)).lineStream_!.headOption match {
      case Some(firstVariantLine) =>
        val headerProbeFile = new File(localTmpDir, "header_probe.vcf")
        val pw = new PrintWriter(headerProbeFile)
        pw.write((originalHeader :+ firstVariantLine).mkString("\n"))
        pw.write("\n")
        pw.close()

        val headerProbeCmd = s"$execDir ${replaceFlag("-i", localInputPath, headerProbeFile.getAbsolutePath, distributedVepArgs)}"
        Process(headerProbeCmd).lineStream_!.takeWhile(_.startsWith("#")).toSeq
      case None =>
        originalHeader
    }
    val finalHeaderRDD = sc.parallelize(finalHeader, 1)

    val outputVariantsRDD =
      if (chunkSpecs.isEmpty) {
        sc.emptyRDD[String]
      } else {
        sc.makeRDD(chunkSpecs, chunkSpecs.length).mapPartitionsWithIndex { case (partIdx, iter) =>
          val taskConf = new Configuration()
          if (hdfsDefaultFs != null && hdfsDefaultFs.nonEmpty) {
            taskConf.set("fs.defaultFS", hdfsDefaultFs)
          }
          val taskHdfs = FileSystem.get(taskConf)

          val localizedChunks = iter.map { case (batchIdx, hdfsChunkPath) =>
            val executorTmpRoot = new File(s"/tmp/spark4vcf_vep_exec_${ts}_part${partIdx}_batch${batchIdx}")
            executorTmpRoot.mkdirs()
            val localChunkFile = new File(executorTmpRoot, s"chunk_$batchIdx.vcf")
            taskHdfs.copyToLocalFile(
              false,
              new Path(hdfsChunkPath),
              new Path("file://" + localChunkFile.getAbsolutePath),
              true
            )
            (executorTmpRoot, localChunkFile.getAbsolutePath)
          }.toList

          val outputLines = localizedChunks.map(_._2).iterator.pipeCmd(annotateCmd).filter(line => !line.startsWith("#")).toList
          localizedChunks.foreach { case (executorTmpRoot, _) =>
            new Directory(executorTmpRoot).deleteRecursively()
          }

          outputLines.iterator
        }
      }

    finalHeaderRDD
      .union(outputVariantsRDD)
      .saveAsSingleTextFile(hdfsOutputPath.toString)

    println(s"[VEP] Downloading hdfs:${hdfsOutputPath.toString} -> $localOutputPath")
    val localOutFile = new File(localOutputPath)
    Option(localOutFile.getParentFile).foreach(_.mkdirs())
    hdfs.copyToLocalFile(
      false,
      hdfsOutputPath,
      new Path("file://" + localOutFile.getAbsolutePath),
      true
    )

    println(s"[VEP] Cleaning up HDFS tmp at $hdfsTmpRoot")
    hdfs.delete(new Path(hdfsTmpR@@oot), true)
    new Directory(localTmpDir).deleteRecursively()

    println(s"[VEP] Done. Output at: $localOutputPath")
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 