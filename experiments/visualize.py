import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# =========================
# Helper functions
# =========================
def hhmmss_to_minutes(s):
    """
    Convert 'HH:MM:SS' or 'MM:SS.xx' to minutes.
    """
    if pd.isna(s) or s == "":
        return np.nan

    parts = str(s).split(":")
    if len(parts) == 3:
        h, m, sec = parts
        return int(h) * 60 + int(m) + float(sec) / 60
    elif len(parts) == 2:
        m, sec = parts
        return int(m) + float(sec) / 60
    else:
        raise ValueError(f"Unsupported time format: {s}")

def seconds_to_minutes(x):
    if pd.isna(x) or x == "":
        return np.nan
    return float(str(x).replace(" seconds", "").strip()) / 60

def median_speedup(baseline, spark):
    return np.nanmedian(baseline) / np.nanmedian(spark)

def add_speedup_bracket(ax, x1, x2, y, h, text):
    ax.plot([x1, x1, x2, x2], [y, y + h, y + h, y], lw=1.0, color="black")
    ax.text((x1 + x2) / 2, y + h * 1.08, text, ha="center", va="bottom", fontsize=10)

# =========================
# Raw data
# =========================
raw = pd.DataFrame({
    "Fold": [1,2,3,4,5,6,7,8,9,10],
    "PyPGx": [
        "02:20:02","02:17:23","02:12:37","02:15:14","02:15:32",
        "02:14:59","02:14:46","02:14:46","02:15:45","02:16:37"
    ],
    "PyPGx-Spark4VCF": [
        "30:30.08","30:20.52","30:10.37","30:29.73","31:42.29",
        "30:17.07","31:07.69","30:52.16","31:01.57","31:09.22"
    ],
    "VEP": [
        "44572 seconds","48468 seconds","48153 seconds","48577 seconds","48430 seconds",
        "48466 seconds","51256 seconds","51823 seconds","52742 seconds",""
    ],
    "VEP-Spark4VCF": [
        "03:06:47","02:58:52","02:53:30","03:04:28","03:00:55",
        "02:58:20","03:03:37","02:54:24","03:08:13","05:48:33"
    ],
    "GATK-HaplotypeCaller": [
        "10:21:58","10:46:51","10:01:22","09:14:44","10:18:02",
        "10:12:10","10:19:21","10:44:22","10:26:27","10:52:41"
    ],
    "GATK-HaplotypeCaller-Spark4VCF": [
        "02:40:39","02:43:34","02:42:02","02:41:45","02:51:12",
        "02:43:30","02:31:51","02:46:04","03:36:15","02:42:04"
    ]
})

# =========================
# Convert to minutes
# =========================
df = pd.DataFrame()
df["Fold"] = raw["Fold"]
df["PyPGx_min"] = raw["PyPGx"].apply(hhmmss_to_minutes)
df["PyPGx-Spark4VCF_min"] = raw["PyPGx-Spark4VCF"].apply(hhmmss_to_minutes)
df["VEP_min"] = raw["VEP"].apply(seconds_to_minutes)
df["VEP-Spark4VCF_min"] = raw["VEP-Spark4VCF"].apply(hhmmss_to_minutes)
df["GATK-HaplotypeCaller_min"] = raw["GATK-HaplotypeCaller"].apply(hhmmss_to_minutes)
df["GATK-HaplotypeCaller-Spark4VCF_min"] = raw["GATK-HaplotypeCaller-Spark4VCF"].apply(hhmmss_to_minutes)

# Save converted minutes table
df.to_csv("output/benchmark_in_minutes.csv", index=False)

print("Converted data in minutes:")
print(df)

# =========================
# Plot boxplot (3 panels)
# =========================
plt.rcParams.update({
    "font.family": "DejaVu Sans",
    "font.size": 10,
    "axes.titlesize": 12,
    "axes.labelsize": 11,
    "xtick.labelsize": 10,
    "ytick.labelsize": 10,
    "pdf.fonttype": 42,
    "ps.fonttype": 42,
})

fig, axes = plt.subplots(1, 3, figsize=(10.5, 4.2), constrained_layout=True, sharey=True)

panels = [
    ("GATK-HaplotypeCaller", df["GATK-HaplotypeCaller_min"].values, df["GATK-HaplotypeCaller-Spark4VCF_min"].values),
    ("PyPGx", df["PyPGx_min"].values, df["PyPGx-Spark4VCF_min"].values),
    ("VEP", df["VEP_min"].values, df["VEP-Spark4VCF_min"].values),
]

# Calculate global limits for common y-axis
all_values_log = []
for _, b, s in panels:
    all_values_log.extend(np.log10(b[~np.isnan(b)]))
    all_values_log.extend(np.log10(s[~np.isnan(s)]))
global_ymin = np.min(all_values_log)
global_ymax = np.max(all_values_log)

for i, (title, baseline, spark) in enumerate(panels):
    ax = axes[i]

    # log10 transformation for the plot
    baseline_plot = np.log10(baseline[~np.isnan(baseline)])
    spark_plot = np.log10(spark[~np.isnan(spark)])

    bp = ax.boxplot(
        [baseline_plot, spark_plot],
        positions=[1, 2],
        widths=0.55,
        patch_artist=True,
        medianprops=dict(color="black", linewidth=1.2),
        boxprops=dict(linewidth=1.0, color="black"),
        whiskerprops=dict(linewidth=1.0, color="black"),
        capprops=dict(linewidth=1.0, color="black"),
        flierprops=dict(
            marker="o",
            markersize=4,
            markerfacecolor="black",
            markeredgecolor="black",
            linestyle="none"
        ),
    )

    # colors
    bp["boxes"][0].set_facecolor("#d9c4a3")   # baseline
    bp["boxes"][1].set_facecolor("#a9c2df")   # spark

    ax.set_title(title, pad=8)
    ax.set_xticks([1, 2])
    ax.set_xticklabels(["Baseline", "Spark4VCF"])
    # ax.set_yscale("log") # Removed as we are plotting log10 values directly

    if i == 0:
        ax.set_ylabel(r"Running time (log$_{10}$ minutes)")

    ax.grid(axis="y", which="major", linestyle="--", linewidth=0.6, alpha=0.5)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    # speedup annotation
    sp = median_speedup(baseline, spark)
    # Use local ymax for bracket height to keep them close to the data, 
    # but the axis is shared so they will be visually comparable.
    local_ymax_plot = max(np.nanmax(baseline_plot), np.nanmax(spark_plot))
    y = local_ymax_plot + 0.15
    h = 0.08
    add_speedup_bracket(ax, 1, 2, y, h, f"{sp:.1f}× speedup")
    
# Set common ylim for all axes
for ax in axes:
    ax.set_ylim(bottom=global_ymin - 0.2, top=global_ymax + 0.5)

fig.suptitle("Running-time Comparison in 10-Fold", fontsize=16, fontweight="bold")

# Export
plt.savefig("output/running_time_comparison_10fold_minutes.pdf", format="pdf", bbox_inches="tight")
plt.savefig("output/running_time_comparison_10fold_minutes.png", dpi=600, bbox_inches="tight")

plt.show()