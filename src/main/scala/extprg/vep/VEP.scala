
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
                     inputPath: String,
                     outputPath: String,
                     vepArgs: String,
                     execDir: String) {
    val annotateCmd = execDir + " " + vepArgs
    val dataRDD = sc.textFile(inputPath)
    val (headerRDD, variantsRDD) = dataRDD.filterDivisor(line => line.startsWith("#"))
    val gatheredHeaderRDD = headerRDD.coalesce(1)
    val numberOfLines: Long = variantsRDD.persist(StorageLevel.MEMORY_AND_DISK).count()
    val vepBufferSize: Long = 20000
    val numberOfPartitions = numberOfLines/vepBufferSize
    //val numberOfPartitions = 50

    val directory = new Directory(new File(s"/vagrant/tmp/*"))
    directory.deleteRecursively()


    val tmp = variantsRDD.collect().toList.sliding(vepBufferSize.toInt, vepBufferSize.toInt).toList
    tmp.zipWithIndex.foreach{case (line,i) => val pw = new PrintWriter(new File(s"/vagrant/tmp/tmp_$i.vcf")); pw.write(headerRDD.union(sc.makeRDD(line)).collect().toList.mkString("\n")); pw.close}
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


}
