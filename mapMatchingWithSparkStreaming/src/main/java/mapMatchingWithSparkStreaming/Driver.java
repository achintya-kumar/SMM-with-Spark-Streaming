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
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.spark.JavaHBaseContext;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.JSONObject;

import scala.Tuple2;

import com.bmwcarit.barefoot.matcher.MatcherFactory;
import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.matcher.MatcherSample;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class Driver {

	public static void main(String[] args) throws Exception {
		
		// Initializing SparkConf with 4 threads and StreamingContext with a batch interval of 20 seconds
		System.out.println("Local execution is DEACTIVATED!");
		SparkConf conf = new SparkConf().setAppName("spark_kafka")/*.setMaster("local[*]")*/;
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.milliseconds(12000));

		// Create table if it doesn't exist
		Configuration hBaseConfiguration = HBaseConfiguration.create();
		hBaseConfiguration.addResource("/etc/hbase/conf/core-site.xml");
		hBaseConfiguration.addResource("/etc/hbase/conf/hbase-site.xml");
		initializeHBaseTable(hBaseConfiguration);

	    JavaHBaseContext hbaseContext = new JavaHBaseContext(sc, hBaseConfiguration);
		// Broadcasting some utilities
		Broadcast<BroadcastedUtilities> broadcasted = ssc.sparkContext().broadcast(new BroadcastedUtilities());
		
		// Feeding Kafka with samples
		System.out.println("FEEDING KAFKA IS ENABLED!");
		simpleClient.Client.feedKafka();

		// Kafka streaming
		Map<String, String> kafkaParams = new HashMap<String, String>();
		kafkaParams.put("bootstrap.servers", "quickstart.cloudera:9092");
		kafkaParams.put("group.id", "map_group");
		kafkaParams.put("enable.auto.commit", "true");
		Set<String> topic = Collections.singleton("gps");

		// Getting streams from Kafka
		JavaPairInputDStream<String, String> kafkaStreams = KafkaUtils.createDirectStream(ssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topic);
		JavaDStream<String> lines = kafkaStreams.map(Tuple2::_2)/*ssc.socketTextStream("192.168.0.102", 1111) KEPT FOR TESTING PURPOSES!*/;

		// Creating a pair Dstream with the ID as the key
		JavaPairDStream<String, String> keySamplePair = lines.mapToPair(line -> {
					JSONObject json = new JSONObject(line);
					String deviceID = (String) json.get("id");
					return new Tuple2<>(deviceID, line);
				});

		// Grouping the Dstreams by the key
		JavaPairDStream<String, List<String>> keySamplePairInListForm = keySamplePair.mapValues(v -> {
			List<String> inListForm = new ArrayList<>();
			inListForm.add(v);
			return inListForm;
		});

		JavaPairDStream<String, List<String>> keySamplePairInListFormReducedByKey = keySamplePairInListForm.reduceByKey((a, b) -> { // <-- Replaced groupByKey with reduceByKey, because LESS SHUFFLING!
			a.addAll(b);
			return a;
		});
		
		//linesInPairedFormWithIDAndGroupedByID.print();
	
		JavaPairDStream<String, List<MatcherSample>> keySamplePairInListFormReducedByKeyAndValueMappedToMatcherSamples = keySamplePairInListFormReducedByKey.mapValues(v -> {
					Iterator<String> iterator = v.iterator();
					List<MatcherSample> listOfSamples = new ArrayList<>();
					while (iterator.hasNext()) {
						listOfSamples.add(new MatcherSample(new JSONObject(iterator.next())));
					}
					return listOfSamples;
		});
		
		JavaPairDStream<String, List<MatcherSample>> keySamplePairInListFormReducedByKeyAndValueMappedToMatcherSamplesSorted = keySamplePairInListFormReducedByKeyAndValueMappedToMatcherSamples.mapValues(v -> {
			Collections.sort(v, (a, b) -> {
				Long aTime = new Long(a.time());
				Long bTime = new Long(b.time());
				return aTime.compareTo(bTime);
			});
			
			return v;
		});

		JavaPairDStream<String, MatcherKState> keySamplePairInListFormReducedByKeyAndValueMappedToMatcherKState = keySamplePairInListFormReducedByKeyAndValueMappedToMatcherSamplesSorted.mapValues(v -> {
					System.out.println("oldKstateJSON = " + broadcasted.getValue().getKstateJSONfromHBase(v.get(0).id()));
					String oldKstateJSON = broadcasted.getValue().getKstateJSONfromHBase(v.get(0).id());
					if (oldKstateJSON == null)
						return broadcasted.getValue().getBarefootMatcher().mmatch(v, 1, 150);
					else {
						MatcherKState state = new MatcherKState(new JSONObject(oldKstateJSON), new MatcherFactory(broadcasted.getValue().getRoadMap()));
						// Updating the existing state retrieved from HBase
						for (MatcherSample sample : v)
							state.update(broadcasted.getValue().getBarefootMatcher().execute(state.vector(), state.sample(), sample), sample);

						return state;
					}
		});

//		The following action saves the rows one by one! Replaced with BulkPut but kept because it works more often than BulkPut!
//		linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState.foreachRDD(rdd -> {
//			rdd.foreach(value -> {
//				//println(value._1 + ", " + value._2.toJSON().toString());
//				broadcasted.getValue().saveKstateJSONtoHBase(new String(value._1), value._2);
//			});
//		});
		
		
		JavaDStream<String> javaDstream = keySamplePairInListFormReducedByKeyAndValueMappedToMatcherKState.map(v -> v._1 + "&" + v._2.toJSON().toString());
		
		hbaseContext.streamBulkPut(javaDstream, TableName.valueOf("samples"), new PutFunction());
		
		ssc.start();
		ssc.awaitTermination();
		ssc.close();
	}

	/**
	 * Initializing empty tables is necessary as computation on samples is sensitive to time information.
	 * @param con
	 * @throws MasterNotRunningException
	 * @throws ZooKeeperConnectionException
	 * @throws IOException
	 */
	private static void initializeHBaseTable(Configuration con)
			throws MasterNotRunningException, ZooKeeperConnectionException,
			IOException {
		
		//FOR TABLE 'samples'
		// Instantiating HbaseAdmin class
		HBaseAdmin admin = new HBaseAdmin(con);

		// Instantiating table descriptor class
		HTableDescriptor tableDescriptor = new HTableDescriptor();
		tableDescriptor.setName(Bytes.toBytes("samples"));

		// Adding column families to table descriptor
		tableDescriptor.addFamily(new HColumnDescriptor("kstate"));

		// Execute the table through admin
		if (!admin.tableExists(Bytes.toBytes("samples"))) {
			admin.createTable(tableDescriptor);
			System.out.println(" \'samples\' Table created ");
		} else {
			System.out.println(" \'samples\' Table already exists! Dropping and re-creating it...");
			admin.disableTable(Bytes.toBytes("samples"));
			admin.deleteTable(Bytes.toBytes("samples"));
			admin.createTable(tableDescriptor);
		}

		
		//FOR TABLE 'results'
		// Instantiating table descriptor class
		HTableDescriptor anotherTableDescriptor = new HTableDescriptor();
		anotherTableDescriptor.setName(Bytes.toBytes("results"));

		// Adding column families to table descriptor
		anotherTableDescriptor.addFamily(new HColumnDescriptor("pathTrace"));

		// Execute the table through admin
		if (!admin.tableExists(Bytes.toBytes("results"))) {
			admin.createTable(anotherTableDescriptor);
			System.out.println(" \'results\' Table created ");
		} else {
			System.out.println(" \'results\' Table already exists! Dropping and re-creating it...");
			admin.disableTable(Bytes.toBytes("results"));
			admin.deleteTable(Bytes.toBytes("results"));
			admin.createTable(anotherTableDescriptor);
		}

	}

	
	public static class PutFunction implements Function<String, Put> {

	    private static final long serialVersionUID = 1L;

	    public Put call(String v) throws Exception {
	      String[] part = v.split("&"); 					// <-- Breaking into rowKey and the JSON value
	      String rowKey = part[0].replace("\\", "").trim(); // <-- Chiseling rough edges!
	      String value = part[1];
	      Put p = new Put(Bytes.toBytes(rowKey));
	      p.addColumn(Bytes.toBytes("kstate"), Bytes.toBytes("json"), Bytes.toBytes(value));
	      return p;
	    }

	  }

}
