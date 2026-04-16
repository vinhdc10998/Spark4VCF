VAGRANT_HOME="/home/vagrant"
NODE_INDEX="$1"
NODE_COUNT="$2"
IP_PREFIX="$3"
#export DEBIAN_FRONTEND=noninteractive
sudo apt-get -y update

# install vim
sudo apt-get install -y vim htop r-base

# install jdk8
sudo apt-get install -y software-properties-common
sudo add-apt-repository -y ppa:openjdk-r/ppa
# sudo apt-get -y update
sudo apt-get install -y openjdk-8-jdk
sudo apt-get -y install ssh
sudo apt-get install openssh-server openssh-client -y
sudo apt-get -y install openjdk-8-jdk-headless

# Install Pixi (modern package manager for bioinformatics)
echo "Installing Pixi..."
curl -fsSL https://pixi.sh/install.sh | bash

# Add Pixi to system-wide PATH for Spark workers
# Pixi installs by default to ~/.pixi/bin/pixi
# We link it to /usr/local/bin to make it universally accessible
sudo ln -sf $HOME/.pixi/bin/pixi /usr/local/bin/pixi

# Configure Pixi to avoid conflicts with host's synced .pixi folder
# We tell Pixi to store environments locally on the node's disk
mkdir -p /home/vagrant/.pixi_local
echo 'file_system_relationship = "copy"' >> /home/vagrant/.pixi_config.toml # Optional: use copies instead of hardlinks for stability

# Pre-build the environment on the node (optional, speeds up first run)
# cd /spark4vcf && pixi install

PRIVATE_IP="${IP_PREFIX}${NODE_INDEX}"
PRIVATE_HOSTNAME="cluster${NODE_INDEX}"

cat <<EOF | sudo tee /etc/profile.d/spark4vcf-network.sh >/dev/null
export SPARK_LOCAL_IP=${PRIVATE_IP}
export SPARK_LOCAL_HOSTNAME=${PRIVATE_HOSTNAME}
EOF

grep -q "SPARK_LOCAL_IP=${PRIVATE_IP}" /home/vagrant/.bashrc || cat <<EOF >> /home/vagrant/.bashrc

export SPARK_LOCAL_IP=${PRIVATE_IP}
export SPARK_LOCAL_HOSTNAME=${PRIVATE_HOSTNAME}
EOF

mkdir /tmp/hadoop/ && cd /tmp/hadoop/
wget https://archive.apache.org/dist/hadoop/core/hadoop-2.7.3/hadoop-2.7.3.tar.gz
tar -xzf hadoop-2.7.3.tar.gz

# -----------------------------------------------------------------------
# Fix: Spark 2.x + Java 17 -- add-opens flags for reflective access
# Java 9+ (module system) blocks Spark 2.x's internal reflection.
# These flags restore the access Spark 2.x needs.
# -----------------------------------------------------------------------
SPARK_HOME="/usr/local/spark"  # Adjust if your Spark is installed elsewhere

if [ -d "$SPARK_HOME/conf" ]; then
  JAVA_OPENS="--add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED \
    --add-opens=java.base/java.net=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens=java.base/sun.security.action=ALL-UNNAMED \
    --add-opens=java.base/sun.util.calendar=ALL-UNNAMED"

  cat >> "$SPARK_HOME/conf/spark-defaults.conf" <<EOF

# --- Java 17 compatibility (added by bootstrap.sh) ---
spark.driver.extraJavaOptions   $JAVA_OPENS
spark.executor.extraJavaOptions $JAVA_OPENS
spark.driver.bindAddress        0.0.0.0
EOF
  echo "spark-defaults.conf updated with Java 17 --add-opens flags."
else
  echo "WARNING: Spark conf directory not found at $SPARK_HOME/conf. Skipping spark-defaults.conf update."
  echo "Please manually add --add-opens flags to your spark-submit command."
fi
