#!/usr/bin/env bash
# environmental variables / aliases for the data pipeline
# set by calling
# source ./pipeline-env.sh
# may need to first call >sudo chmod -x ./pipeline-env.sh

#Paths to major components
export DATAPIPELINE=~/Documents/SMOM/DataPipelineTestArchitecture 
export KAFKA=~/Documents/SMOM/kafka_2.12-2.5.0
export HADOOP=~/Documents/SMOM/hadoop-3.2.1
export SPARK=~/Documents/SMOM/spark-3.0.0-bin-hadoop3.2
export ZEPPELIN=~/Documents/SMOM/zeppelin-0.9.0-preview1-bin-all

#Kafka
#For add-on connectors (mtconnect, mqtt, etc.)
export KafkaConnectors=$DATAPIPELINE/connectors 
export KafkaConfig=$DATAPIPELINE/config

#Pyspark
export PYSPARK_PYTHON=python3
export LD_LIBRARY_PATH=$HADOOP/lib/native

#MTConnect simulator
export MTConnectAdapterSim=~/Documents/SMOM/parts_processes-master
export MTConnectAdapterSimLog=~/Documents/SMOM/parts_processes-master
export RUBY=/usr/bin/ruby
#export MTConnectAgent=

