package extprg.pypgx

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

object PYPGX {
  def annotateByPypgx(
                     sc: SparkContext,
                     inputPath: String,
                     outputPath: String,
                     pyPGXArgs: String,
                     execDir: String) {
    val annotateCmd = execDir + s" /vagrant/{}" + pyPGXArgs
    val dataRDD = sc.textFile(inputPath)
    val (headerRDD, variantsRDD) = dataRDD.filterDivisor(line => line.startsWith("#"))
    val gatheredHeaderRDD = headerRDD.coalesce(1)
    val header = gatheredHeaderRDD.collect().last
    val samples = header.split("\t").drop(9)
    
    val numberOfSamples: Long = 10
    val numberOfPartitions = samples.size/numberOfSamples

    val directory = new Directory(new File(s"$outputPath/*"))
    directory.deleteRecursively()
    val tmp = samples.sliding(numberOfSamples.toInt, numberOfSamples.toInt).toList
    tmp.zipWithIndex.foreach{case (line,i) => val pw = new PrintWriter(new File(s"$outputPath/tmpSample_$i.txt")); pw.write(line.mkString("\n")); pw.close()}

    val p = sc.makeRDD(List.range(0, tmp.size).map(line => s"$outputPath/tmpSample_$line.txt")).repartition(numberOfPartitions.toInt)
    val outputSamples = p.pipe(annotateCmd).collect()
  }
}