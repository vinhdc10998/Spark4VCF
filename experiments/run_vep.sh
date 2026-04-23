#!/bin/bash

# ===== CONFIG =====
CMD="vep --offline --cache --no_stats \
--force_overwrite --dir_cache /spark4vcf/tools/ensembl-vep/ \
--vcf --af --appris --biotype \
--buffer_size 500 --check_existing --distance 5000 \
--mane --polyphen b --pubmed --regulatory \
--sift b --species homo_sapiens \
--symbol --transcript_version --tsl \
-i /data/1KGP.chr22.900000.vcf.gz \
-o output/vep/1KGP.chr22.norm.1000.vep.only.vcf.gz"

LOG_FILE="output/log/vep_runtime.log"

echo "===== VEP Benchmark (10 runs) =====" > $LOG_FILE

# ===== LOOP 10 TIMES =====
for i in {1..10}
do
    echo "Run $i started at $(date)" | tee -a $LOG_FILE

    START=$(date +%s)

    /usr/bin/time -v bash -c "$CMD" >> $LOG_FILE 2>&1

    END=$(date +%s)

    RUNTIME=$((END - START))

    echo "Run $i finished at $(date)" | tee -a $LOG_FILE
    echo "Run $i runtime: $RUNTIME seconds" | tee -a $LOG_FILE
    echo "----------------------------------------" | tee -a $LOG_FILE
done

echo "===== DONE =====" | tee -a $LOG_FILE
