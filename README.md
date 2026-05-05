# Spark4VCF

**Spark4VCF** is a scalable and high performance toolkit for the analysis, annotation, and prioritization of genomic variants.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Features](#2-features)
3. [Installation](#3-installation)
4. [Usage](#4-usage)
   - [VEP](#41-vep---variant-effect-predictor)
   - [PyPGx](#42-pypgx---pharmacogenomics-analysis)
   - [GATK](#43-gatk---genome-analysis-toolkit)

---

## 1. Introduction

Spark4VCF was created by the software development team at **Vinbigdata's Biomedical Information Center**. It leverages Apache Spark parallelism to speed up data processing times of genomic tools like VEP, GATK, PyPGx, etc.

With a simple architecture, the integration of bioinformatics tools with Spark is easy and effective. Each tool is wrapped as a Spark job — the driver partitions the input VCF into chunks, distributes annotation tasks across workers, and collects the results back into a single output file.

The architecture of Spark4VCF is shown in the following figure:

![Spark4VCF Architecture](./Spark4VCF.png)

---

## 2. Features

| Feature | Description |
|---|---|
| 🧬 **VEP integration** | Parallel annotation using Ensembl VEP |
| 💊 **PyPGx integration** | Distributed pharmacogenomics analysis per sample batch |
| 🔬 **GATK integration** | Parallelized interval-based variant calling with HaplotypeCaller |
| 🖥️ **Unified CLI** | Single `spark4vcf` entry point for all supported tools |
| 🚧 **ANNOVAR** *(in development)* | Spark-based wrapper for ANNOVAR annotation |
| 🚧 **DeepVariant** *(in development)* | Distributed deep learning variant calling with DeepVariant |
| 🚧 **SnpEff** *(in development)* | Parallel functional variant annotation with SnpEff |

---

## 3. Installation

### Prerequisites

- Java 8
- Apache Spark 2.4+
- [SBT](https://www.scala-sbt.org/) (build tool, auto-installed by `install.sh`)
- [Pixi](https://pixi.sh) for managing tool environments (VEP, PyPGx, etc.)

### Build from Source

```bash
git clone https://github.com/vinhdc10998/Spark4VCF.git
cd Spark4VCF
./install.sh
```

`install.sh` will:
1. Install SBT if not already present
2. Build the fat JAR via `sbt assembly`
3. Copy the `spark4vcf` wrapper script and `vaspark-0.1.jar` to `~/.local/bin/`

Make sure `~/.local/bin` is in your `PATH`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Install Tool Environments (Pixi)

Spark4VCF uses [Pixi](https://pixi.sh) to manage reproducible environments for each bioinformatics tool:

```bash
# Install default environment (VEP 115 + bcftools + PyPGx)
pixi install

# Install VEP 108 environment
pixi install -e vep108
```

### Deploy on a Local Cluster (VirtualBox)

Since we don't have multiple physical machines to build a full cluster, we simulate a multi-node Spark cluster by running Virtual Machines (VMs) using [VirtualBox](https://www.virtualbox.org/) and [Vagrant](https://www.vagrantup.com/). This approach lets us develop, test, and benchmark Spark4VCF on a realistic distributed setup from a single PC.

- 📄 [VirtualBox Deployment Guide](./docs/virtualbox_deployment.md)

### Deploy on AWS

- 📄 [AWS Deployment Guide](./docs/aws_deployment.md)

---

## 4. Usage

### Setting Up External Tool Paths

Spark4VCF relies on several external bioinformatics tools (`vep`, `gatk`, `pypgx`, `bcftools`, `samtools`). By default, it expects these tools to be available in your system's `$PATH`.

In a distributed cluster, you have two options:
1. **Install tools on every node:** Ensure the tools are installed and in the `$PATH` on all worker nodes.
2. **Use a shared network path:** If you have a shared filesystem (like NFS) accessible by all nodes, you only need to define the paths on the **driver node** before running Spark4VCF. Spark will automatically serialize and distribute these paths to the worker nodes!

To use custom or shared paths, export the following environment variables on your driver node before submitting your job:
```bash
# Example using a shared network path accessible to all nodes
export VEP_BIN=/shared/nfs/bin/vep
export GATK_BIN=/shared/nfs/bin/gatk
export PYPGX_BIN=/shared/nfs/bin/pypgx
export BCFTOOLS_BIN=/shared/nfs/bin/bcftools
export SAMTOOLS_BIN=/shared/nfs/bin/samtools
```

*Note: You have to verify your local tools are availabel, or correctly set up by running the `./install.sh` script, which includes a pre-flight environment check.*

---

The `spark4vcf` CLI is a friendly wrapper around `spark-submit`. It auto-locates the built JAR and routes arguments to the correct tool.

```
spark4vcf [spark-submit-args] <tool> [tool-args...]
```

**Valid tools:** `vep`, `pypgx`, `gatk`, `annovar`, `snpeff`, `deepvariant`

---

### Documentation for Tools

Detailed documentation for running each supported tool via Spark4VCF can be found below:

- 📄 [4.1 VEP — Variant Effect Predictor](./docs/vep.md)
- 📄 [4.2 PyPGx — Pharmacogenomics Analysis](./docs/pypgx.md)
- 📄 [4.3 GATK — Genome Analysis Toolkit](./docs/gatk.md)

---

## Project Structure

```
Spark4VCF/
├── src/main/scala/
│   └── extprg/
│       ├── vep/          # VEP Spark wrapper
│       ├── pypgx/        # PyPGx Spark wrapper
│       ├── gatk/         # GATK Spark wrapper
│       ├── snpeff/       # SnpEff Spark wrapper
│       ├── annovar/      # ANNOVAR Spark wrapper
│       └── deepvariant/  # DeepVariant Spark wrapper
├── docs/                 # Deployment guides (VirtualBox, AWS)
├── scripts/              # Cluster bootstrap scripts
├── ansible/              # Ansible playbooks for cluster setup
├── kubernetes/           # Kubernetes deployment manifests
├── pixi.toml             # Pixi environment definitions
├── build.sbt             # SBT build configuration
├── spark4vcf             # CLI wrapper script
└── install.sh            # Build and install script
```

---

## Tech Stack

| Component | Version |
|---|---|
| Apache Spark | 2.4.0 |
| Scala | 2.11.12 |
| Ensembl VEP | 108 / 115 |
| PyPGx | ≥ 0.26.0 |
| GATK | 4.1.9.0 |
| bcftools / samtools | 1.21 |
| Java | 8 |

---

## License

This project is licensed under the [MIT License](./LICENSE).

Developed and maintained by the **Biomedical Information Center, Vinbigdata**.
