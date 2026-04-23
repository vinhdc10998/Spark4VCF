#!/bin/bash

# Define the log file
LOG_FILE="/spark4vcf/output/vep/vep_spark4vcf_10fold.txt"

# Clear the log file before starting
> $LOG_FILE

echo "Starting 10-fold benchmark..." | tee -a $LOG_FILE

for i in {1..10}
do
    echo "============================================" | tee -a $LOG_FILE
    echo "FOLD $i - Started at: $(date)" | tee -a $LOG_FILE
    
    # Use /usr/bin/time to capture the real elapsed time
    # -a appends to the log, -o specifies the file, -f formats the output
    /usr/bin/time -a -o $LOG_FILE -f "Fold $i Real Time: %E" \
    spark4vcf \
        --master yarn \
        --deploy-mode cluster \
        --num-executors 4 \
        --executor-cores 2 \
        --executor-memory 4G \
        --conf spark.sql.shuffle.partitions=8 \
        --conf spark.default.parallelism=4 \
        --conf spark.network.timeout=1000s \
        --conf spark.executor.heartbeatInterval=60s \
        --conf spark.executor.memoryOverhead=4G \
        --conf spark.driver.host=cluster1 \
        --conf spark.driver.bindAddress=0.0.0.0 \
        vep \
        --cache \
        --no_stats \
        --force_overwrite \
        --dir_cache /spark4vcf/tools/ensembl-vep/ \
        --offline --vcf --af --appris --biotype --check_existing \
        --distance 5000 --mane --polyphen b --pubmed --regulatory \
        --sift b --species homo_sapiens --symbol --transcript_version \
        --tsl --buffer_size 500 \
        -i /data/1KGP.chr22.900000.vcf.gz \
        -o /spark4vcf/output/vep/1KGP.chr22.900000.spark4vcf_$i.vcf.gz

    echo "FOLD $i - Finished at: $(date)" | tee -a $LOG_FILE
    
    # Give the cluster 10 seconds to clean up containers and release RAM
    sleep 10
done

echo "Benchmark complete. Results saved to $LOG_FILE"
