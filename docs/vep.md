### 4.1 VEP — Variant Effect Predictor

Annotates variants in a VCF file using [Ensembl VEP](https://www.ensembl.org/vep), distributed across Spark partitions (default: 2500 variants/partition).

**Syntax:**
```bash
spark4vcf [spark-args] vep \
  --offline --cache --no_stats \
  --force_overwrite \
  --dir_cache /path/to/vep_cache \
  --vcf --af --appris --biotype \
  --buffer_size 500 --check_existing --distance 5000 \
  --mane --polyphen b --pubmed --regulatory \
  --sift b --species homo_sapiens \
  --symbol --transcript_version --tsl \
  -i /data/input.vcf.gz \
  -o /data/output.vep.vcf.gz
```

**Example (local mode):**
```bash
spark4vcf --master local[*] vep \
  --offline --cache --no_stats --force_overwrite \
  --dir_cache /spark4vcf/tools/ensembl-vep/ \
  --vcf --af --appris --biotype \
  --buffer_size 500 --check_existing --distance 5000 \
  --mane --polyphen b --pubmed --regulatory \
  --sift b --species homo_sapiens \
  --symbol --transcript_version --tsl \
  -i /data/1KGP.chr22.900000.vcf.gz \
  -o /data/output/1KGP.chr22.vep.vcf.gz
```

**How it works:**
1. The input VCF is uploaded to HDFS (if local).
2. The header is extracted and variant lines are partitioned into chunks of 2500 (can modify and rebuild).
3. Each Spark task runs VEP on its chunk, writing output to STDOUT.
4. Results are merged back into a single output file (local or HDFS).
