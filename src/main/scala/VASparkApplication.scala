import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import scopt.OParser
import extprg.vep.VEP._
import extprg.annovar.ANNOVAR._
import extprg.snpeff.SNPEFF._
import extprg.pypgx.PYPGX._
import extprg.gatk.GATK._

case class Config(
                   annotationTool: String = "",
                   toolDir: String = "",
                   inputPath: String = "",
                   outputPath: String = "",
                   toolArgs: String = ""
                 )

object VASparkApplication extends App {

  val builder = OParser.builder[Config]

  // Handle arguments
  val mParser = {
    import builder._
    OParser.sequence(
      programName("vaspark"),
      head("vaspark", "0.1"),
      opt[String]("annotation_tool")
        .required()
        .action((x, c) => c.copy(annotationTool = x))
        .text("Annotation tool name (example: vep, annovar, snpeff)"),
      opt[String]("tool_dir")
        .required()
        .action((x, c) => c.copy(toolDir = x))
        .text("Executable path"),
      opt[String]('i', "input_file")
        .required()
        .action((x, c) => c.copy(inputPath = x))
        .text("Path to input file (should be absolute)"),
      opt[String]('o', "output_file")
        .required()
        .action((x, c) => c.copy(outputPath = x))
        .text("Path to output file (should be absolute)"),
      opt[String]("tool_args")
        .optional()
        .action((x, c) => c.copy(toolArgs = x))
        .text("Annotation tool's arguments (must be quoted)"),
      help('h', "help").text("Print usage"),
      note(
        """
          | You need to index all file input
          |   Examples:
          |    Annovar sample command:
          |     spark-submit \
          |     --master local[*] \
          |     /home/ubuntu/vaspark/target/scala-2.11/vaspark-0.1.jar \
          |     --annotation_tool annovar \
          |     --tool_dir /path/to/annovar/ \
          |     -i /path/to/vcf/file/sample.vcf \
          |     -o /path/to/output/files/myanno \
          |     --tool_args "/path/to/annovar/humandb/ -buildver hg19 -remove -protocol refGene,cytoBand,dbnsfp30a -operation g,r,f -nastring . -vcfinput"
          |
          |    Ensembl vep sample command:
          |     spark-submit \
          |     --master local[*] \
          |     /home/ubuntu/vaspark/target/scala-2.11/vaspark-0.1.jar \
          |     --annotation_tool vep \
          |     --tool_dir /path/to/ensembl/vep \
          |     -i /path/to/vcf/file/sample.vcf \
          |     -o /path/to/output/files/output.vcf \
          |     --tool_dir /path/to/ensembl/vep \
          |     --tool_args "--format vcf --no_stats --force_overwrite --cache_dir /home/.vep --offline --vcf --vcf_info_field ANN --buffer_size 60000 --phased --hgvsg --hgvs --symbol --variant_class --biotype --gene_phenotype --regulatory --ccds --transcript_version --tsl --appris --canonical --protein --uniprot --domains --sift b --polyphen b --check_existing --af --max_af --af_1kg --af_gnomad --minimal --allele_number --pubmed --fasta /home/ubuntu/.vep/homo_sapiens/100_GRCh38/Homo_sapiens.GRCh38.dna.toplevel.fa.gz "
          |
          |    SnpEff sample command:
          |     spark-submit
          |     --master local[*] \
          |     /home/ubuntu/vaspark/target/scala-2.11/vaspark-0.1.jar \
          |     --annotation_tool snpeff \
          |     --tool_dir /path/to/snpeff/snpeff.jar \
          |     -i /path/to/vcf/file/sample.vcf \
          |     -o /path/to/output/files/output.vcf \
          |     --tool_args "-v -canon GRCh37.99"
          |
          |    PyPGX sample command:
          |     spark-submit
          |     --master local[*] \
          |     /home/ubuntu/vaspark/target/scala-2.11/vaspark-0.1.jar \
          |     --annotation_tool pypgx \
          |     --tool_dir /path/to/snpeff/bin/pypgx run-ngs-pipeline CYP2D6 \ $ GENE
          |     -i /path/to/vcf/file/sample.vcf \ path in hdfs
          |     -o /path/to/output/ \
          |     --tool_args "--variants /vagrant/vn1008.chr22.vcf.gz (path in local) --samples {} --assembly GRCh38"
          |
          |
          |    GATK sample command:
          |     spark-submit
          |     --master local[*] \
          |     /home/ubuntu/vaspark/target/scala-2.11/vaspark-0.1.jar \
          |     --annotation_tool gatk \
          |     --tool_dir xargs -I {} /vagrant/tools/gatk-4.1.9.0/gatk --java-options -Xmx4g HaplotypeCaller
          |     -i /vagrant/intervals_HG00131.list \ path in hdfs
          |     -o /vagrant/gatk_3 \
          |     --tool_args "-R /vagrant/Data/Bam/Homo_sapiens_assembly38.fasta -I /vagrant/Data/Bam/HG00131-1-0-1-0.sorted.hg38.test.bam.sorted.bam -O {}.output.vcf.gz   -ERC GVCF -L {}"
          |""".stripMargin
      )
    )
  }

  OParser.parse(mParser, args, Config()) match {
    case Some(config) =>

      // Set log level to STDERR
      Logger.getLogger("org.apache.spark").setLevel(Level.ERROR)

      // Initialize Spark Application
      val spark = SparkSession.builder()
        .appName("VASpark Application")
        .getOrCreate()
      val sc = spark.sparkContext

      config.annotationTool match {
        case "vep" => annotateByVep(
          sc,
          config.inputPath,
          config.outputPath,
          config.toolArgs,
          config.toolDir
        )
        case "annovar" => annotateByAnnovar(
          sc,
          config.inputPath,
          config.outputPath,
          config.toolArgs,
          config.toolDir
        )
        case "snpeff" => annotateBySnpEff(
          sc,
          config.inputPath,
          config.outputPath,
          config.toolArgs,
          config.toolDir
        )
        case "pypgx" => annotateByPypgx(
          sc,
          config.inputPath,
          config.outputPath,
          config.toolArgs,
          config.toolDir
        )
        case "gatk" => annotateByGatk(
          sc,
          config.inputPath,
          config.outputPath,
          config.toolArgs,
          config.toolDir
        )
      }
    case _ =>
      // arguments are bad, error message will have been displayed
  }
}

