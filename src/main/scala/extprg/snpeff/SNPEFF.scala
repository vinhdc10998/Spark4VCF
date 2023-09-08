package extprg.snpeff

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import utils.CustomOperators.RDDOperators

object SNPEFF {
  def annotateBySnpEff(
                     sc: SparkContext,
                     inputPath: String,
                     outputPath: String,
                     snpEffArgs: String,
                     snpEffJarPath: String) {
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
