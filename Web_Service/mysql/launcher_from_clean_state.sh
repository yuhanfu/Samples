# Ubuntu Server 16.04 LTS (ami-cd0f5cb6)
sudo apt-get update

# install java
sudo apt-get install -y openjdk-8-jdk

# install maven
sudo apt-get install -y maven

# run maven
mvn clean package

# start server
mvn exec:java