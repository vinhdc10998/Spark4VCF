#!/bin/bash

# Define the log file
LOG_FILE="/spark4vcf/output/gatk/gatk_10fold.txt"

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
    /spark4vcf/tools/gatk-4.1.9.0/gatk --java-options -Xmx4g HaplotypeCaller \
    -R /data/Bam/Homo_sapiens_assembly38.fasta \
    -I /data/Bam/HG00131-1-0-1-0.sorted.hg38.test.bam.sorted.sampled.bam \
    -O /data/Bam/HG00131-1-0-1-0.sorted.hg38_1.vcf -ERC GVCF
    echo "FOLD $i - Finished at: $(date)" | tee -a $LOG_FILE
    
    # Give the cluster 10 seconds to clean up containers and release RAM
    sleep 10
done

echo "Benchmark complete. Results saved to $LOG_FILE"
