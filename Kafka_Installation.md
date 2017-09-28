## Kafka Introduction and Installation
---

### Contents
1. Kafka introduction
    1. Kafka Server/broker
    2. Kafka topic
    3. Kafka producer
    4. Kafka consumer
2. Role of zookeeper
3. Confluent platform
    1. Need for Confluent
    2. Downloading and installing confluent
4. Configuration
    1. Zookeeper
    2. Kafka server
    3. Kafka Rest proxy server
5. Starting the services
    1. Zookeeper server
    2. Kafka server
    3. Kafka Rest proxy server
6. Creating a topic
7. Starting console producer and consumer
8. Connecting spark streaming with Kafka Server
9. Providing input JSON data for Kafka Server
10. Viewing the output

#### 1. Kafka Introduction
Kafka is a publish/subscribe messaging system to process streams of data efficiently  in real time. It achieves efficiency by storing the data on distributed replicated cluster.

![kafka architecture](https://user-images.githubusercontent.com/19308726/29339699-aeaebfc0-821b-11e7-991c-98a08caddc58.JPG)

[Reference] (http://cloudurable.com/blog/kafka-architecture/index.html)

The key components of Kafka core.
###### 1.1 Kafka broker/server
  A single server in a Kafka cluster is called a broker. It acts as an intermediate connector between the producer and the consumer. Once the broker receives the messages it assigns offset to them and stores it on the disk .It provides access to the messages based on consumer’s request.  
###### 1.2. Kafka Topic
It is a sub-division of a broker which is similar to a table or a folder in a file system where each topic represents a category of messages. Topics are further divided into partitions.
###### 1.3. Kafka Producer
Producers are those which publishes the messages to topics on Kafka brokers which will be then subscribed by the consumers. They are also called as publishers.
###### 1.4. Kafka Consumer
Consumers pulls the messages those are published by the producers on the topics which are subscribed by the consumer (or) subscriber.
#### 2. Role of Zookeeper
It stores the metadata of all the brokers those are running on the node where zookeeper is installed.
#### 3. Confluent platform
It is an open source platform built based on apache Kafka to organize and manage data from different sources. Apart from providing a system to transport data it also provides tools services and  to connect to the data sources such as connectors for JDBC, REST proxy to connect with web applications. It also provides few more services like Schema Registry which is used to manage Kafka topics metadata, Kafka Streams, Kafka Connect that are not included in Kafka Core.

![confluent platform](https://user-images.githubusercontent.com/19308726/29339702-b21a10d8-821b-11e7-8f26-c82543d53573.JPG)

[Reference] (http://docs.confluent.io/current/platform.html)

###### 3.1. Need for Confluent platform
Since we need to stream the data from GPS devices REST proxy helps us to post the GPS data to the Kafka broker which eliminates the need for intermediate producer.
###### 3.2. Downloading and installing confluent
Download confluent from [here] (https://www.confluent.io/download/).
Once the download is complete unzip or untar the file accordingly using the below commands.
```
$ unzip filename.zip
$ tar -xvzf filename.tar.gz
```
Now we are ready to use the services and tools provided by Confluent.
#### 4. Configuration
Before using starting to use Apache Kafka we need to configure the following services.
###### 4.1. Zookeeper
  - Go to the directory **_/etc/zookeeper/conf_** and change the variable _clientPort_ in zoo.cfg configuration file to run the zookeeper server on the client node.

  **_server.x = hostname:port1:port2_**
  where  
  _x -> sequence for the servers  
  hostname -> the ip address or hostname of zookeeper server  
  port1 -> for follower connection if the server itself is a leader   
  port2 -> to communicate between other servers during leader election_

###### 4.2. Kafka Server
  - The Kafka server properties are configured using the file _server.properties_ in the directory **_{KAFKA-HOME}/config_**
  - By default the server will run on _localhost_ port number _9092_. But by changing the value of the variable _listeners  = PLAINTEXT://hostname:port_ we can run the server on different nodes.
  - While starting another server on different the value of the variable boker.id should be changed to an unique number.
  -Since we need to have a zookeeper server running to start a Kafka server we have to set the variable _zookeeper.connect=hostname:port_ where the default value will be _localhost:2181_ (i.e.,) where the zookeeper server will run by default.

###### 4.3. REST proxy server (Kafka Rest Server)
  - To configure the properties of the Kafka REST server edit the file _kafka-rest.properties_ in the directory **_/confluent/etc/kafka-rest_**
  - We can change the _ID_ of the server using the variable _id_ and we assign the _hostname_ and _port number_ where zookeeper is running to the variable _zookeeper.connect_ like how we did in _server.properties_ file.

#### 5. Starting the services
###### 5.1. Zookeeper Server
  Start the zookeeper by going to the directory **_{ZOOKEEPER-HOME}zookeeper/bin/_** and running the following command.
  ```
  $ sudo ./zkServer.sh start
  ```

###### 5.2. Kafka Server
  To start the Kafka server run the following command from Kafka home directory.
  ```
  $ bin/kafka-server-start.sh config/server.properties
  ```

###### 5.3. Kafka REST Server
  Start the Kafka REST server by running the following command from Confluent home directory.
  ```
  $ bin/kafka-rest-start etc/kafka-rest/kafka-rest.properties
  ```

#### 6. Creating a topic
  Once the Kafka server is up and running we can create a topic using the below command from Kafka home directory.
  ```
  $ bin/kafka-topics.sh --zookeeper hostname:port --create --topic topicname --partition x --replication-factor x
  ```
  where  
  _x -> Defines the number of partitions, minimum value should be 1  
  hostname:port -> where zookeeper is running_

#### 7. Staring Console Producer and Consumer
  Once the topic is created we can check whether the topic is working by creating a console producer and consumer.
  Use the following commands to start a producer and a consumer respectively.
  ```
  $ bin/kafka-console-producer.sh --broker-list hostname:port --topic topicname
  ```
  where   
  _hostname:port -> hostname and port where Kafka Server is running_
  ```
  $ bin/kafka-console-consumer.sh --zookeeper hostname:port --topic topicname
  ```
  _hostname:port -> hostname and port where zookeeper is running_

  To receive all the messages from the beginning of a producer use the below command.
  ```
    $ bin/kafka-console-consumer.sh --zookeeper hostname:port --topic topicname --from-beginning
  ```
  Now send a message from the producer console and the message will be received in the consumer console.

#### 8. Connecting Spark Streaming with Kafka Server
We can connect Kafka server with Apache Spark to process the messages and store it to a database.
To use Kafka with Spark we need to add the below dependency in pom.xml file.
```
<dependency>
  <groupId>org.apache.spark</groupId>
  <artifactId>spark-streaming-kafka_2.10</artifactId>
  <version>1.6.0</version>
</dependency>
```
To connect to kafka broker use the below code in spark java class.
```
Map<String, String> kafkaParameters = new HashMap<String, String>();
kafkaParameters .put("bootstrap.servers", "hostname:port");
Set<String> topic = Collections.singleton("topicname");
```
where   
_hostname:port -> hostname and port where Kafka Server is running_

Once the connection is established we can receive the messages from the broker using the below code.
```
JavaPairInputDStream<String, String> kafkaStreams = KafkaUtils.createDirectStream(ssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParameters , topic);
JavaDStream<String> messages = kafkaStreams.map(Tuple2::_2);
```
where   
_ssc -> spark streaming context
topic -> set object which contains the list of topics_

#### 9. Providing input JSON data for Kafka Server
We can provide JSON data to Kafka Server using the below curl command in the terminal.
```
$ curl -X POST -H "Content-Type: application/vnd.kafka.v1+json" --data '{"records":[{"value":{"foo":"bar"}}]}' "http://hostname:8082/topics/topicname"
```
where   
Content-Type -> Specifies that its JSON data sent to Kafka Server
hostname -> hostname where Kafka REST server is running
Providing data to Kafka Server using Barefoot’s stream.py which generates JSON data that contains time, ID and GPS position.
  - Clone barefoot Github repository from [here] (https://github.com/bmwcarit/barefoot.git) and untar it.
  - Go to to the directory /barefoot/util/submit and replace the last line of the file stream.py with the below code.
```
subprocess.call("curl -X POST -H \"Content-Type: application/vnd.kafka.json.v1+json\" --data '{\"records\":[{\"value\": %s}]}' 'http://hostname:8082/topics/gps'" % (json.dumps(sample)), shell=True)
```
where   
hostname -> hostname of the REST server

- Go back to the barefoot directory and run the below command.
```
$ python util/submit/stream.py --file src/test/resources/com/bmwcarit/barefoot/matcher/x0001-001.json
```
This command generates the above mentioned GPS data that contains  time, ID and GPS position.

#### 10. Viewing the output
We can view the GPS JSON data from a Console Consumer that subscribes to the topic that receives the JSON data or by connecting to that particular topic from Apache spark.
