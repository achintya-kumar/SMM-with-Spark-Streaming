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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkEnv;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.JSONObject;

import response.Trace;
import scala.Tuple2;

import com.bmwcarit.barefoot.matcher.MatcherCandidate;
import com.bmwcarit.barefoot.matcher.MatcherFactory;
import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.matcher.MatcherSample;
import com.bmwcarit.barefoot.road.BfmapReader;
import com.bmwcarit.barefoot.roadmap.RoadMap;
import com.google.common.base.Optional;
import com.google.gson.Gson;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class Driver {

	public static void main(String[] args) throws Exception {
		Logger log = org.apache.log4j.LogManager.getRootLogger();
		log.setLevel(Level.INFO);
		
		// Initializing SparkConf with at least 2 threads and StreamingContext with a batch interval of 5 seconds or more
		System.out.println("Local execution is DEACTIVATED!");
		SparkConf conf = new SparkConf().setAppName("spark_kafka").setMaster("local[*]");
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.milliseconds(6000));
		ssc.checkpoint("/tmp");

		// Create table if it doesn't exist
		Configuration hBaseConfiguration = new HBaseConfiguration();
		hBaseConfiguration.clear();
		//hBaseConfiguration.addResource("/etc/hbase/conf/core-site.xml");
		//hBaseConfiguration.addResource("/etc/hbase/conf/hbase-site.xml");
		hBaseConfiguration.set("hbase.zookeeper.quorum", "localhost");
		hBaseConfiguration.set("hbase.zookeeper.property.clientPort","2181");
		initializeHBaseTable(hBaseConfiguration);

		JavaHBaseContext hbaseContext = new JavaHBaseContext(sc, hBaseConfiguration);
		// Broadcasting some utilities
		RoadMap map = RoadMap.Load(new BfmapReader("./oberbayern.bfmap"));
		System.out.println("Map has been loaded! Index construction will be done lazily within broadcastVariable.");
		Broadcast<BroadcastedUtilities> broadcasted = ssc.sparkContext().broadcast(new BroadcastedUtilities(map));
		

		// Kafka streaming
		Map<String, String> kafkaParams = new HashMap<String, String>();
		kafkaParams.put("bootstrap.servers", "localhost:9092");
		kafkaParams.put("group.id", "map_group");
		kafkaParams.put("enable.auto.commit", "true");
		Set<String> topic = Collections.singleton("gps");

		// Getting streams from Kafka
		JavaPairInputDStream<String, String> kafkaStreams = KafkaUtils.createDirectStream(ssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topic);
		JavaDStream<String> lines = kafkaStreams.map(Tuple2::_2)/*ssc.socketTextStream("localhost", 1111) /*KEPT FOR TESTING PURPOSES!*/;

		// Creating a pair Dstream with the ID as the key
		JavaPairDStream<String, String> linesInPairedFormWithID = lines.mapToPair(line -> {
					JSONObject json = new JSONObject(line);
					String deviceID = (String) json.get("id");
					return new Tuple2<>(deviceID, line);
				});
		
		// Converting the String values to List<String> values, for easier reduction in the subsequent step.
		JavaPairDStream<String, List<String>> linesInPairedFormWithIDinListForm = linesInPairedFormWithID.mapValues(v -> {
			List<String> inListForm = new ArrayList<>();
			inListForm.add(v);
			return inListForm;
		});
		
		// Initially, groupByKey seemed easier to go with. But after realizing high costs of shuffling associated with it,
		// we have used reduceByKey to achieve the same functionality. We wish to have all the samples from the same device
		// on the same node. ReduceByKey brings them together on a single machine with less shuffling operations.
		JavaPairDStream<String, List<String>> linesInPairedFormWithIDAndGroupedByID = linesInPairedFormWithIDinListForm.reduceByKey((a, b) -> {
			a.addAll(b);
			return a;
		});
		
		// Stateful transformation begins here
		// This is where the heart of the implementation lies. The previous design which had to retrieve old KState JSON from
		// 'samples' HBase table was proving to be a bottleneck. Additionally, the problem is related to state-updation.
		// Inspired by Stateful Network Wordcount, the application now performs the state-updation of KState by sharing results
		// across batches and thereby the dependence on HBase per batch-interval is eliminated.
		JavaPairDStream<String, Tuple2<String, Long>> deviceIdPairedWithKstate = linesInPairedFormWithIDAndGroupedByID.updateStateByKey((samples, currentKState) -> {
			
			// If a device has been inactive in terms of sample-transmission for more than 5 minutes,
			// it should be dropped from the state-update loop. Otherwise, the length of items to process
			// will keep growing indefinitely.
			if(samples.isEmpty()) {
				System.out.println("EMPTY SAMPLES FOUND!");
				long inactivityTolerance = 5 * 60 * 1000; // <-- Must be in milliseconds. Currently at 5 minutes.
				if(System.currentTimeMillis() - currentKState.get()._2() >= inactivityTolerance) {
					System.out.println("IT'S BEEN MORE THAN 5 MINUTES!");
					return Optional.absent(); // <-- Dropping it from state-update loop!
				}
				return Optional.of(new Tuple2<>(currentKState.get()._1(), currentKState.get()._2()));
			}
			
			// The updateStateByKey API reduces all the values for a key into a list.
			// Since the values are already reduced, the API will produce a list with just one item.
			Iterator<String> iterator = samples.get(0).iterator();
			List<MatcherSample> matcherSamples = new ArrayList<>();
			while (iterator.hasNext()) {
				matcherSamples.add(new MatcherSample(new JSONObject(iterator.next())));
			}
			
			// Unsorted samples lead to runtime exceptions
			Collections.sort(matcherSamples, (a, b) -> {
				Long aTime = new Long(a.time());
				Long bTime = new Long(b.time());
				return aTime.compareTo(bTime);
			});
			
			// If the samples are present, then it maybe from a known device or a completely new device.
			if(!currentKState.isPresent()) { // <-- Never seen before
				String kStateJSON = broadcasted.getValue().getBarefootMatcher().mmatch(matcherSamples, 1, 150).toJSON().toString();
				return Optional.of(new Tuple2<>(kStateJSON, System.currentTimeMillis()));  // <-- Tagging when it was last seen
			} else { // <-- Seen before, hence updating existing kStateJSON
				System.out.println("OLD STATE JSON = " + currentKState.get()._1());
				MatcherKState state = new MatcherKState(new JSONObject(/*TO CHECK*/currentKState.get()._1()), new MatcherFactory(broadcasted.getValue().getRoadMap()));
				for (MatcherSample sample : matcherSamples)
					state.update(broadcasted.getValue().getBarefootMatcher().execute(state.vector(), state.sample(), sample), sample);
				
				return Optional.of(new Tuple2<>(state.toJSON().toString(), System.currentTimeMillis()));  // <-- Tagging when it was last seen
			}
		});
				
		
		// HBaseContext works with DStreams and not with PairDStreams, apparently. Hardly a problem though.	
		// The following two transformations will save to 2 different tables, one is for storage of KState JSON,
		// which gets used in tracking. The other transformation is for saving the path-trace in a human-friendly 
		// JSON format. The following are the transformations to HBase table mappings:
		// KStateJSON -> samples
		// pathTrace  -> results
		JavaDStream<Tuple2<String, String>> javaDstreamForKState = deviceIdPairedWithKstate.map(v -> new Tuple2<>(v._1, v._2._1)).persist(StorageLevel.MEMORY_AND_DISK());
		
		JavaDStream<Tuple2<String, String>> javaDstreamForPathTrace = javaDstreamForKState.map(v -> {

			// Removing funny characters(e.g. \) from the key
			String key = v._1.replace("\\", "").trim();
			
			// Initializing a new path trace object
			Trace trace = new Trace();
			trace.setDeviceID(key);
			
			List<Long> timestamps = new ArrayList<>();
			MatcherKState kState = new MatcherKState(new JSONObject(v._2), new MatcherFactory(broadcasted.getValue().getRoadMap()));
			
			kState.samples().forEach(s -> timestamps.add(s.time())); // <-- Extracting timestamps from kState, to place inside the pathTrace
			
			Iterator<Long> iterator = timestamps.iterator(); // <-- To iterate over the timestamps extracted above!
			
			for(MatcherCandidate cand : kState.sequence()) {
				trace.addCoordinates(iterator.next(), cand.point().geometry().getY(), cand.point().geometry().getX());
			}
			
			//Gson to json-ize the trace for storage
			Gson gson = new Gson();
			
			return new Tuple2<>(key, gson.toJson(trace));
			
		});
		javaDstreamForKState.print();
		hbaseContext.streamBulkPut(javaDstreamForKState, TableName.valueOf("samples"), new PutFunctionForKState());
		hbaseContext.streamBulkPut(javaDstreamForPathTrace, TableName.valueOf("results"), new PutFunctionForPathTrace());
		
		
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

	
	/**
	* The following Function classes are used to convert DStreams into Put objects for
	* bulk-storing into HBase tables.
	*/
	
	public static class PutFunctionForKState implements Function<Tuple2<String, String>, Put> {

	    private static final long serialVersionUID = 1L;

	    public Put call(Tuple2<String, String> v) throws Exception {
	      String rowKey = v._1.replace("\\", "").trim(); // <-- Chiseling rough edges!
	      String value = v._2;
	      Put p = new Put(Bytes.toBytes(rowKey));
	      p.addColumn(Bytes.toBytes("kstate"), Bytes.toBytes("json"), Bytes.toBytes(value));
	      return p;
	    }

	  }

	public static class PutFunctionForPathTrace implements Function<Tuple2<String, String>, Put> {

	    private static final long serialVersionUID = 1L;

	    public Put call(Tuple2<String, String> v) throws Exception {
	      String rowKey = v._1.replace("\\", "").trim(); // <-- Chiseling rough edges!
	      String value = v._2;
	      Put p = new Put(Bytes.toBytes(rowKey));
	      p.addColumn(Bytes.toBytes("pathTrace"), Bytes.toBytes("json"), Bytes.toBytes(value));
	      return p;
	    }

	  }
	
}
