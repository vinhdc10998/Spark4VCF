#!/bin/bash
# Description: Install spark4vcf from source

echo "Installing Spark4VCF and dependencies..."

# Ensure sbt is installed
if ! command -v sbt &> /dev/null; then
    echo "SBT is not installed. Attempting to install SBT..."
    sudo apt-get update
    sudo apt-get install apt-transport-https curl gnupg -y
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
    sudo apt-get update
    sudo apt-get install sbt -y
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR"

echo "Building Spark4VCF JAR via sbt assembly..."
sbt assembly

if [ $? -ne 0 ]; then
    echo "SBT build failed."
    exit 1
fi

echo "Copying wrapper binary to ~/.local/bin/spark4vcf..."
mkdir -p ~/.local/bin
cp spark4vcf ~/.local/bin/spark4vcf
chmod +x ~/.local/bin/spark4vcf

JAR_FILE=$(find target/scala-* -name "vaspark-*.jar" | head -n 1)
if [ -n "$JAR_FILE" ]; then
    # Create a link or copy the jar next to it
    cp "$JAR_FILE" ~/.local/bin/vaspark-0.1.jar
    echo "Finished!"
    echo "Please ensure ~/.local/bin is in your PATH. Try running: spark4vcf"
else
    echo "Error: Built JAR file not found."
    exit 1
fi
