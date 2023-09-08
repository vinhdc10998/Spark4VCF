# va-spark

va-spark is a scalable and high performance toolkit for the analysis, annotation, and prioritization of genomic variants.

## Introduction

va-spark was created by the software development team at Vinbigdata's Biomedical Information center, which leverages spark parallelism to speed up data processing times of genomic annotate tools like vep, annovar, snpeff, etc. With a simple architecture, making the integration of tools like vep, annovar, snpeff with spark easy and effective, the results of the integration is remarkable. The architecture of VEP is shown in the following figure:

#<<<<<<< HEAD
#![va-spark integration flow](/data/img/Spark4VCF.drawio.pdf)
#=======
[va-spark integration flow](/data/img/Spark4VCF.drawio.png)
#>>>>>>> b27cc48 (fix image)

## Table of contents
* [Deploy and run va-spark on AWS EC2 instances](/docs/aws_deployment.md)
* [Deploy and run va-spark on local PC with virtualbox](/docs/virtualbox_deployment.md)
