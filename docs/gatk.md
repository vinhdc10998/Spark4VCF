### 4.3 GATK — Genome Analysis Toolkit

Runs [GATK HaplotypeCaller](https://gatk.broadinstitute.org) in parallel across genomic intervals.

**Syntax:**
```bash
spark4vcf [spark-args] gatk <gatk-subcommand> [gatk-options]
```

**Example:**
```bash
spark4vcf --master local[*] gatk \
  HaplotypeCaller \
  --java-options -Xmx4g \
  -R /data/Homo_sapiens_assembly38.fasta \
  -I /data/sample.bam \
  -O /data/output/ \
  -ERC GVCF
```

**How it works:**
1. The input interval list is split into chunks (default: 3 intervals/task).
2. Each Spark task runs GATK on its subset of intervals.
3. Per-interval VCF outputs are written to the specified output directory.
4. Merge each outputs into a single output file