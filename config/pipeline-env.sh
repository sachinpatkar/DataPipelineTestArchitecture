#!/usr/bin/env bash
# environmental variables / aliases for the data pipeline
# set by calling
# source ./pipeline-env.sh
# may need to first call >sudo chmod -x ./pipeline-env.sh

#Paths to major components
export DATAPIPELINE=/home/tim/DataPipelineTestArchitecture
export KAFKA=/home/tim/Kafka/kafka_2.12-2.5.0
export HADOOP=/home/tim/Kafka/hadoop-3.2.1
export SPARK=/home/tim/Kafka/spark-3.0.0-bin-hadoop3.2

#Kafka
#For add-on connectors (mtconnect, mqtt, etc.)
export KafkaConnectors=$DATAPIPELINE/connectors 
export KafkaConfig=$DATAPIPELINE/config

#Pyspark
export PYSPARK_PYTHON=python3
export LD_LIBRARY_PATH=$HADOOP/lib/native

#MTConnect simulator
export MTConnectAdapterSim=/etc/mtconnect/adapter
export MTConnectAdapterSimLog=/etc/mtconnect/adapter
export RUBY=/usr/bin/ruby
#export MTConnectAgent=

