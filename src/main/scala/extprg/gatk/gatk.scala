package extprg.gatk

import org.apache.spark.SparkContext
import scala.sys.process._
import java.io.{File, PrintWriter}

object GATK {
  def annotateByGatk(
      sc: SparkContext,
      toolArgs: String,
      execDir: String
  ): Unit = {

    // ── 1. Parse -I / -O from toolArgs ────────────────────────────────────────
    val tokens     = toolArgs.split("\\s+")
    val iIdx       = tokens.indexOf("-I")
    val oIdx       = tokens.indexOf("-O")
    val inputBam   = if (iIdx >= 0 && iIdx + 1 < tokens.length) tokens(iIdx + 1) else ""
    val outputPath = if (oIdx >= 0 && oIdx + 1 < tokens.length) tokens(oIdx + 1) else ""

    require(inputBam.nonEmpty,   "[GATK] -I <bam> is required in toolArgs")
    require(outputPath.nonEmpty, "[GATK] -O <output> is required in toolArgs")

    // ── 2. Collect user-supplied -L values ────────────────────────────────────
    // If the user passes "-L chr21 -L chr22" we distribute one task per chromosome
    // and pass those names DIRECTLY to GATK — no interval_list files are created.
    // If the user supplies no -L at all we fall back to reading the BAM header via
    // samtools and generating Picard interval_list files for every sequence.
    val userIntervals: List[String] = {
      val tks = tokens.toList
      tks.zipWithIndex.collect {
        case (tok, idx) if tok == "-L" && idx + 1 < tks.length => tks(idx + 1)
      }
    }

    // ── 3. Work directory (for vcf_list.txt and, if needed, interval_list files) ─
    val ts      = System.currentTimeMillis()
    val workDir = new File(s"/tmp/gatk_work_$ts")
    workDir.mkdirs()

    // ── 4. Build the base GATK command (stripping all user -L / -O flags) ──────
    val gatkBin = "pixi run --manifest-path /spark4vcf/pixi.toml -e default -- /spark4vcf/tools/gatk-4.1.9.0/gatk"

    // Strip every -L <val> and -O <val> pair; we inject our own per-task values.
    val strippedArgs: String = {
      val tks = tokens.toList
      tks.zipWithIndex.foldLeft(List.empty[String]) { case (acc, (tok, idx)) =>
        if (tok == "-L" || tok == "-O") acc
        else if (idx > 0 && (tks(idx - 1) == "-L" || tks(idx - 1) == "-O")) acc
        else acc :+ tok
      }.mkString(" ")
    }

    // ── 5. Determine per-task output file extension ───────────────────────────
    val (outBase, outExt) = {
      val name = new File(outputPath).getName
      val firstDot = name.indexOf('.')
      if (firstDot > 0)
        (outputPath.stripSuffix(name.substring(firstDot)), name.substring(firstDot))
      else
        (outputPath, "")
    }
    val outputDir = new File(outputPath).getParentFile
    if (outputDir != null) outputDir.mkdirs()

    // ── 6. Build the per-task interval descriptors ───────────────────────────
    //
    // PATH A – user supplied "-L chr21 -L chr22"
    //   → distribute chrom names directly; one task per chromosome; NO interval files.
    //
    // PATH B – no -L given
    //   → read BAM header with samtools on the DRIVER, chunk sequences, then
    //     distribute the interval file *content* (not a path) to each Spark task.
    //     The task writes its own local /tmp interval_list file on the worker node
    //     before calling GATK — this avoids the driver's /tmp being unavailable
    //     on remote workers.
    //
    // Each element of `taskIntervals` is a String:
    //   • PATH A: bare chrom name, e.g. "chr21"  → passed as -L chr21
    //   • PATH B: "INTERVALLIST:<content>" where <content> is the full Picard
    //             interval_list file body (header + data lines, \n-separated).
    //             The task writes this to a local /tmp file and passes that path.
    val taskIntervals: List[String] = if (userIntervals.nonEmpty) {
      println(s"[GATK] User-supplied intervals: ${userIntervals.mkString(", ")} — skipping BAM-header scan")
      userIntervals
    } else {
      println(s"[GATK] No -L supplied — reading BAM header from: $inputBam")
      val samtoolsCmd = s"pixi run --manifest-path /spark4vcf/pixi.toml -e default -- samtools view -H $inputBam"
      val headerLines = samtoolsCmd.lineStream_!.toList
      val sqLines     = headerLines.filter(_.startsWith("@SQ"))
      require(sqLines.nonEmpty,
        s"[GATK] No @SQ lines found in BAM header. Is '$inputBam' a valid BAM file?")

      def tagValue(line: String, tag: String): String =
        line.split("\t").find(_.startsWith(s"$tag:")).map(_.drop(tag.length + 1)).getOrElse("")

      case class SeqInfo(chrom: String, length: Long)
      val allSeqs = sqLines.map(sq => SeqInfo(tagValue(sq, "SN"), tagValue(sq, "LN").toLong))
      println(s"[GATK] Found ${allSeqs.size} sequences in BAM header.")

      val hdLine       = headerLines.find(_.startsWith("@HD")).getOrElse("@HD\tVN:1.6\tSO:coordinate")
      val picardHeader = (hdLine +: sqLines).mkString("\n")

      val intervalsPerTask = 3  // tune: sequences per Spark task
      val chunks = allSeqs.grouped(intervalsPerTask).toList

      // Serialize the interval file content into the distributed string so the
      // worker can reconstruct the file locally — no driver /tmp path is sent.
      chunks.zipWithIndex.map { case (chunk, i) =>
        val intervalLines = chunk.map(s => s"${s.chrom}\t1\t${s.length}\t+\t.").mkString("\n")
        val fileContent   = s"$picardHeader\n$intervalLines"
        println(s"[GATK] Queued interval chunk $i: ${chunk.map(_.chrom).mkString(", ")}")
        s"INTERVALLIST:$fileContent"   // worker will strip the prefix and write the file
      }
    }

    println(s"[GATK] Launching ${taskIntervals.size} Spark task(s).")

    // ── 7. Distribute: one Spark task per interval ────────────────────────────
    val numPartitions   = taskIntervals.size
    val taskIntervalsRDD = sc.makeRDD(taskIntervals, numPartitions)

    val outputVcfPathsRDD = taskIntervalsRDD.mapPartitions { iter =>
      iter.map { intervalArg =>
        import scala.sys.process._
        import java.io.{File, PrintWriter}

        val partitionId = org.apache.spark.TaskContext.get().partitionId()
        val taskOutFile = s"${outBase}_$partitionId$outExt"

        // Resolve -L argument:
        //   PATH A → plain chrom name, pass directly.
        //   PATH B → "INTERVALLIST:<content>": write a local /tmp file on this
        //            worker node so GATK can read it from the local filesystem.
        val lArg: String = if (intervalArg.startsWith("INTERVALLIST:")) {
          val content  = intervalArg.stripPrefix("INTERVALLIST:")
          val tmpFile  = File.createTempFile(s"gatk_intervals_p${partitionId}_", ".interval_list")
          tmpFile.deleteOnExit()
          val pw = new PrintWriter(tmpFile)
          pw.print(content)
          pw.close()
          println(s"[GATK] Partition $partitionId wrote local interval file: ${tmpFile.getAbsolutePath}")
          tmpFile.getAbsolutePath
        } else {
          intervalArg  // plain chrom name (PATH A)
        }

        val cmd = s"$gatkBin $strippedArgs -O $taskOutFile -L $lArg"
        println(s"[GATK] Partition $partitionId cmd: $cmd")

        val exitCode = Process(cmd) ! ProcessLogger(
          out => println(s"[GATK stdout p$partitionId] $out"),
          err => System.err.println(s"[GATK stderr p$partitionId] $err")
        )

        if (exitCode != 0)
          throw new RuntimeException(
            s"[GATK] Partition $partitionId failed (exit $exitCode). Command: $cmd"
          )

        println(s"[GATK] Partition $partitionId finished → $taskOutFile")
        taskOutFile
      }
    }

    // ── 8. Collect all per-task VCF paths ─────────────────────────────────────
    val partVcfPaths = outputVcfPathsRDD.collect()
    println(s"[GATK] All ${partVcfPaths.length} distributed tasks finished.")
    partVcfPaths.foreach(p => println(s"[GATK]   partial VCF: $p"))

    // ── 9. Write VCF list file for bcftools ───────────────────────────────────
    // Sort paths by partition index so the chromosomal order is preserved.
    val sortedPaths   = partVcfPaths.sorted
    val vcfListFile   = new File(workDir, "vcf_list.txt")
    val listPw        = new PrintWriter(vcfListFile)
    sortedPaths.foreach(listPw.println)
    listPw.close()
    println(s"[GATK] VCF list written to: ${vcfListFile.getAbsolutePath}")

    val bcftoolsBin = "pixi run --manifest-path /spark4vcf/pixi.toml -e default -- bcftools"

    // ── 10. Index each partial VCF with bcftools index ───────────────────────
    sortedPaths.foreach { vcf =>
      val idxCmd  = s"$bcftoolsBin index --force --tbi $vcf"
      println(s"[GATK] Indexing: $idxCmd")
      val idxExit = Process(idxCmd) ! ProcessLogger(
        out => println(s"[GATK bcftools index] $out"),
        err => System.err.println(s"[GATK bcftools index stderr] $err")
      )
      if (idxExit != 0)
        throw new RuntimeException(s"[GATK] bcftools index failed (exit $idxExit) for: $vcf")
    }

    // ── 11. Merge all partial VCFs into the final output with bcftools concat ─
    val mergeCmd    = s"$bcftoolsBin concat --file-list ${vcfListFile.getAbsolutePath} --allow-overlaps -o $outputPath -O z"
    println(s"[GATK] Merging with: $mergeCmd")

    val mergeExit = Process(mergeCmd) ! ProcessLogger(
      out => println(s"[GATK bcftools] $out"),
      err => System.err.println(s"[GATK bcftools stderr] $err")
    )
    if (mergeExit != 0)
      throw new RuntimeException(s"[GATK] bcftools concat failed (exit $mergeExit). Command: $mergeCmd")

    // Final index on the merged VCF
    val finalIdxCmd = s"$bcftoolsBin index --tbi $outputPath"
    println(s"[GATK] Indexing final output: $finalIdxCmd")
    Process(finalIdxCmd) ! ProcessLogger(
      out => println(s"[GATK index] $out"),
      err => System.err.println(s"[GATK index stderr] $err")
    )

    println(s"[GATK] Done. Final merged VCF: $outputPath")
  }
}
