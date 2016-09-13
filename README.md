flumer-log4j
============

This project is a sample Java application, Avro serializer, and poorly written step-by-step instructions for sending log4j events to HDFS via Flume and querying with Impala.

Disclaimer: I am not a Hadoop expert. Much of what I've implemented here can be improved, and some may simply not even be the "Hadoop Way". This project was a learning exercise.

Design Goals
------------

TODO

Setup
-----

### CDH 5.5.0 QuickStart VM ###

Install and start the CDH QuickStart VM. 100% of my interaction with the VM is as the _cloudera_ user (password _cloudera_) or as root (password: _cloudera_). Your VM might have different users or passwords.

#### Java ####

If you prefer or require Java 8, you will need to install it on the VM. Setting JAVA_HOME in bigtop-utils does not set the property for user's profile; it sets it only for Hadoop applications. (For what it's worth, I have not been able to determine where JAVA_HOME gets set for users. I've tried `~/.bashrc`, `~/.bash_profile`, and `/etc/profile` with no luck.) 

1. [Install the Java 8 JDK](http://www.cloudera.com/documentation/enterprise/5-5-x/topics/cdh_ig_jdk_installation.html) to /usr/java.
2. Change the owner to root: `sudo chown -R root /usr/java/jdk1.8.0_[build]/`
3. Set JAVA_HOME in `/etc/default/bigtop-utils`.

#### Networking ####

Create host mapping on host to VM (i.e., update `/etc/hosts/` with an entry for quickstart.cloudera that points to the IP address of the VM).

### HDFS ###

Create a directory where log data will be stored:

    [cloudera@quickstart ~]$ hadoop fs -mkdir log-data
    [cloudera@quickstart ~]$ hadoop fs -ls
    Found 1 items
    drwxr-xr-x   - cloudera cloudera          0 2016-09-01 14:42 log-data

### Flume ###

The Flume topology is simple and consists of two agents. The first takes our log events, serializes them, and sends them to the second agent. The second agent re-serializes them so they can be handled by Impala and stores them in HDFS.

#### Agent 2: Flume Event -> HDFS ####

Define the second of two agents in `/etc/hadoop/conf/logging-agent.properties`:

    # Specify the source, channel and sink for this agent
    logging-agent.sources = logging-src
    logging-agent.channels = logging-chan
    logging-agent.sinks = logging-sink

    # Set up the source
    logging-agent.sources.logging-src.type = avro
    logging-agent.sources.logging-src.bind = 0.0.0.0
    logging-agent.sources.logging-src.port = 5555
    logging-agent.sources.logging-src.channels = logging-chan

    # set up the channel
    logging-agent.channels.logging-chan.type = memory
    logging-agent.channels.logging-chan.capacity = 1000

    # set up our sink
    logging-agent.sinks.logging-sink.type = hdfs
    logging-agent.sinks.logging-sink.hdfs.path = /user/cloudera/log-data/ymd=%Y-%m-%d/h=%k
    logging-agent.sinks.logging-sink.hdfs.filePrefix = raw.log
    logging-agent.sinks.logging-sink.hdfs.fileSuffix=.avro
    logging-agent.sinks.logging-sink.hdfs.fileType = DataStream
    logging-agent.sinks.logging-sink.hdfs.useLocalTimeStamp = true
    logging-agent.sinks.logging-sink.serializer = com.mikemunhall.flumerlog4j2.FlumeEventStringBodySerializer$Builder
    logging-agent.sinks.logging-sink.serializer.compressionCodec=snappy
    logging-agent.sinks.logging-sink.channel = logging-chan
    logging-agent.sinks.logging-sink.hdfs.rollInterval = 10
    logging-agent.sinks.logging-sink.hdfs.rollSize = 0

#### Agent 1: Log4j -> Agent 2 ####

The first of the two agents agent is embedded into the application as a log4j appender.

    <?xml version="1.0" encoding="UTF-8"?>
    <Configuration status="WARN">
        <Appenders>
            <Console name="console" target="SYSTEM_OUT">
                <PatternLayout pattern="%d{ISO8601} - %-5level - %logger{36} - %msg%n"/>
            </Console>
            <File name="file" fileName="logs/app.log">
                <PatternLayout>
                    <PatternLayout pattern="%d{ISO8601} - %-5level - %logger{36} - %msg%n"/>
                </PatternLayout>
            </File>
            <Flume name="flume" compress="true" type="Embedded" ignoreExceptions="false">
                <Property name="channel.type">memory</Property>
                <Property name="sinks">avrosink</Property>
                <Property name="avrosink.channel">memory</Property>
                <Property name="avrosink.type">avro</Property>
                <Property name="avrosink.hostname">192.168.240.165</Property>
                <Property name="avrosink.port">5555</Property>
                <Property name="avrosink.batch-size">10</Property>
                <Property name="avrosink.serializer">avro_event</Property>
                <Property name="processor.type">failover</Property>
                <PatternLayout pattern="%d{ISO8601} - %-5level - %logger{36} - %msg%"/>
            </Flume>
        </Appenders>
        <Loggers>
            <Root level="trace">
                <AppenderRef ref="flume" />
                <AppenderRef ref="console"/>
                <AppenderRef ref="file"/>
            </Root>
        </Loggers>
    </Configuration>

### Impala ###

#### Start Impala shell ####

    impala-shell -i localhost --quiet

#### Show databases ####

    [localhost:21000] > show databases;
    +------------------+
    | name             |
    +------------------+
    | _impala_builtins |
    | default          |
    +------------------+

#### Create log_data database ####

    [localhost:21000] > create database log_data;

    [localhost:21000] > show databases;
    +------------------+
    | name             |
    +------------------+
    | _impala_builtins |
    | default          |
    | log_data         |
    +------------------+

#### Create log_records table ####

    [localhost:21000] > create external table log_records
                  > (body string, header_timestamp bigint, header_guid string)
                  > partitioned by (ymd string, h int)
                  > stored as avro
                  > location '/user/cloudera/log-data'
                  > tblproperties ('avro.schema.literal'='{
                  >   "type" : "record",
                  >   "name" : "Event",
                  >   "fields" : [
                  >     {
                  >       "name" : "body",
                  >       "type" : "string"
                  >     },
                  >     {
                  >       "name" : "header_timestamp",
                  >       "type" : "long"
                  >     },
                  >     {
                  >       "name" : "header_guid",
                  >       "type" : "string"
                  >     }
                  >   ]
                  > }');

    [localhost:21000] > show tables;
    +-------------+
    | name        |
    +-------------+
    | log_records |
    +-------------+
    [localhost:21000] > describe log_records;
    +------------------+--------+-------------------+
    | name             | type   | comment           |
    +------------------+--------+-------------------+
    | body             | string | from deserializer |
    | header_timestamp | bigint | from deserializer |
    | header_guid      | string | from deserializer |
    | ymd              | string |                   |
    | h                | int    |                   |
    +------------------+--------+-------------------+

### Avro Serializer ###

The second agent employs a custom Avro serializer that reformats the structure of the Avro event generated by the first agent. That serializer must be available to the second Flume agent. Package the Java project as a JAR (`mvn clean package`) and copy the JAR to `/usr/lib/flume-ng/lib/` on the VM.

Start the flume agent:

    flume-ng agent --conf /etc/hadoop/conf --conf-file /etc/hadoop/conf/logging-agent.properties --name logging-agent

### Sample App ###

1. Update the log4j configuration with the IP address or hostname (i.e., _quickstart.cloudera_) of the CDH VM.
2. Start the sample application (`mvn exec:java`) to begin logging to HDFS.

The application should be logging to the console and to a file in the `logs/` directory. It should also be streaming log events through the two Flume agents and into HDFS. 

### Verify ###

#### Data is being written to HDFS ####

    [cloudera@quickstart ~]$ hadoop fs -ls log-data
    Found 1 items
    drwxr-xr-x   - cloudera cloudera          0 2016-09-12 13:24 log-data/ymd=2016-09-12
    [cloudera@quickstart ~]$ hadoop fs -ls log-data/ymd=2016-09-12
    Found 1 items
    drwxr-xr-x   - cloudera cloudera          0 2016-09-12 13:33 log-data/ymd=2016-09-12/h=13
    [cloudera@quickstart ~]$ hadoop fs -ls log-data/ymd=2016-09-12/h=13
    Found 57 items
    -rw-r--r--   1 cloudera cloudera       1626 2016-09-12 13:24 log-data/ymd=2016-09-12/h=13/raw.log.1473711857347.avro
    ...
    -rw-r--r--   1 cloudera cloudera       1620 2016-09-12 13:33 log-data/ymd=2016-09-12/h=13/raw.log.1473712347312.avro
    -rw-r--r--   1 cloudera cloudera       1537 2016-09-12 13:33 log-data/ymd=2016-09-12/h=13/raw.log.1473712347313.avro.tmp

#### Avro is properly serializing files ####

    [cloudera@quickstart ~]$ hadoop fs -get log-data/ymd=2016-09-12/h=13/raw.log.1473712347312.avro
    [cloudera@quickstart ~]$ java -jar avro-tools-1.8.1.jar getschema raw.log.1473712347312.avro
    log4j:WARN No appenders could be found for logger (org.apache.hadoop.metrics2.lib.MutableMetricsFactory).
    log4j:WARN Please initialize the log4j system properly.
    log4j:WARN See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.
    {
        "type" : "record",
        "name" : "Event",
        "fields" : [ {
            "name" : "body",
            "type" : "string"
        }, {
            "name" : "header_timestamp",
            "type" : "long"
        }, {
            "name" : "header_guid",
            "type" : "string"
        } ]
    }
    [cloudera@quickstart ~]$ java -jar avro-tools-1.8.1.jar tojson raw.log.1473712347312.avro
    log4j:WARN No appenders could be found for logger (org.apache.hadoop.metrics2.lib.MutableMetricsFactory).
    log4j:WARN Please initialize the log4j system properly.
    log4j:WARN See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.
    {"body":"2016-09-13T08:55:58,415 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 1319989668\n        line two: next int: -1797315748\n","header_timestamp":1473778558415,"header_guid":"2e062680-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:55:59,415 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 975128831\n        line two: next int: -2050840613\n","header_timestamp":1473778559415,"header_guid":"2e9ebd01-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:00,416 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: -1947445954\n        line two: next int: 862721290\n","header_timestamp":1473778560416,"header_guid":"2f377a92-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:01,419 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 1464484431\n        line two: next int: -635068240\n","header_timestamp":1473778561419,"header_guid":"2fd08643-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:02,424 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 2015644971\n        line two: next int: 767086157\n","header_timestamp":1473778562424,"header_guid":"3069e014-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:03,425 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: -57266330\n        line two: next int: 753845475\n","header_timestamp":1473778563425,"header_guid":"31029da5-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:04,426 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 33957186\n        line two: next int: 1566128757\n","header_timestamp":1473778564426,"header_guid":"319b5b36-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:05,429 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 535462733\n        line two: next int: -1382405103\n","header_timestamp":1473778565429,"header_guid":"323466e7-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:06,434 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: -2098733426\n        line two: next int: -880104855\n","header_timestamp":1473778566434,"header_guid":"32cdc0b8-79c2-11e6-b261-985aebc6901e"}
    {"body":"2016-09-13T08:56:07,436 - INFO  - com.mikemunhall.flumerlog4j2.Main - next int: 39690956\n        line two: next int: -1869703628\n","header_timestamp":1473778567436,"header_guid":"3366a559-79c2-11e6-b261-985aebc6901e"}
    [cloudera@quickstart ~]$ java -jar avro-tools-1.8.1.jar getmeta raw.log.1473712347312.avro
    log4j:WARN No appenders could be found for logger (org.apache.hadoop.metrics2.lib.MutableMetricsFactory).
    log4j:WARN Please initialize the log4j system properly.
    log4j:WARN See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.
    avro.schema	{"type":"record","name":"Event","fields":[{"name":"body","type":"string"},{"name":"header_timestamp","type":"long"},{"name":"header_guid","type":"string"}]}
    avro.codec	snappy


#### Data is accessible to Impala ####

Impala must be informed when there is new a new partition. It must also be told to refresh its metastore when there is new data.

    [localhost:21000] > select count(*) from log_records;
    +----------+
    | count(*) |
    +----------+
    | 0        |
    +----------+
    [localhost:21000] > alter table log_records add partition(ymd='2016-09-12', h=13);
    [localhost:21000] > refresh log_records;

    [localhost:21000] > select count(*) from log_records;
    +----------+
    | count(*) |
    +----------+
    | 2140     |
    +----------+
    [localhost:21000] > alter table log_records add partition(ymd='2016-09-12', h=14);
    [localhost:21000] > refresh log_records;

    [localhost:21000] > select count(*) from log_records;
    +----------+
    | count(*) |
    +----------+
    | 2380     |
    +----------+
    [localhost:21000] > show partitions log_records;
    +------------+----+-------+--------+----------+--------------+-------------------+--------+-------------------+----------------------------------------------------------------------------+
    | ymd        | h  | #Rows | #Files | Size     | Bytes Cached | Cache Replication | Format | Incremental stats | Location                                                                   |
    +------------+----+-------+--------+----------+--------------+-------------------+--------+-------------------+----------------------------------------------------------------------------+
    | 2016-09-12 | 13 | -1    | 215    | 330.10KB | NOT CACHED   | NOT CACHED        | AVRO   | false             | hdfs://quickstart.cloudera:8020/user/cloudera/log-data/ymd=2016-09-12/h=13 |
    | 2016-09-12 | 14 | -1    | 216    | 325.46KB | NOT CACHED   | NOT CACHED        | AVRO   | false             | hdfs://quickstart.cloudera:8020/user/cloudera/log-data/ymd=2016-09-12/h=14 |
    +------------+----+-------+--------+----------+--------------+-------------------+--------+-------------------+----------------------------------------------------------------------------+

TODO
----

* This works for a single instance of a single application, but anything more complex would require a different, potentially more complex Flume topology.
* Maintenance of a Hadoop cluster (e.g., purging, compacting, balancing, filesystem checks, etc.) is not mentioned, but must be considered.
* Is there a Logback Flume appender?
* Configuration
    * Memory flume channels are great for testing, but in production file channels should probably be used instead.
    * Roll interval, roll size and a number of other properties should be adjusted or specified to avoid a "small files" problem.
* Parititioning
    * Re-partition ymd to y, m, d
    * Add partitions for app, instance
* Investigate Parquet
* Investigate Kudu
