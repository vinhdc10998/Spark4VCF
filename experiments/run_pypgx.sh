#!/bin/bash

# Define the log file
LOG_FILE="pypgx_only_benchmark_results_remake_2.txt"

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
      pypgx run-chip-pipeline CYP2D6 \
      /spark4vcf/output/pypgx/1KGP-CYP2D6-1000-spark4vcf/ \
      /data/1KGP.chr22.norm.1000.vcf.gz \
      --assembly GRCh38 --force

    echo "FOLD $i - Finished at: $(date)" | tee -a $LOG_FILE
    
    # Give the cluster 10 seconds to clean up containers and release RAM
    sleep 10
done

echo "Benchmark complete. Results saved to $LOG_FILE"
