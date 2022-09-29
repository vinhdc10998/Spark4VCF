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
    val annotateCmd = "java -Xmx8g -jar " + snpEffJarPath + " " + snpEffArgs
    val vcfRDD = sc.textFile(inputPath)
    val (headerRDD, variantsRDD) = vcfRDD.filterDivisor(line => line.startsWith("#"))

    val headRDD = headerRDD
      .union(sc.parallelize(Seq(variantsRDD.first())))
      .coalesce(1)
      .pipe(annotateCmd)

    val tailRDD = variantsRDD
      .coalesce(sc.defaultParallelism)
      .mapPartitionsWithIndex((index, it) =>
          if (index == 0) it.drop(1) else it,
          preservesPartitioning = true)
      .pipe(annotateCmd)
      .filter(line => !line.startsWith("#"))

    headRDD
      .union(tailRDD)
      .saveAsSingleTextFile(outputPath)
  }
}
