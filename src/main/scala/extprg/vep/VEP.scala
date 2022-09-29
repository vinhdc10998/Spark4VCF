package extprg.vep

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
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
    val outputHeaderRDD = gatheredHeaderRDD.pipe(annotateCmd)
    val numberOfLines: Long = variantsRDD.persist(StorageLevel.MEMORY_AND_DISK).count()
    val vepBufferSize: Long = 29000
    val numberOfPartitions = numberOfLines/vepBufferSize
    variantsRDD.repartition(numberOfPartitions.toInt)
    val outputVariantsRDD = variantsRDD.pipe(annotateCmd).filter(line => !line.startsWith("#"))
    outputHeaderRDD
      .union(outputVariantsRDD)
      .saveAsSingleTextFile(outputPath)
  }
}