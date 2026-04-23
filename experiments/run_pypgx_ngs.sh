#!/bin/bash

# Define the log file
LOG_FILE="./output/log/pypgx_benchmark_results_remake_10times.txt"

# Clear the log file before starting
> $LOG_FILE

# Define the list of genes and their chromosomes (GRCh38)
declare -A GENE_TO_CHR=(
    ["ABCB1"]="chr7" ["CACNA1S"]="chr1" ["CFTR"]="chr7" ["CYP2B6"]="chr19"
    ["CYP2C19"]="chr10" ["CYP2C9"]="chr10" ["CYP2D6"]="chr22" ["CYP3A5"]="chr7"
    ["CYP4F2"]="chr19" ["DPYD"]="chr1" ["F5"]="chr1" ["IFNL3"]="chr19"
    ["NAT2"]="chr8" ["NUDT15"]="chr13" ["RYR1"]="chr19" ["SLCO1B1"]="chr12"
    ["TPMT"]="chr6" ["UGT1A1"]="chr2" ["VKORC1"]="chr16"
)

GENES=("ABCB1" "CACNA1S" "CFTR" "CYP2B6" "CYP2C19" "CYP2C9" "CYP2D6" "CYP3A5" "CYP4F2" "DPYD" "F5" "IFNL3" "NAT2" "NUDT15" "RYR1" "SLCO1B1" "TPMT" "UGT1A1" "VKORC1")
GENES=("CYP2B6" "CYP2D6")

echo "Starting 10-fold benchmark for ${#GENES[@]} genes..." | tee -a $LOG_FILE

for i in {1..1}
do
    echo "============================================" | tee -a $LOG_FILE
    echo "FOLD $i - Started at: $(date)" | tee -a $LOG_FILE
    
    for GENE in "${GENES[@]}"
    do
        CHR=${GENE_TO_CHR[$GENE]}
        INPUT_VCF="/spark4vcf/data/CCDG_14151_B01_GRM_WGS_2020-08-05_${CHR}.Total.norm.vcf.gz"
        OUTPUT_DIR="/spark4vcf/output/pypgx/1KGP-${GENE}-spark4vcf/"
        bcftools index $INPUT_VCF
        echo "--------------------------------------------" | tee -a $LOG_FILE
        echo "Processing Gene: $GENE (Chromosome: $CHR)" | tee -a $LOG_FILE
        echo "Input VCF:       $INPUT_VCF" | tee -a $LOG_FILE
        
        # Use /usr/bin/time to capture the real elapsed time
        /usr/bin/time -a -o $LOG_FILE -f "Fold $i Gene $GENE Real Time: %E" \
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
            pypgx run-ngs-pipeline "$GENE" \
            "$OUTPUT_DIR" \
            --variants "$INPUT_VCF" \
            --assembly GRCh38 \
            --force
    done

    echo "FOLD $i - Finished at: $(date)" | tee -a $LOG_FILE
    
    # Give the cluster 10 seconds to clean up containers and release RAM
    sleep 10
done

echo "Benchmark complete. Results saved to $LOG_FILE"
