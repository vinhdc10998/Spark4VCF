FROM ensemblorg/ensembl-vep

ARG spark_uid=185
#ARG spark_version="spark-3.0.0"
#ARG spark_version="spark-3.0.0-bin-hadoop3.2"
ARG spark_version="spark-3.0.0-bin-without-hadoop"

USER root

WORKDIR /opt

RUN set -v && \
    apt-get update && \
    apt install -y openjdk-8-jre && \
    curl -O https://archive.apache.org/dist/spark/spark-3.0.0/${spark_version}.tgz && \
    ls -la && \
    tar -xf ${spark_version}.tgz && \
    mv ${spark_version} spark && \
    cp /opt/spark/kubernetes/dockerfiles/spark/entrypoint.sh /opt/ && \
    mkdir -p /opt/spark/work-dir && \
    chgrp root /etc/passwd && chmod ug+rw /etc/passwd && \
    rm -rf /var/cache/apt/*

ENV SPARK_HOME /opt/spark

WORKDIR /opt/spark/work-dir
RUN chmod g+w /opt/spark/work-dir

ENTRYPOINT [ "/opt/entrypoint.sh" ]

USER ${spark_uid}