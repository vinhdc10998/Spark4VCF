package extprg.deepvariant
import scala.io.Source

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

object DeepVariant {
  def callingByDeepVariant(
                     sc: SparkContext,
                     toolArgs: String,
                     execDir: String): Unit = {
    // TODO: Full HDFS support to be implemented later
    // Parse --reads= and --output_vcf= from toolArgs
    val readsMatch     = "--reads=([^\\s]+)".r.findFirstMatchIn(toolArgs)
    val outputMatch    = "--output_vcf=([^\\s]+)".r.findFirstMatchIn(toolArgs)
    val inputPath  = readsMatch.map(_.group(1)).getOrElse("")
    val outputPath = outputMatch.map(_.group(1)).getOrElse("")
    val deepVariantArgs = toolArgs
    val annotateCmd = execDir + " " + deepVariantArgs
    val intervals = Source.fromFile(inputPath).getLines.toList
    // val (headerRDD, variantsRDD) = dataRDD.filterDivisor(line => line.startsWith("#"))
    // val gatheredHeaderRDD = headerRDD.coalesce(1)

    val p = sc.makeRDD(intervals).repartition(5)
    val outputVariantsRDD = p.pipe(annotateCmd).filter(line => line.startsWith("chr20"))
    outputVariantsRDD.saveAsSingleTextFile(outputPath)

    // val outputSamples = p.pipe(annotateCmd).collect()

    // val outputSamples = p.pipe("xargs -I {} /vagrant/tools/gatk-4.1.9.0/gatk --java-options -Xmx4g HaplotypeCaller -R /vagrant/Data/Bam/Homo_sapiens_assembly38.fasta -I /vagrant/Data/Bam/HG00131-1-0-1-0.sorted.hg38.test.bam.sorted.bam -O {}.output.vcf.gz   -ERC GVCF -L {}").collect()
  }
}
