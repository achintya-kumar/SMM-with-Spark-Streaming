package mapMatchingWithSparkStreaming;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kafka.serializer.StringDecoder;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.JSONObject;

import scala.Tuple2;

import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.matcher.MatcherSample;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class Driver {

	public static void main(String[] args) throws InterruptedException, IOException {
		
		SparkConf conf = new SparkConf().setAppName("spark_kafka").setMaster("local[4]");
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.milliseconds(20000));
		
		//Create table if it doesn't exist
		Configuration hBaseConfiguration = HBaseConfiguration.create();
		initializeHBaseTable(hBaseConfiguration);

	    
		
		Broadcast<BroadcastUtility> broadcasted = ssc.sparkContext().broadcast(new BroadcastUtility());
		
		System.out.println("streaming");
		
		//Kafka streaming
		Map<String, String> kafkaParams = new HashMap<String, String>();
		kafkaParams.put("bootstrap.servers", "10.0.2.15:9092");
		Set<String> topic = Collections.singleton("gps");
		
		
		JavaPairInputDStream<String, String> kafkaStreams = KafkaUtils.createDirectStream(ssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topic);
		JavaDStream<String> lines = kafkaStreams.map(Tuple2::_2);
		JavaPairDStream<String, String> linesInPairedFormWithID = lines.mapToPair(line -> {
			JSONObject json = new JSONObject(line);
			String deviceID = (String) json.get("id");
			return new Tuple2<>(deviceID, line);
		});
		
		//lines.print();
		//linesInPairedFormWithID.print();
		JavaPairDStream<String, Iterable<String>> linesInPairedFormWithIDAndGroupedByID = linesInPairedFormWithID.groupByKey();
		linesInPairedFormWithIDAndGroupedByID.print();
		
		JavaPairDStream<String, List<MatcherSample>> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherSamples = linesInPairedFormWithIDAndGroupedByID.mapValues(v -> {
			Iterator<String> iterator = v.iterator();
			List<MatcherSample> listOfSamples = new ArrayList<>();
			while(iterator.hasNext()) {
				listOfSamples.add(new MatcherSample(new JSONObject(iterator.next())));
			}
			
			return listOfSamples;
		});
		
		JavaPairDStream<String, MatcherKState> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState = linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherSamples.mapValues(v -> {
			return broadcasted.getValue().getMatcher().mmatch(v, 1, 150);
		});
		
		linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState.mapValues(x -> "Result = " + x.sequence().get(0).point().geometry().getY() + ", " + x.sequence().get(0).point().geometry().getX()).print();
		
		JavaPairDStream<String, String> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToKStateJSON = linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState.mapValues(v -> v.toJSON().toString());
		
		linesInPairedFormWithIDAndGroupedByIDAndValueMappedToKStateJSON.foreachRDD(rdd -> {
			rdd.foreach(v -> {
				println(v._1 + ", " + v._2);
				broadcasted.getValue().savePair(new String(v._1), new String(v._2));
			});
		});
		
		//hbaseContext.streamBulkPut(linesInPairedFormWithIDAndGroupedByIDAndValueMappedToKStateJSON, TableName.valueOf("samples"), null);
		
		ssc.start();
		ssc.awaitTermination();
	}
	
	private static void initializeHBaseTable(Configuration con) throws MasterNotRunningException, ZooKeeperConnectionException, IOException {
		
		  System.out.println(System.currentTimeMillis());
	      // Instantiating HbaseAdmin class
	      HBaseAdmin admin = new HBaseAdmin(con);
	      
	      // Instantiating table descriptor class
	      HTableDescriptor tableDescriptor = new HTableDescriptor();
	      tableDescriptor.setName(Bytes.toBytes("samples"));

	      // Adding column families to table descriptor
	      tableDescriptor.addFamily(new HColumnDescriptor("kstate"));

	      // Execute the table through admin
	      if(!admin.tableExists(Bytes.toBytes("samples"))) {
	    	  admin.createTable(tableDescriptor);
	    	  System.out.println(" Table created ");
	      } else 
	    	  System.out.println("Table already exists!");
	      
	      System.out.println(System.currentTimeMillis());
	}
	
	private static void println(String s) {
		System.out.println("outout = " + s);
		
	}
	
	
}