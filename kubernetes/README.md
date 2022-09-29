### Download Spark to local
- Run `docker build -t spark:latest -f kubernetes/dockerfiles/spark/Dockerfile .` to build Docker image
- Run `docker login` to log in to Docker Hub
- Run `docker push docker_hub_username/spark` to push to Docker Hub
- Install minikube in local set up a Kubernetes cluster. See: https://kubernetes.io/docs/tasks/tools/install-minikube/
- Run `minikube start --driver=kvm2`
- Run Spark PI example in Kubernetes cluster 
```
sudo ./bin/spark-submit \
--master k8s://$(kubectl cluster-info | sed "s/.*(https:\/\/.*:8443).*/\1/" | head -1) \
--deploy-mode cluster \
--name spark-pi \
--class org.apache.spark.examples.SparkPi \
--conf spark.driver.cores=2 \
--conf spark.driver.memory=4g \
--conf spark.executor.cores=2 \
--conf spark.executor.memory=4g \
--conf spark.executor.instances=3 \
--conf spark.kubernetes.container.image=cuongdd2/spark:latest \
--conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
local:///opt/spark/examples/jars/spark-examples_2.12-3.0.0.jar 100
```
