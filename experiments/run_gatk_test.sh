#!/bin/bash

# Define the log file
LOG_FILE="/spark4vcf/output/gatk/gatk_spark4vcf_10fold.txt"

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
    --conf spark.sql.shuffle.partitions=2 \
    --conf spark.default.parallelism=2 \
    --conf spark.network.timeout=1000s \
    --conf spark.executor.heartbeatInterval=60s \
    --conf spark.executor.memoryOverhead=4G \
    --conf spark.driver.host=cluster1 \
    --conf spark.driver.bindAddress=0.0.0.0 \
    gatk \
        HaplotypeCaller \
        --java-options -Xmx4g \
        -R /data/Bam/Homo_sapiens_assembly38.fasta \
        -I /data/Bam/HG00131-1-0-1-0.sorted.hg38.test.bam.sorted.sampled.bam \
        -O /spark4vcf/output/gatk/gatk_spark4vcf_$i.g.vcf.gz \
        -ERC GVCF 


    echo "FOLD $i - Finished at: $(date)" | tee -a $LOG_FILE
    
    # Give the cluster 10 seconds to clean up containers and release RAM
    sleep 10
done

echo "Benchmark complete. Results saved to $LOG_FILE"

