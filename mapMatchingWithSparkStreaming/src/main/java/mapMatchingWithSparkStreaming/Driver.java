package mapMatchingWithSparkStreaming;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

import com.bmwcarit.barefoot.matcher.MatcherFactory;
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

		// Create table if it doesn't exist
		Configuration hBaseConfiguration = HBaseConfiguration.create();
		initializeHBaseTable(hBaseConfiguration);

		// Broadcasting some utilities
		Broadcast<BroadcastedUtilities> broadcasted = ssc.sparkContext().broadcast(new BroadcastedUtilities());

		System.out.println("streaming");

		// Kafka streaming
		Map<String, String> kafkaParams = new HashMap<String, String>();
		kafkaParams.put("bootstrap.servers", "10.0.2.15:9092");
		Set<String> topic = Collections.singleton("gps");

		// Getting streams from Kafka
		JavaPairInputDStream<String, String> kafkaStreams = KafkaUtils.createDirectStream(ssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topic);
		JavaDStream<String> lines = kafkaStreams.map(Tuple2::_2);

		// Creating a pair Dstream with the ID as the key
		JavaPairDStream<String, String> linesInPairedFormWithID = lines.mapToPair(line -> {
					JSONObject json = new JSONObject(line);
					String deviceID = (String) json.get("id");
					return new Tuple2<>(deviceID, line);
				});

		// Grouping the Dstreams by the key
		JavaPairDStream<String, Iterable<String>> linesInPairedFormWithIDAndGroupedByID = linesInPairedFormWithID.groupByKey();
		linesInPairedFormWithIDAndGroupedByID.print();

		//
		JavaPairDStream<String, List<MatcherSample>> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherSamples = linesInPairedFormWithIDAndGroupedByID.mapValues(v -> {
					Iterator<String> iterator = v.iterator();
					List<MatcherSample> listOfSamples = new ArrayList<>();
					while (iterator.hasNext()) {
						listOfSamples.add(new MatcherSample(new JSONObject(
								iterator.next())));
					}
					return listOfSamples;
				});
		
		JavaPairDStream<String, MatcherKState> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState = linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherSamples
				.mapValues(v -> {
					System.out.println("id = " + v.get(0).id());
					System.out.println("oldKstateJSON = "
							+ broadcasted.getValue().getKstateJSONfromHBase(
									v.get(0).id()));
					String oldKstateJSON = broadcasted.getValue()
							.getKstateJSONfromHBase(v.get(0).id());
					if (oldKstateJSON == null)
						return broadcasted.getValue().getBarefootMatcher()
								.mmatch(v, 1, 150);
					else {
						MatcherKState state = new MatcherKState(new JSONObject(oldKstateJSON), new MatcherFactory(broadcasted.getValue().getRoadMap()));
						System.out.println("reconstructedJSON = "
								+ state.toJSON());

				// Unsorted samples (wrt time) leads to out-of-order RuntimeException.
				Collections.sort(v, (a, b) -> {
					Long aTime = new Long(a.time());
					Long bTime = new Long(b.time());
					return aTime.compareTo(bTime);
				});
				
				// Updating the existing state retrieved from HBase
				for (MatcherSample sample : v)
					state.update(broadcasted.getValue().getBarefootMatcher().execute(state.vector(), state.sample(), sample), sample);

				System.out.println("found state = " + state);
				return state;
			}
		});
		
		

		/*linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState
				.mapValues(
						x -> "Result = "
								+ x.sequence().get(0).point().geometry().getY()
								+ ", "
								+ x.sequence().get(0).point().geometry().getX())
				.print();

		JavaPairDStream<String, String> linesInPairedFormWithIDAndGroupedByIDAndValueMappedToKStateJSON = linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState
				.mapValues(v -> v.toJSON().toString());

		linesInPairedFormWithIDAndGroupedByIDAndValueMappedToKStateJSON
				.foreachRDD(rdd -> {
					rdd.foreach(v -> {
						println(v._1 + ", " + v._2);
						broadcasted.getValue().saveKstateJSONtoHBase(new String(v._1), new String(v._2));
					});
				});*/
		
		linesInPairedFormWithIDAndGroupedByIDAndValueMappedToMatcherKState.foreachRDD(rdd -> {
			rdd.foreach(value -> {
				println(value._1 + ", " + value._2.toJSON().toString());
				broadcasted.getValue().saveKstateJSONtoHBase(new String(value._1), value._2);
			});
		});

		ssc.start();
		ssc.awaitTermination();
	}

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
		} else
			System.out.println(" \'samples\'Table already exists!");

		
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
		} else
			System.out.println(" \'results\'Table already exists!");

	}

	private static void println(String s) {
		System.out.println("outout = " + s);
	}

}