package extprg.snpeff

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import utils.CustomOperators.RDDOperators

object SNPEFF {
  def annotateBySnpEff(
                     sc: SparkContext,
                     toolArgs: String,
                     snpEffJarPath: String): Unit = {
    // TODO: Parse input/output from toolArgs (to be implemented)
    val tokens = toolArgs.split("\\s+").filterNot(_.startsWith("-"))
    val inputPath  = if (tokens.nonEmpty) tokens.last  else ""
    val outputPath = if (tokens.length >= 2) tokens(tokens.length - 2) else ""
    val snpEffArgs = toolArgs
    val annotateCmd = "java -jar " + snpEffJarPath + " " + snpEffArgs
    val vcfRDD = sc.textFile(inputPath)
    val (headerRDD, variantsRDD) = vcfRDD.filterDivisor(line => line.startsWith("#"))

    val headRDD = headerRDD.coalesce(1)

    val tailRDD = variantsRDD
      .repartition(1000)
      .pipe(annotateCmd)
      .filter(line => !line.startsWith("#"))

    headRDD
      .union(tailRDD)
      .saveAsSingleTextFile(outputPath)
  }
}
