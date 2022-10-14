# Deploy VA Spark cluster using VMs on Virtualbox

Vagrant is a great tool when it comes to deploy multiple virtual machines that have the exact system resources and libraries as the current running machines in different environments. You can find out more information about Vagrant in [here](https://www.vagrantup.com/).

The prerequisites in order to deploy VA spark cluster in multiple VMs on your Virtualbox are:

   1. Having installed Virtualbox setup in place.

   2. Installed vagrant tool

   3. General knowledge about how VM  works

Without ado, let’s jump right into the detailed steps for how to do it.

## I. Deploy the virtual machines

### 1. Create a new project folder in your local machine

```bash
mkdir -p projects/va-spark-vagrant
cd projects/va-spark-vagrant
mkdir resources

```
### 2.  Create a new shell script to tell vagrant to create the Ubuntu (18.04) machines with pre-installed tools like `java jdk, software-properties-common ..`

```bash
VAGRANT_HOME="/home/vagrant"

sudo apt-get -y update

# install vim
sudo apt-get install -y vim htop r-base

# install jdk8
sudo apt-get install -y software-properties-common python-software-properties
sudo add-apt-repository -y ppa:openjdk-r/ppa
sudo apt-get update
sudo apt-get install -y openjdk-8-jdk
```

### 3. Create vagrant script file

```bash
# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version">= 1.5.0"

ipAdrPrefix = "192.168.100.10"
memTot = 30000
numNodes = 4
memory = memTot/numNodes
cpuCap = 100/numNodes

Vagrant.configure(2) do |config|
	r = numNodes..1
	(r.first).downto(r.last).each do |i|
		config.vm.define "node-#{i}" do |node|
			#node.vm.box = "hashicorp/precise64"
			node.vm.box = "hashicorp/bionic64"
			node.vm.provider "virtualbox" do |v|
				v.name = "spark-node#{i}"
				v.customize ["modifyvm", :id, "--cpuexecutioncap", cpuCap]
				v.customize ["modifyvm", :id, "--memory", memory.to_s]
				v.customize ["modifyvm", :id, "--usb", "off"]
				v.customize ["modifyvm", :id, "--usbehci", "off"]
			end
			node.vm.network "private_network", ip: "#{ipAdrPrefix}#{i}"
			node.vm.hostname = "spark-node#{i}"
			node.vm.provision "shell" do |s|
				s.path = "./scripts/bootstrap.sh"
				s.args = "#{i} #{numNodes} #{ipAdrPrefix}"
				s.privileged = false
			end
		end
	end
end

```

## II.Hadoop installation

This tutorial will help you to install Apache Hadoop with a basic cluster. In the example below, there will be 1 master (also be used as a worker) node - cluster1, and 3 worker nodes - cluster2, cluster3, cluster4. More worker nodes can be used as users need. All nodes in the instruction use OS Ubuntu Server 18.04, with login user ubuntu, therefore the home directory will be /home/ubuntu/. Remember to replace your appropriate Home directory with /home/ubuntu/.

Below are the 4 nodes and their IP addresses I will be referring to here:
```bash
172.16.203.128 cluster1
172.16.203.129 cluster2
172.16.203.130 cluster3
172.16.203.131 cluster4

```


### 1. Install SSH on **all nodes**

```bash
sudo apt-get update
sudo apt-get install ssh
```

### 2. Add all our nodes to /etc/hosts on **all nodes**

```bash
sudo vim /etc/hosts
```
And paste this to the end of the file:

```bash 
/etc/hosts

172.16.203.128 cluster1
172.16.203.129 cluster2
172.16.203.130 cluster3
172.16.203.131 cluster4
```
### 3. Setup password-less login

Copy the key pem from local to AWS instance:

```bash
scp -i {pem_file} {pem_file_to_send} {username}@{dns_address_name}:~/.

```
Add an inbound rule for the network group we're using, to allow access from `cluster 1`. For example, the IP of cluster 1 is `172.31.1.136`, we can add an inbound rule  like the one below:

![Inbound rule set for cluster viewing](https://i.imgur.com/yJTzexR.png)

Generate key in cluster1

```bash
ssh-keygen -t rsa -P '' -f /home/ubuntu/.ssh/id_rsa
cat /home/ubuntu/.ssh/id_rsa.pub >> /home/ubuntu/.ssh/authorized_keys

```

And copy to `cluster2`, `cluster3`, `cluster4` by using `scp`:

```bash
scp -i path/to/keyfile.pem /home/ubuntu/.ssh/authorized_keys cluster2:/home/ubuntu/.ssh/authorized_keys
scp -i path/to/keyfile.pem /home/ubuntu/.ssh/authorized_keys cluster3:/home/ubuntu/.ssh/authorized_keys
scp -i path/to/keyfile.pem /home/ubuntu/.ssh/authorized_keys cluster4:/home/ubuntu/.ssh/authorized_keys

``` 
(or just manually copy the authorized_keys to the worker nodes)

### 4. Install JDK1.8 on **all 4 nodes**

```bash
sudo apt-get -y install openjdk-8-jdk-headless
```

### 5. Install Hadoop

Download Hadoop 2.7.3 in `all nodes`:

```bash
mkdir /tmp/hadoop/ && cd /tmp/hadoop/
wget https://archive.apache.org/dist/hadoop/core/hadoop-2.7.3/hadoop-2.7.3.tar.gz
```
Unzip

```bash
tar -xzf hadoop-2.7.3.tar.gz

```

Rename the directory for short

```bash
mv hadoop-2.7.3 /home/ubuntu/hadoop

```


Update `hadoop-env.sh` in **all nodes**:
```bash
vim /home/ubuntu/hadoop/etc/hadoop/hadoop-env.sh

```
In `hadoop-env.sh` file, file the line starts with `export JAVA_HOME=` and replaces it with the line below. If not found, then add the line at the end of the file.

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

```

Update `core-site.xml` in **all nodes**:
```bash
vim /home/ubuntu/hadoop/etc/hadoop/core-site.xml
```
The full content of `core-site.xml` in **all nodes**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
        <property>
                <name>fs.defaultFS</name>
                <value>hdfs://cluster1:9000</value>
        </property>
</configuration>
```
Update `hdfs-site.xml` in **all nodes** (The content of master node will be different):

```bash
vim /home/ubuntu/hadoop/etc/hadoop/hdfs-site.xml
```

The full content of `hdfs-site.xml` in **cluster1**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
        <property>
                <name>dfs.replication</name>
                <value>2</value>
        </property>
        <property>
                <name>dfs.namenode.name.dir</name>
                <value>file:/home/ubuntu/hadoop/hdfs/namenode</value>
        </property>
        <property>
                <name>dfs.datanode.data.dir</name>
                <value>file:/home/ubuntu/hadoop/hdfs/datanode</value>
        </property>
</configuration>
```

The full content of `hdfs-site.xml` in `cluster2`, `cluster3`, `cluster4`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
        <property>
                <name>dfs.replication</name>
                <value>2</value>
        </property>
        <property>
                <name>dfs.datanode.data.dir</name>
                <value>file:/home/ubuntu/hadoop/hdfs/datanode</value>
        </property>
</configuration>
```

Update `yarn-site.xml` in **all nodes**:

```xml
vim /home/ubuntu/hadoop/etc/hadoop/yarn-site.xml
```
The full content of `yarn-site.xml` in **all nodes**:
```xml

<?xml version="1.0"?>
<configuration>
        <property>
                <name>yarn.resourcemanager.resource-tracker.address</name>
                <value>cluster1:8025</value>
        </property>
        <property>
                <name>yarn.resourcemanager.scheduler.address</name>
                <value>cluster1:8030</value>
        </property>
 
        <property>
                <name>yarn.resourcemanager.address</name>
                <value>cluster1:8050</value>
        </property>
 
        <property>
                <name>yarn.nodemanager.aux-services</name>
                <value>mapreduce_shuffle</value>
        </property>
 
        <property>
                <name>yarn.nodemanager.aux-services.mapreduce.shuffle.class</name>
                <value>org.apache.hadoop.mapred.ShuffleHandler</value>
        </property>
        <property>
                <name>yarn.acl.enable</name>
                <value>0</value>
        </property>
        <property>
                <name>yarn.resourcemanager.hostname</name>
                <value>cluster1</value>
        </property>
        <property>
                <name>yarn.nodemanager.vmem-check-enabled</name>
                <value>false</value>
        </property>
        <property>
                <name>yarn.scheduler.maximum-allocation-mb</name>
                <value>16384</value>
        </property>
</configuration>
```
Update `mapred-site.xml` in **all nodes** (If not existed, create this file):

```bash
vim /home/ubuntu/hadoop/etc/hadoop/mapred-site.xml

```

The full content of `mapred-site.xml` in **all nodes**:
```xml
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
        <property>
                <name>mapreduce.framework.name</name>
                <value>yarn</value>
        </property>
        <property>
                <name>mapreduce.jobhistory.address</name>
                <value>cluster1:10020</value>
        </property>
        <property>
                <name>mapreduce.jobhistory.webapp.address</name>
                <value>hostName:19888</value>
        </property>
    <property>
            <name>yarn.app.mapreduce.am.env</name>
            <value>HADOOP_MAPRED_HOME=$HADOOP_HOME</value>
    </property>
    <property>
            <name>mapreduce.map.env</name>
            <value>HADOOP_MAPRED_HOME=$HADOOP_HOME</value>
    </property>
    <property>
            <name>mapreduce.reduce.env</name>
            <value>HADOOP_MAPRED_HOME=$HADOOP_HOME</value>
    </property>
</configuration>

``` 
Edit *slaves* (workers) file in **all nodes**:
 
```bash
vim /home/ubuntu/hadoop/etc/hadoop/slaves
```
And add the following lines (delete localhost if such a line exists):

```bash
[slaves file]

cluster1
cluster2
cluster3
cluster4
```
Edit `master` file in **all nodes**:

```bash
vim /home/ubuntu/hadoop/etc/hadoop/masters
```
And add the following lines (delete localhost if such a line exists):

```bash
[masters file]
cluster1
```

### 7.Setup environment variables in **all nodes**

```bash
vim /home/ubuntu/.bashrc
```
And add these lines to the end of the file:

```bash
export HADOOP_HOME="/home/ubuntu/hadoop"
export PATH=$PATH:$HADOOP_HOME/bin
export PATH=$PATH:$HADOOP_HOME/sbin
export HADOOP_MAPRED_HOME=${HADOOP_HOME}
export HADOOP_COMMON_HOME=${HADOOP_HOME}
export HADOOP_HDFS_HOME=${HADOOP_HOME}
export YARN_HOME=${HADOOP_HOME}
```

Now load the environment variables to the opened session

```bash
source /home/ubuntu/.bashrc
```
### 8. Create a data folder and set its permissions

In `cluster1`:

```bash
sudo rm -rf /home/ubuntu/hadoop/hdfs/namenode/
sudo rm -rf /home/ubuntu/hadoop/hdfs/datanode/
sudo mkdir -p /home/ubuntu/hadoop/hdfs/namenode/
sudo mkdir -p /home/ubuntu/hadoop/hdfs/datanode/
sudo chown ubuntu:ubuntu /home/ubuntu/hadoop/hdfs/namenode/
sudo chown ubuntu:ubuntu /home/ubuntu/hadoop/hdfs/datanode/
sudo chmod 777 /home/ubuntu/hadoop/hdfs/namenode/
sudo chmod 777 /home/ubuntu/hadoop/hdfs/datanode/
```
In `cluster2`, `cluster3`, `cluster4`:
```bash
sudo rm -rf /home/ubuntu/hadoop/hdfs/datanode/
sudo mkdir -p /home/ubuntu/hadoop/hdfs/datanode/
sudo chown ubuntu:ubuntu /home/ubuntu/hadoop/hdfs/datanode/
sudo chmod 777 /home/ubuntu/hadoop/hdfs/datanode/
```
I set chmod to 777 for easy access. You can change it if you want.

### 9. Format HDFS and start cluster

```bash
hdfs namenode -format
start-dfs.sh && start-yarn.sh
```

You should see the following lines:

![Console result for dfs and yarn start](https://i.imgur.com/6GhbnrX.png)

Run jps on cluster1 should list the following:
![Jps console result for cluster 1](https://i.imgur.com/vdYwSWn.png)

Run jps on cluster2, 3, 4 should list the following:
![Jps console result for cluster 2,3,4](https://i.imgur.com/y1YFqe4.png)
Update firewall rules for port 50070 and port 8088 to be accessible.
```bash
sudo ufw allow 50070
sudo ufw allow 8088
```
Then set up a security group for inbound rules value for ports `50070` and `8088` to be accessed from the internet (in this case from my IP address).

![Jps console result for cluster 2,3,4](https://i.imgur.com/gr0juwE.png)

By accessing `http://${cluster1}:50070` you should see the following HDFS web UI (where ${cluster_1} is the IP value you can retrieve from AWS console).

![Overview about the cluster](https://i.imgur.com/YjW73EA.png).

![Running instances](https://i.imgur.com/B8EZ7md.png).

By accessing `http://${cluster1}:8088` you should see the following YARN web UI.

![Hadoop server GUI](https://i.imgur.com/3MMtEsm.png).

Note that if you're having the issue which DataNode is not up and running in `cluster 1`:
![Issue about cluster 1](https://i.imgur.com/qyCkudz.png).

You would need to remove the following directories in `cluster 1`:

```bash
sudo rm -rf /home/ubuntu/hadoop/hdfs/namenode/
sudo rm -rf /home/ubuntu/hadoop/hdfs/datanode/
```
Then creating the new directories and set write permissions for it.

```bash
sudo mkdir -p /home/ubuntu/hadoop/hdfs/namenode/
sudo mkdir -p /home/ubuntu/hadoop/hdfs/datanode/
sudo chown ubuntu:ubuntu /home/ubuntu/hadoop/hdfs/namenode/
sudo chown ubuntu:ubuntu /home/ubuntu/hadoop/hdfs/datanode/
sudo chmod 777 /home/ubuntu/hadoop/hdfs/namenode/
sudo chmod 777 /home/ubuntu/hadoop/hdfs/datanode/
```

### 10. Test by uploading a file to HDFS

Writing and reading to HDFS is done with command hdfs dfs. First, manually create your home directory. All other commands will use a path relative to this default home directory: (note that ubuntu is my logged in user. If you login with different user then please use your user id instead of ubuntu).

```bash
hdfs dfs -mkdir -p /user/ubuntu/
```
Get a books file

```bash
wget -O alice.txt https://www.gutenberg.org/files/11/11-0.txt
```

Upload downloaded file to hdfs using -put:
```bash
hdfs dfs -mkdir books
hdfs dfs -put alice.txt books/alice.txt
```

List a file on hdfs:

```bash
hdfs dfs -ls books/
```
![hdfs dfs ls](https://i.imgur.com/dtsi2jX.png).
You can also check in HDFS Web UI

### 11. Stopping cluster
```bash
stop-yarn.sh && stop-dfs.sh
```

## III. Spark installation

### 1. Download Apache spark version 2.4.0 to cluster1

```bash
cd /home/ubuntu/
wget https://archive.apache.org/dist/spark/spark-2.4.0/spark-2.4.0-bin-hadoop2.7.tgz
```

### 2. Unzip the file's contents and rename the folder to spark

```bash
tar -xzf spark-2.4.0-bin-hadoop2.7.tgz
mv spark-2.4.0-bin-hadoop2.7 spark
```
### 3. Add spark environment variables to `.bashrc` in `cluster1`:
Open file in vim editor:
```bash
vim /home/ubuntu/.bashrc
```
Add the below variables to the end of the file: 

```bash
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export SPARK_HOME=/home/ubuntu/spark
export PATH=$PATH:$SPARK_HOME/bin
export LD_LIBRARY_PATH=$HADOOP_HOME/lib/native:$LD_LIBRARY_PATH
```

Now load the environment variables to the opened session by running below command:
```bash
source /home/ubuntu/.bashrc
```
In case if you added to .profile file then restart your session by logging out and logging in again.

### 4. Run Sample spark job to test the installation

```bash
spark-submit --master local\[*\] --deploy-mode client --class org.apache.spark.examples.SparkPi $SPARK_HOME/examples/jars/spark-examples_2.11-2.4.0.jar 10
```
Notes: when running with VEP, need to run hdfs dfsadmin -safemode leave to disable safemode.


## IV. VEP installation

### 1. Install required packages

```bash
sudo apt-get update
sudo apt-get install libdbi-perl gcc libdbd-mysql-perl perl=5.26.1-6ubuntu0.5 gcc=4:7.4.0-1ubuntu2.3 g++=4:7.4.0-1ubuntu2.3 make=4.1-9.1ubuntu1 libbz2-dev=1.0.6-8.1ubuntu0.2 liblzma-dev=5.2.2-1.3 libpng-dev=1.6.34-1ubuntu0.18.04.2 uuid-dev=2.31.1-0.4ubuntu3.7 cpanminus libmysqlclient-dev mysql-server unzip=6.0-21ubuntu1.1 git make unzip libpng-dev uuid-dev bcftools
sudo cpanm Archive::Zip
sudo cpanm Archive::Extract
sudo cpanm DBD::mysql
sudo cpanm Set::IntervalTree
sudo cpanm JSON
sudo cpanm PerlIO::gzip
wget http://hgdownload.cse.ucsc.edu/admin/jksrc.zip
unzip jksrc.zip
cd kent/src/lib
export MACHTYPE=i686
make
cd ..
export KENT_SRC=`pwd`
sudo cpanm Bio::DB::BigFile

```

### 2. Install vep version 100

```bash
git clone https://github.com/Ensembl/ensembl-vep.git
cd ensembl-vep
git checkout release/100
perl INSTALL.pl
```

**Notes**:
- Cache version: 100
- GRCh38
- Fasta: Homo sapiens
- Plugins: All
- All else configs: Default

### 3. Test VEP
Copy data file from local to AWS EC2 instance
```bash
scp -i ${pem_file} ${path_to_data_file_local} ${user_name_ec2_machine}@${ec2_ip}:${path_to_folder_ec2}
```

```
/home/ubuntu/ensembl-vep/vep --format vcf --no_stats --force_overwrite --dir_cache /home/ubuntu/.vep --offline --vcf --vcf_info_field ANN --buffer_size 60000 --phased --hgvsg --hgvs --symbol --variant_class --biotype --gene_phenotype --regulatory --ccds --transcript_version --tsl --appris --canonical --protein --uniprot --domains --sift b --polyphen b --check_existing --af --max_af --af_1kg --af_gnomad --minimal --allele_number --pubmed --fasta /home/ubuntu/.vep/homo_sapiens/100_GRCh38/Homo_sapiens.GRCh38.dna.toplevel.fa.gz --input_file ALL.chr22.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf --output_file f1_b60000.vcf
```

## V. VASpark installation

### 1. Install required packages

```bash
sudo apt-get update
sudo apt-get install scala=2.11.12-4~18.04 zip=3.0-11build1
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install sbt 1.3.8
```

### 2. Install VASpark

```bash
git clone https://github.com/variant-annotation/va-spark.git
cd va-spark/
git checkout fix/snpeff
sbt assembly
```

### 3. Test VASpark
```bash
(time spark-submit --master yarn --deploy-mode cluster --executor-memory 4g --num_executors 4 --executor_cores 2 /home/ubuntu/vep-spark/target/scala-2.11/vepspark_2.11-0.1.jar --vep_dir /home/ubuntu/ensembl-vep/vep --format vcf --no_stats --force_overwrite --cache_dir /home/ubuntu/.vep --offline --vcf --vcf_info_field ANN --phased --hgvsg --hgvs --symbol --variant_class --biotype --gene_phenotype --regulatory --ccds --transcript_version --tsl --appris --canonical --protein --uniprot --domains --sift b --polyphen b --check_existing --af --max_af --af_1kg --af_gnomad --minimal --allele_number --pubmed --fasta /home/ubuntu/.vep/homo_sapiens/100_GRCh38/Homo_sapiens.GRCh38.dna.toplevel.fa.gz --input_file /user/ubuntu/data/ALL.chr22.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf --output_file /user/output/vs_10gb_nop34_r4_non4_442.vcf) &> time_vs_10gb_nop34_r8_non4_442.txt
```
