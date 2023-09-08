package extprg.gatk
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

object GATK {
  def annotateByGatk(
                     sc: SparkContext,
                     inputPath: String,
                     outputPath: String,
                     gatkArgs: String,
                     execDir: String) { 
    val annotateCmd = execDir + gatkArgs
    val intervals = Source.fromFile(inputPath).getLines.toList
    val numberOfInterals: Long = 3
    val numberOfPartitions = intervals.size/numberOfInterals

    val tmp = intervals.sliding(numberOfInterals.toInt, numberOfInterals.toInt).toList
    tmp.zipWithIndex.foreach{
      case (line,i) => 
        val pw = new PrintWriter(new File(s"$outputPath/tmpIntervals_$i.list")); 
        pw.write(line.mkString("\n")); 
        pw.close()
    }

    val p = sc.makeRDD(List.range(0, tmp.size).map(line => s"$outputPath/tmpIntervals_$line.list")).repartition(numberOfPartitions.toInt)

    val outputSamples = p.pipe(annotateCmd).collect()

    // val outputSamples = p.pipe("xargs -I {} /vagrant/tools/gatk-4.1.9.0/gatk --java-options -Xmx4g HaplotypeCaller -R /vagrant/Data/Bam/Homo_sapiens_assembly38.fasta -I /vagrant/Data/Bam/HG00131-1-0-1-0.sorted.hg38.test.bam.sorted.bam -O {}.output.vcf.gz   -ERC GVCF -L {}").collect()
  }
}
