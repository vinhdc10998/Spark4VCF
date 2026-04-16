error id: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala:textFile.
file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
empty definition using pc, found symbol in pc: textFile.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -java/io/sc/textFile.
	 -java/io/sc/textFile#
	 -java/io/sc/textFile().
	 -sc/textFile.
	 -sc/textFile#
	 -sc/textFile().
	 -scala/Predef.sc.textFile.
	 -scala/Predef.sc.textFile#
	 -scala/Predef.sc.textFile().
offset: 990
uri: file://<WORKSPACE>/src/main/scala/extprg/vep/VEP.scala
text:
```scala

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
                     execDir: String): Unit = {
    // Parse input/output from toolArgs
    val tokens = vepArgs.split("\\s+")
    val iIdx = tokens.indexOf("-i")
    val oIdx = tokens.indexOf("-o")
    val inputPath  = if (iIdx >= 0 && iIdx + 1 < tokens.length) tokens(iIdx + 1) else ""
    val outputPath = if (oIdx >= 0 && oIdx + 1 < tokens.length) tokens(oIdx + 1) else ""
    val annotateCmd = execDir + " " + vepArgs
    val dataRDD = sc.textFil@@e(inputPath)  
    val annotateCmd = execDir + " " + vepArgs
    val dataRDD = sc.textFile(inputPath)
    val (headerRDD, variantsRDD) = dataRDD.filterDivisor(line => line.startsWith("#"))
    val gatheredHeaderRDD = headerRDD.coalesce(1)
    val header = gatheredHeaderRDD.collect()
    val numberOfLines: Long = variantsRDD.persist(StorageLevel.MEMORY_AND_DISK).count()
    val vepBufferSize: Long = 20000
    val numberOfPartitions = numberOfLines/vepBufferSize
    //val numberOfPartitions = 50

    val directory = new Directory(new File(s"/vagrant/tmp/*"))
    directory.deleteRecursively()


    val tmp = variantsRDD.collect.toList.sliding(vepBufferSize.toInt, vepBufferSize.toInt).toList
    tmp.zipWithIndex.par.foreach{case (line,i) => val pw = new PrintWriter(new File(s"/vagrant/tmp/tmp_$i.vcf")); pw.write(header.union(line).mkString("\n")); pw.close}
    //tmp.zipWithIndex.foreach{case (line,i) => gatheredHeaderRDD.union(sc.makeRDD(line)).coalesce(1).saveAsTextFile(s"file:///vagrant/tmp/tmp_$i/")}
    //val eachFile = tmp.map(line => gatheredHeaderRDD.union(sc.makeRDD(line)))
    //val kk = sc.makeRDD(tmp)
    val p = sc.makeRDD(List.range(0, tmp.size).map(line => s"/vagrant/tmp/tmp_$line.vcf")).repartition(numberOfPartitions.toInt)
    val outputVariantsRDD = p.pipe(annotateCmd).filter(line => !line.startsWith("#"))
    gatheredHeaderRDD
      .union(outputVariantsRDD)
      .saveAsSingleTextFile(outputPath)
  } 
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: textFile.