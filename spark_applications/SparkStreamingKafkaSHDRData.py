
"""
 Consumes SHDR data messages from VMC-3Axis_SHDR topic in Kafka and parses them using JSON schema to extract key, values, timestamps.
 
 Usage: SparkStreamingKafkaSHDRData.py <bootstrap-servers> <subscribe-type> <topics>
   <bootstrap-servers> The Kafka "bootstrap.servers" configuration. A
   comma-separated list of host:port.
   <subscribe-type> There are three kinds of type, i.e. 'assign', 'subscribe',
   'subscribePattern'.
   |- <assign> Specific TopicPartitions to consume. Json string
   |  {"topicA":[0,1],"topicB":[2,4]}.
   |- <subscribe> The topic list to subscribe. A comma-separated list of
   |  topics.
   |- <subscribePattern> The pattern used to subscribe to topic(s).
   |  Java regex string.
   |- Only one of "assign, "subscribe" or "subscribePattern" options can be
   |  specified for Kafka source.
   <topics> Different value format depends on the value of 'subscribe-type'.

 Running our spark program to process SHDR data

 NOTE: change path to checkpoint directory in line 212

`$ ./bin/spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.0.0-preview2,\
org.apache.spark:spark-token-provider-kafka-0-10_2.12:3.0.0-preview2,\
org.apache.spark:spark-streaming-kafka-0-10_2.12:3.0.0-preview2,\
org.apache.spark:spark-core_2.12:3.0.0-preview2,\
org.apache.spark:spark-streaming-kafka-0-10-assembly_2.12:3.0.0-preview2 \
/Users/sar6/Documents/TimSprockProject/DataPipelineTestArchitecture/spark_applications/SparkStreamingKafkaSHDRData.py localhost:9092 subscribe VMC-3Axis_SHDR


"""
from __future__ import print_function

import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import split 
from pyspark.sql.functions import from_json, col

from pyspark.sql.types import *
from pyspark.sql.functions import *      # for window() function

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("""
        Usage: SparkStreamingKafkaSHDRData.py <bootstrap-servers> <subscribe-type> <topics>
        """, file=sys.stderr)
        sys.exit(-1)

    bootstrapServers = sys.argv[1]
    subscribeType = sys.argv[2]
    topics = sys.argv[3]

    # Start the Spark session
    spark = SparkSession\
        .builder\
        .appName("StructuredKafkaWordCount")\
        .getOrCreate()

    # Define the input data json schema needed to interpret the "value" in kafka stream
    # note: "schema", "payload" here must match the names within the value data. otherwise it won't work
    # this is probably not ideal as it is hardcoded. Maybe we can load a small batch of data and first infer schema
    jsonSchema = StructType().add("schema", StringType())\
                             .add("payload", StringType())
    print("\njsonSchema")
    print(jsonSchema)
    # StructType(List(StructField(schema,StringType,true),StructField(payload,StringType,true)))

    # Create DataSet representing the stream of input lines from kafka
    lines = spark\
        .readStream\
        .format("kafka")\
        .option("kafka.bootstrap.servers", bootstrapServers)\
        .option("kafka.batch.size",10)\
        .option(subscribeType, topics)\
        .load()

    print("\nschema of lines")
    lines.printSchema()
    # root
    #  |-- key: binary (nullable = true)
    #  |-- value: binary (nullable = true)
    #  |-- topic: string (nullable = true)
    #  |-- partition: integer (nullable = true)
    #  |-- offset: long (nullable = true)
    #  |-- timestamp: timestamp (nullable = true)
    #  |-- timestampType: integer (nullable = true)

    # Create new dataframe using JSON schema to parse key and value
    parsedlines = lines.select( \
      from_json(col("key").cast("string"),jsonSchema).alias("parsedkey"), \
      from_json(col("value").cast("string"),jsonSchema).alias("parsedvalue"), \
      col("topic"), \
      col('offset'), \
      col('timestamp') )

    print("\nschema of parsedlines")
    parsedlines.printSchema() 
    # root
    #  |-- parsedkey: struct (nullable = true)
    #  |    |-- schema: string (nullable = true)
    #  |    |-- payload: string (nullable = true)
    #  |-- parsedvalue: struct (nullable = true)
    #  |    |-- schema: string (nullable = true)
    #  |    |-- payload: string (nullable = true)
    #  |-- topic: string (nullable = true)
    #  |-- offset: long (nullable = true)
    #  |-- timestamp: timestamp (nullable = true)

    # extract the payload from parsedkey and rename it to "key"
    parsedData = parsedlines.select("parsedkey.payload","parsedvalue","topic","offset","timestamp")
    parsedData = parsedData.withColumnRenamed("payload", "key")

    # extract the payload from parsedvalue and rename it to "value"
    parsedData2 = parsedData.select("key","parsedvalue.payload","topic","offset","timestamp")
    parsedData = parsedData2.withColumnRenamed("payload", "values")

    # Now split the lines in "value" to extract sensor_timestamp and actual value
    split_col = split(parsedData['values'], '\|')
    parsedData = parsedData.withColumn('sensor_timestamp', split_col.getItem(0))
    parsedData = parsedData.withColumn('value', split_col.getItem(2))
    # parsedData = parsedData.drop('value') # drop the original value column now

    # maybe need to change sensor_timestamp cast as timestamp, example below
    # we need this timestampGMT as seconds for our Window time frame
    # df = df.withColumn('timestampGMT', df.timestampGMT.cast('timestamp'))
    # or
    # .withColumn('timestamp', unix_timestamp(col('EventDate'), "MM/dd/yyyy hh:mm:ss aa").cast(TimestampType())) \

    # Reorganize dataframe 
    parsedData = parsedData.select('key','value','sensor_timestamp','timestamp','offset','topic' )

    print("\nschema of parsedData")
    parsedData.printSchema()   
    # root
    #  |-- key: string (nullable = true)
    #  |-- value: string (nullable = true)
    #  |-- sensor_timestamp: string (nullable = true)
    #  |-- timestamp: timestamp (nullable = true)
    #  |-- offset: long (nullable = true)
    #  |-- topic: string (nullable = true) 

    # break out into separate dataframes - need to redo in a better way for it to automatically breakout based on new keys
    Xcom_DF = parsedData.filter(parsedData['key'] == "Xcom")
    print("\nschema of Xcom_DF")
    Xcom_DF.printSchema()    
    # root
    #  |-- key: string (nullable = true)
    #  |-- value: string (nullable = true)
    #  |-- sensor_timestamp: string (nullable = true)
    #  |-- timestamp: timestamp (nullable = true)
    #  |-- offset: long (nullable = true)
    #  |-- topic: string (nullable = true)


    Ycom_DF = parsedData.filter(parsedData['key'] == "Ycom")
    Xact_DF = parsedData.filter(parsedData['key'] == "Xact")
    Yact_DF = parsedData.filter(parsedData['key'] == "Yact")
    path_feedrate_DF = parsedData.filter(parsedData['key'] == "path_feedrate")
    line_DF = parsedData.filter(parsedData['key'] == "line")
    block_DF = parsedData.filter(parsedData['key'] == "block")


    # Group the data by window and key, and compute the average of each group (using kafka timestamp)

    windowDuration = "2 minutes" # gives the size of window, specified as integer number of seconds
    slideDuration = "1 minutes" # gives the amount of time successive windows are offset from one another,

    # should change this to be done based on sensor timestamp, not kafka event timestamp ?
    avgVals = parsedData\
        .withWatermark("timestamp", "5 minutes") \
        .groupBy(\
            window(parsedData.timestamp, windowDuration, slideDuration),\
            parsedData.key)\
        .agg(mean(parsedData.value)) 
        # .count()

    # avgVals = avgVals.withColumnRenamed("count", "value")
    avgVals = avgVals.withColumnRenamed("avg(value)", "value")

    print("\nschema of avgVals")
    avgVals.printSchema()      
    # root
    #  |-- window: struct (nullable = true)
    #  |    |-- start: timestamp (nullable = true)
    #  |    |-- end: timestamp (nullable = true)
    #  |-- key: string (nullable = true)
    #  |-- value: double (nullable = true)

    # query = parsedData\
    #     .writeStream\
    #     .outputMode('append')\
    #     .format('console')\
    #     .option('truncate', 'false')\
    #     .start()

    # Start running the query that prints the running averages to the console
    query_avg = avgVals\
        .writeStream\
        .outputMode('complete')\
        .format('console')\
        .option('truncate', 'false')\
        .start()

    # Write the above query to a kafka topic - not yet working
    query_Ycom = avgVals\
        .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)") \
        .writeStream\
        .format('kafka')\
        .option("kafka.bootstrap.servers", "localhost:9092")\
        .option('truncate', 'false')\
        .option("topic", "VMC-3Axis_Ycom")\
        .option("checkpointLocation", "~/Documents/TimSprockProject/Experiment/checkpoint")\
        .outputMode('update')\
        .start()

    # try this query with append or update and see if it works?

    query_avg.awaitTermination()


