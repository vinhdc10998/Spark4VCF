package extprg.annovar
import org.apache.spark.rdd.{RDD, UnionRDD}
import org.apache.spark.{SparkContext, TaskContext}
import utils.CustomOperators._
import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.reflect.io.Directory
import scala.util.control.Breaks.{break, breakable}

object ANNOVAR {

  def getSuffixList(prjTmpDir: File): ArrayBuffer[String] = {
    val files = prjTmpDir.listFiles()
    var suffix = new String
    val sufList = new ArrayBuffer[String]
    for (file <- files) {
      suffix = file.getName.split("[.]", 2)(1)
      if (!sufList.contains(suffix)) sufList += suffix
    }
    sufList
  }

  def firstLine(f: File): Option[String] = {
    val src = Source.fromFile(f)
    try {
      src.getLines.find(_ => true)
    } finally {
      src.close()
    }
  }

  def generateVcfHeaderAnnotationINFO(prjTmpDir: File): ArrayBuffer[String] = {
    val files = prjTmpDir.listFiles()

    val annotateInfos = new ArrayBuffer[String]

    breakable(
      for (file <- files) if (file.getName.endsWith(".txt")) {
        firstLine(file).get.split("\t").map(col => {
          if (
            !col.contains("Otherinfo") &&
              !col.equals("Chr") &&
              !col.equals("Start") &&
              !col.equals("End") &&
              !col.equals("Ref") &&
              !col.equals("Alt")
          ) annotateInfos += "##INFO=<ID=" + col + ",Number=.,Type=String,Description=\"" + col + " annotation provided by ANNOVAR\">"
        })
        break
      }
    )
    if (annotateInfos.nonEmpty) {
      val openTag = "##INFO=<ID=ANNOVAR_DATE,Number=1,Type=String,Description=\"Flag the start of ANNOVAR annotation for one alternative allele\">"
      val closeTag = "##INFO=<ID=ALLELE_END,Number=0,Type=Flag,Description=\"Flag the end of ANNOVAR annotation for one alternative allele\">"
      openTag +=: annotateInfos += closeTag
    }
    annotateInfos
  }

  def mergeTxtCsvFiles(
                      sc: SparkContext,
                      numOfPartitions: Int,
                      suffix: String,
                      prjTmpDir: String,
                      outputPath: String
                      ) {
    val txtRDDs = new ArrayBuffer[RDD[String]]()
    print(outputPath + "." + suffix)
    txtRDDs += sc.textFile(prjTmpDir + "/0." + suffix)
    for (i <- 1 until numOfPartitions)
      if (new File(prjTmpDir + "/" + i.toString + "." + suffix).exists())
        txtRDDs += sc.textFile("file:///"+prjTmpDir + "/" + i.toString + "." + suffix).mapPartitionsWithIndex(
          (index, iterator) => if (index == 0) iterator.drop(1) else iterator
        )
    print(outputPath + "." + suffix)
    new UnionRDD(sc, txtRDDs)
      .coalesce(1)
      .saveAsSingleTextFile(outputPath + "." + suffix)
  }

  def mergeFiles(
                  sc: SparkContext,
                  numOfPartitions: Int,
                  suffix: String,
                  prjTmpDir: String,
                  outputPath: String
                ) {
    val filePaths = new ArrayBuffer[String]
    for (i <- 0 until numOfPartitions) {
      val partFile = new File(prjTmpDir + "/" + i.toString + "." + suffix)
      if (partFile.exists()) filePaths += "file:///"+partFile.getAbsolutePath
    }
    sc.textFile(filePaths.mkString(",")).saveAsSingleTextFile(outputPath + "." + suffix)
  }

  def mergeVcfFiles(
                     sc: SparkContext,
                     numOfPartitions: Int,
                     suffix: String,
                     prjTmpDir: String,
                     outputPath: String,
                     headerRDD: RDD[String]
                   ) {
    val filePaths = new ArrayBuffer[String]
    for (i <- 0 until numOfPartitions) {
      val partFile = new File(prjTmpDir + "/" + i.toString + "." + suffix)
      if (partFile.exists()) filePaths += "file:///"+partFile.getAbsolutePath
    }
    print(filePaths.mkString(","))
    headerRDD.union(sc.textFile(filePaths.mkString(","))).saveAsSingleTextFile(outputPath + "." + suffix)
  }

  def annotateByAnnovar(
                         sc: SparkContext,
                         inputPath: String,
                         outputPath: String,
                         annovarArgs: String,
                         execDir: String) {

    // Prepare a temporary directory to save annovar output files
    val tmpDirName = "vaspark_tmp"
    val prjTmpDir = new File(execDir, tmpDirName)
    if (prjTmpDir.exists())
      if (prjTmpDir.isDirectory) new Directory(prjTmpDir).deleteRecursively()
      else prjTmpDir.delete()
    prjTmpDir.mkdir()

    val inputFileRDD = sc.textFile(inputPath)

    val (inputHeaderRDD, variantsRDD) = inputFileRDD.filterDivisor(line => line.startsWith("#"))

    val numOfPartitions = variantsRDD.getNumPartitions
    variantsRDD.mapPartitions(partition => {
      val pId = TaskContext.get().partitionId()
      val tmpOutputDir = prjTmpDir.getAbsolutePath + "/" + pId
      val annovarCommand =
        execDir + "table_annovar.pl /dev/stdin " + annovarArgs + " -out " + tmpOutputDir
      partition.pipeCmd(annovarCommand)
    }).collect()

    val suffixList = getSuffixList(prjTmpDir)

    var vcfHeader = ArrayBuffer(inputHeaderRDD.collect(): _*)
    vcfHeader = vcfHeader.patch(vcfHeader.length - 1, generateVcfHeaderAnnotationINFO(prjTmpDir), 0)
    val annotatedHeaderRDD = sc.parallelize(vcfHeader)

    // Merge output files
    for (suffix <- suffixList) {
      print(suffix)
      suffix.substring(suffix.length - 3) match {
        case "txt" =>
          mergeTxtCsvFiles(
            sc,
            numOfPartitions,
            suffix,
            prjTmpDir.getAbsolutePath,
            outputPath
          )
        case "csv" =>
          mergeTxtCsvFiles(
            sc,
            numOfPartitions,
            suffix,
            prjTmpDir.getAbsolutePath,
            outputPath
          )
        case "vcf" =>
          mergeVcfFiles(
            sc,
            numOfPartitions,
            suffix,
            prjTmpDir.getAbsolutePath,
            outputPath,
            annotatedHeaderRDD
          )
        case _ =>
          mergeFiles(
            sc,
            numOfPartitions,
            suffix,
            prjTmpDir.getAbsolutePath,
            outputPath
          )
      }
    }

    new Directory(prjTmpDir).deleteRecursively()
  }
}
