error id: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala:scala/Long#
file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
empty definition using pc, found symbol in pc: scala/Long#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -Long#
	 -scala/Predef.Long#
offset: 2668
uri: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
text:
```scala
package extprg.vep

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.apache.hadoop.fs.{FileSystem, Path}
import scala.reflect.io.Directory
import java.io.{File, PrintWriter}
import utils.CustomOperators.RDDOperators

object VEP {

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
   * e.g. replaceFlag("-i", "/old/path", "/new/hdfs/path", args)
   */
  private def replaceFlag(flag: String, oldVal: String, newVal: String, args: String): String =
    args.replace(s"$flag $oldVal", s"$flag $newVal")

  def annotateByVep(sc: SparkContext, vepArgs: String, execDir: String): Unit = {

    // --- Parse local input/output from tool args ---
    val localInputPath = parseFlag("-i", vepArgs).getOrElse {
      throw new IllegalArgumentException("[VEP] Missing -i <input_file> in tool args")
    }
    val localOutputPath = parseFlag("-o", vepArgs).getOrElse {
      throw new IllegalArgumentException("[VEP] Missing -o <output_file> in tool args")
    }

    println(s"[VEP] Local input:  $localInputPath")
    println(s"[VEP] Local output: $localOutputPath")

    // --- HDFS paths ---
    val ts = System.currentTimeMillis()
    val hdfs = FileSystem.get(sc.hadoopConfiguration)
    val hdfsInputPath  = new Path(s"/tmp/spark4vcf_vep_$ts/input.vcf")
    val hdfsOutputPath = new Path(s"/tmp/spark4vcf_vep_$ts/output")

    // --- Upload local input to HDFS ---
    println(s"[VEP] Uploading $localInputPath → hdfs:${hdfsInputPath.toString}")
    hdfs.copyFromLocalFile(false, true, new Path("file://" + new File(localInputPath).getAbsolutePath), hdfsInputPath)

    // --- Rewrite -i and -o in tool args to HDFS paths ---
    val hdfsInputStr  = hdfsInputPath.toString
    val hdfsOutputStr = hdfsOutputPath.toString
    val adjustedArgs  = replaceFlag("-i", localInputPath, hdfsInputStr,
                          replaceFlag("-o", localOutputPath, hdfsOutputStr, vepArgs))

    val annotateCmd = execDir + " " + adjustedArgs

    // --- Distributed annotation ---
    val dataRDD = sc.textFile(hdfsInputStr)
    val (headerRDD, variantsRDD) = dataRDD.filterDivisor(line => line.startsWith("#"))
    val gatheredHeaderRDD = headerRDD.coalesce(1)
    val header = gatheredHeaderRDD.collect()

    val vepBufferSize: L@@ong = 20000
    val numberOfLines: Long = variantsRDD.persist(StorageLevel.MEMORY_AND_DISK).count()
    val numberOfPartitions = math.max(1, (numberOfLines / vepBufferSize).toInt)

    // Write per-partition tmp VCF files locally, pipe through VEP
    val localTmpDir = s"/tmp/spark4vcf_vep_local_$ts"
    new File(localTmpDir).mkdirs()

    val tmp = variantsRDD.collect().toList.sliding(vepBufferSize.toInt, vepBufferSize.toInt).toList
    tmp.zipWithIndex.foreach { case (lines, i) =>
      val pw = new PrintWriter(new File(s"$localTmpDir/tmp_$i.vcf"))
      pw.write(header.union(lines).mkString("\n"))
      pw.close()
    }

    val p = sc.makeRDD(List.range(0, tmp.size).map(i => s"$localTmpDir/tmp_$i.vcf"))
               .repartition(numberOfPartitions)
    val outputVariantsRDD = p.pipe(annotateCmd).filter(line => !line.startsWith("#"))

    gatheredHeaderRDD
      .union(outputVariantsRDD)
      .saveAsSingleTextFile(hdfsOutputStr)

    // --- Download HDFS output back to local ---
    println(s"[VEP] Downloading hdfs:${hdfsOutputStr} → $localOutputPath")
    val localOutPath = new Path("file://" + new File(localOutputPath).getAbsolutePath)
    new File(localOutputPath).getParentFile.mkdirs()
    hdfs.copyToLocalFile(false, hdfsOutputPath, localOutPath, true)

    // --- Cleanup HDFS tmp ---
    println(s"[VEP] Cleaning up HDFS tmp at /tmp/spark4vcf_vep_$ts")
    hdfs.delete(new Path(s"/tmp/spark4vcf_vep_$ts"), true)

    // Cleanup local tmp
    new Directory(new File(localTmpDir)).deleteRecursively()

    println("[VEP] Done.")
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/Long#