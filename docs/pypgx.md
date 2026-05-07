### 4.2 PyPGx — Pharmacogenomics Analysis

Runs [PyPGx](https://pypgx.readthedocs.io) pharmacogenomics pipelines in parallel, distributing sample batches across Spark workers.

**Syntax:**
```bash
spark4vcf [spark-args] pypgx <pipeline> <GENE> <OUTPUT_DIR> <VCF_FILE> [pypgx-options]
```

**Supported pipelines:** `run-ngs-pipeline`, `run-chip-pipeline`

**Example (chip pipeline, local mode):**
```bash
spark4vcf --master local[*] pypgx \
  run-chip-pipeline CYP2D6 \
  /data/output/pypgx/CYP2D6/ \
  /data/1KGP.chr22.norm.1000.vcf.gz \
  --assembly GRCh38 --force
```

**Example (NGS pipeline):**
```bash
spark4vcf --master local[*] pypgx \
  run-ngs-pipeline CYP2D6 \
  /data/output/pypgx/CYP2D6-ngs/ \
  /data/variants.vcf.gz \
  --assembly GRCh38
```

**How it works:**
1. Sample names are extracted from the VCF using `bcftools`.
2. Samples are grouped into batches of 10 (can modify and rebuild) and written to temporary sample list files.
3. Each Spark task runs `pypgx` on its sample batch, writing to a unique subdirectory under `OUTPUT_DIR`.
