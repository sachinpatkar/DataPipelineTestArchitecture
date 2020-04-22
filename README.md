# Creating the Data Pipeline Architecture on Ubuntu

## Under-construction!
- This will become a primer on how-to set up the pipeline, with more extensive documentation coming later.
- Much of the guide will be geared toward dev environments, future guidance on multi-node, distributed implementations
- For the most part, we'll follow the Quick Start guide: https://kafka.apache.org/quickstart
- This was all developed and tested on Ubuntu 18.04. The sample commands assume:
  - You have Kafka 2.5.0 downloaded (see #1) and it's unzipped to your home directory, e.g. `/home/tim/`
  - Same for the DataPipelineTestArchitecture
  - If you have a different version or it's stored in a different directory, hopefully you can adapt the commands below

## 1) Download Apache Kafka and this repository
- https://kafka.apache.org/quickstart#quickstart_download
- `git clone https://github.com/usnistgov/DataPipelineTestArchitecture`

## 2) Do some stuff to the directories
- In the folder where you have Kafka (e.g. ./Kafka/kafka_2.12-2.5.0), create a folder called "connectors"
  - `mkdir ./Kafka/kafka_2.12-2.5.0/connectors`
- Copy `mtconnect-source-connector-1.0-SNAPSHOT.jar` from the DataPipelineTestArchitecture/connectors folder, and put it in the "./Kafka/kafka_2.12-2.5.0/connectors" folder
  - For example `cp ./DataPipelineTestArchitecture/connectors/mtconnect-source-connector-1.0-SNAPSHOT.jar ../Kafka/kafka_2.12-2.5.0/connectors`
- Copy `connect-mtconnectTcp-source-1.0-SNAPSHOT.jar` too
- Go to ./Kafka/kafka_2.12-2.5.0/config and open "connect-standalone.properties".
  - At the very bottom you'll see "#plugin.path= ...". Remove the # (uncommenting it) and put the full path of the "connectors" folder.    
  - For example: `plugin.path = /home/tim/Kafka/kafka_2.12-2.5.0/connectors`. This is telling Kafka connect to look in this folder for connectors.
- Copy the .properties files in DataPipelineTestArchitecture/config to ./Kafka/kafka_2.12-2.5.0/config
  - For example `cp ./DataPipelineTestArchitecture/config/connect-mtconnect-source.properties ../Kafka/kafka_2.12-2.5.0/config`

## 3) Edit the .properties files
- In `./Kafka/kafka_2.12-2.5.0/config`, open `connect-mtconnect-source.properties`
- Edit the agent url, path information, and destination topic (multiple agents can be added, separated by semicolons)
  - The example agent is from the Mazak testbed. For example:
  - `agent_url = http://mtconnect.mazakcorp.com:5612`
  - `device_path = path=//Device[@name=\"Mazak\"]` (notice the escape character \")
  - `topic_config = M80104K162N_XML`
- Note: If path is empty, the connector will grab the whole response document
- Note2: I've been naming the topics, by the deviceID plus the data format; more guidance on naming topics coming in the future


## 4) Start Kafka
- You'll need two separate terminal tabs open, and working in the kafka directory
  - For example, `cd ./Kafka/kafka_2.12-2.5.0`
- Start a Zookeeper instance: `bin/zookeeper-server-start.sh config/zookeeper.properties`
- Start a Kafka instance: `bin/kafka-server-start.sh config/server.properties`
- Note: it may be worth creating systemctl scripts to handle this?

## 5) Start MTConnect Agent connector
- This connector will collect the MTConnect XML Response document and store it in the specified topic
- Add a topic with topic name corresponding to the `connect-mtconnect-source.properties` file
  - `bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic M80104K162N_XML`
- Start the mtconnect connector, by running `bin/connect-standalone.sh config/connect-standalone.properties config/connect-mtconnect-source.properties`
- watch your data stream into kafka for hours on end
  - `bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic M80104K162N_XML --from-beginning
- Use `ctrl+c` to kill the process
  - Note: it'll run your battery down if you leave it running for too long
  
## 6) Start MTConnect Adapter connector
- This connector will collect the raw SHDR output from an MTConnect adapter
- This step assumes that you have an adapter to test with, or have installed the adapter simulator found here:
  - http://mtcup.org/wiki/Installing_C%2B%2B_Agent_on_Ubuntu
  - Once you do, you can start the adapter:
    - via systemctl: `sudo systemctl start mtc_adapter`
    - or `/usr/bin/ruby /etc/mtconnect/adapter/run_scenario.rb -l /etc/mtconnect/adapter/VMC-3Axis-Log.txt`
    - First is easier, but the second approach allows you to swap out the log file more easily
- Edit the `connect-mtconnectTCP-source.properties` file
- Add the topic
- Start the connector: `bin/connect-standalone.sh config/connect-standalone.properties config/connect-mtconnectTCP-source.properties`
- **Note**: if you want to run both connectors at the same time, you need to start them at the same time (using the same the Connect instance)
  - For example: `bin/connect-standalone.sh config/connect-standalone.properties config/connect-mtconnect-source.properties config/connect-mtconnectTCP-source.properties`
- watch your data stream into kafka for hours on end
  - `bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic VMC-3Axis_SHDR --from-beginning
- As always, use `ctrl+c` to kill the process

## 7) Managing the connectors via the REST API
- More information coming on how to manage the connectors separately using the REST API. Start, stop, add, delete connectors dynamically.


