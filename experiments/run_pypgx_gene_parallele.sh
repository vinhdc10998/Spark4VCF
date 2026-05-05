#!/bin/bash

LOG_FILE="./output/log/pypgx_benchmark_results_remake_parallel.txt"
mkdir -p ./output/log
> "$LOG_FILE"

declare -A GENE_TO_CHR=(
    ["ABCB1"]="chr7" ["CACNA1S"]="chr1" ["CFTR"]="chr7" ["CYP2B6"]="chr19"
    ["CYP2C19"]="chr10" ["CYP2C9"]="chr10" ["CYP2D6"]="chr22" ["CYP3A5"]="chr7"
    ["CYP4F2"]="chr19" ["DPYD"]="chr1" ["F5"]="chr1" ["IFNL3"]="chr19"
    ["NAT2"]="chr8" ["NUDT15"]="chr13" ["RYR1"]="chr19" ["SLCO1B1"]="chr12"
    ["TPMT"]="chr6" ["UGT1A1"]="chr2" ["VKORC1"]="chr16"
)

# GENES=(
#   "ABCB1" "CACNA1S" "CFTR" "CYP2C19" "CYP2C9" "CYP3A5"
#   "CYP4F2" "DPYD" "F5" "IFNL3" "NAT2" "NUDT15" "RYR1" "SLCO1B1" "TPMT"
#   "UGT1A1" "VKORC1"
# )

GENES=("CYP2D6" "CYP2B6")
PARALLEL_JOBS=32

echo "Starting benchmark for ${#GENES[@]} genes with ${PARALLEL_JOBS} parallel jobs..." | tee -a "$LOG_FILE"

# # Index unique chromosome VCFs once
# for chr in $(printf "%s\n" "${GENE_TO_CHR[@]}" | sort -u); do
#     vcf="/spark4vcf/data/CCDG_14151_B01_GRM_WGS_2020-08-05_${chr}.Total.norm.vcf.gz"
#     if [[ ! -f "${vcf}.tbi" && ! -f "${vcf}.csi" ]]; then
#         echo "Indexing $vcf" | tee -a "$LOG_FILE"
#         bcftools index -t "$vcf"
#     fi
# done

run_gene() {
    local gene="$1"
    local chr="$2"
    local input_vcf="./data/CCDG_14151_B01_GRM_WGS_2020-08-05_${chr}.Total.norm.vcf.gz"
    local output_dir="./output/pypgx/1KGP-${gene}-spark4vcf/"

    {
        echo "--------------------------------------------"
        echo "Started: $(date)"
        echo "Processing Gene: $gene (Chromosome: $chr)"
        echo "Input VCF:       $input_vcf"
    } >> "$LOG_FILE"

    /usr/bin/time -a -o "$LOG_FILE" -f "Gene $gene Real Time: %E" \
        pypgx run-ngs-pipeline "$gene" \
        "$output_dir" \
        --variants "$input_vcf" \
        --assembly GRCh38 \
        --force >> "$LOG_FILE" 2>&1

    echo "Finished Gene: $gene at $(date)" >> "$LOG_FILE"
}

export -f run_gene
export LOG_FILE

for gene in "${GENES[@]}"; do
    echo "$gene ${GENE_TO_CHR[$gene]}"
done | xargs -n 2 -P "$PARALLEL_JOBS" bash -c 'run_gene "$1" "$2"' _

echo "Benchmark complete. Results saved to $LOG_FILE"