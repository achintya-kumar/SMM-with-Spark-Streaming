package mapMatchingWithSparkStreaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.apache.spark.api.java.function.Function3;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.State;
import org.apache.spark.streaming.StateSpec;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaMapWithStateDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import response.Trace;
import scala.Tuple2;

import com.bmwcarit.barefoot.matcher.Matcher;
import com.bmwcarit.barefoot.matcher.MatcherCandidate;
import com.bmwcarit.barefoot.matcher.MatcherFactory;
import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.matcher.MatcherSample;
import com.bmwcarit.barefoot.road.BfmapReader;
import com.bmwcarit.barefoot.roadmap.Road;
import com.bmwcarit.barefoot.roadmap.RoadMap;
import com.bmwcarit.barefoot.roadmap.RoadPoint;
import com.bmwcarit.barefoot.roadmap.TimePriority;
import com.bmwcarit.barefoot.spatial.Geography;
import com.bmwcarit.barefoot.topology.Dijkstra;
import com.google.common.base.Optional;
import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.io.PrintWriter;
/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class Driver {

	public static void main(String[] args) throws Exception {
		Logger log = org.apache.log4j.LogManager.getRootLogger();
		log.setLevel(Level.OFF); // <-- Available options: INFO, DEBUG, ERROR
		
		// Initializing SparkConf with at least 2 threads and StreamingContext with a batch interval of 5 seconds or more
		System.out.println("Local execution is DEACTIVATED!");
		SparkConf conf = new SparkConf().setAppName("spark_kafka")/*.setMaster("local[*]")*/;
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.milliseconds(20000));
		ssc.checkpoint("/tmp");

		// Create table if it doesn't exist
		Configuration hBaseConfiguration = new HBaseConfiguration();
		hBaseConfiguration.clear();
		//hBaseConfiguration.addResource("/etc/hbase/conf/core-site.xml");
		//hBaseConfiguration.addResource("/etc/hbase/conf/hbase-site.xml");
		hBaseConfiguration.set("hbase.zookeeper.quorum", "lemaster.ovgu.de");
		hBaseConfiguration.set("hbase.zookeeper.property.clientPort","2181");
		initializeHBaseTable(hBaseConfiguration);

		JavaHBaseContext hbaseContext = new JavaHBaseContext(sc, hBaseConfiguration);
		System.out.println("Please make sure map servers are up and running on every worker...");
		

		// Kafka streaming
		Map<String, String> kafkaParams = new HashMap<String, String>();
		kafkaParams.put("bootstrap.servers", "lemaster.ovgu.de:9092,leslave1.ovgu.de:9092,leslave2.ovgu.de:9092,leslave3.ovgu.de:9092,leslave4.ovgu.de:9092,leslave5.ovgu.de:9092");
		kafkaParams.put("group.id", "map_group");
		kafkaParams.put("enable.auto.commit", "true");
		Set<String> topic = Collections.singleton("map-topic");

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
		Function3<String, Optional<List<String>>, State<String>, Tuple2<String, String>> mappingFunc =
		    (deviceId, samples, state) -> {
		    	//System.out.println("  COMING FROM: " + SparkEnv.get().executorId() + "\n  Processing DeviceId = " + deviceId);
		    	if(!samples.isPresent())
		    		return new Tuple2<>(deviceId, state.get());
		    	
		    	
		    	Iterator<String> iterator = samples.get().iterator();
				List<MatcherSample> matcherSamples = new ArrayList<>();
				while (iterator.hasNext()) {
					matcherSamples.add(new MatcherSample(new JSONObject(iterator.next())));
				}
				
				Collections.sort(matcherSamples, (a, b) -> {
					Long aTime = new Long(a.time());
					Long bTime = new Long(b.time());
					return aTime.compareTo(bTime);
				});
			
			//Activate the block below for fractional processing
			    /* List<String> underSampled = new ArrayList<>();
			    for(int i = 0; i < samples.get().size()-2; i+=5)
				underSampled.add(samples.get().get(i));
					
			    underSampled.add(samples.get().get(samples.get().size() - 1));*/


			    JSONObject json = new JSONObject();
				json.put("format", "geojson");
				json.put("request", new JSONArray(samples.get().toString())); // <-- Replace samples.get() by underSampled for fractional processing
				if(state.exists()) {
					//System.out.println("     STATE EXISTS!!");
					json.put("oldState", state.get());
				}
				
				String serverName = "localhost";
				int port = 1234;
		
				try {
			         //System.out.println("Connecting to " + serverName + " on port " + port);
			         Socket client = new Socket(serverName, port);
			         InputStream inFromServer = client.getInputStream();
			         BufferedReader br = new BufferedReader(new InputStreamReader(inFromServer, "UTF-8"));
			         
			         //System.out.println("Just connected to " + client.getRemoteSocketAddress());
			         //OutputStream outToServer = client.getOutputStream();
			         //DataOutputStream out = new DataOutputStream(outToServer);
			         PrintWriter output = new PrintWriter(client.getOutputStream());
			         output.print(json.toString());
			         output.flush();
			         //output.close();
			         client.shutdownOutput();
			         String line, result="";
			         while( (line = br.readLine()) != null )
			        	 result += line;
			         
			         //System.out.println("Server says " + result);
			         state.update(result);
			         client.close();
			         return new Tuple2<>(deviceId, result);
			      }catch(IOException e) {
			         e.printStackTrace();
			      }
				
				return new Tuple2<>(deviceId, state.get());
		    };
			
			JavaMapWithStateDStream<String, List<String>, String, Tuple2<String, String>> stateDstream =
			linesInPairedFormWithIDAndGroupedByID.mapWithState(StateSpec.function(mappingFunc).timeout(Durations.seconds(20)));
				
		
		// HBaseContext works with DStreams and not with PairDStreams, apparently. Hardly a problem though.		
		JavaDStream<Tuple2<String, String>> javaDstreamForKState = stateDstream.map(state -> new Tuple2<>(state._1, state._2));
		hbaseContext.streamBulkPut(javaDstreamForKState, TableName.valueOf("samples"), new PutFunctionForKState());
		
		
		ssc.start();
		ssc.awaitTermination();
		ssc.close();
	}

	/**
	 * Initializing empty table upon application restart is necessary as computation on samples is sensitive to time information.
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

	}

	
	/**
	* The following Function classes are used to convert DStreams into Put objects for
	* bulk-storing into HBase table.
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
	
}
