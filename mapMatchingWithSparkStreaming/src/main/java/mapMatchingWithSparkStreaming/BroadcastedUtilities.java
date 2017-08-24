package mapMatchingWithSparkStreaming;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.json.JSONException;
import org.json.JSONObject;

import response.Trace;

import com.bmwcarit.barefoot.matcher.Matcher;
import com.bmwcarit.barefoot.matcher.MatcherCandidate;
import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.matcher.MatcherSample;
import com.bmwcarit.barefoot.roadmap.Loader;
import com.bmwcarit.barefoot.roadmap.Road;
import com.bmwcarit.barefoot.roadmap.RoadMap;
import com.bmwcarit.barefoot.roadmap.RoadPoint;
import com.bmwcarit.barefoot.roadmap.TimePriority;
import com.bmwcarit.barefoot.spatial.Geography;
import com.bmwcarit.barefoot.topology.Dijkstra;
import com.bmwcarit.barefoot.util.SourceException;
import com.google.gson.Gson;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class BroadcastedUtilities implements Serializable {

	private static final long serialVersionUID = 1L;

	// Initialization to take place on respective worker nodes' JVMs
	private RoadMap map;

	private Matcher matcher;

	private Configuration hBaseConfiguration;

	private Connection connection;

	private Table table;

	public BroadcastedUtilities() {
	}

	public RoadMap getRoadMap() {
		synchronized (this) {
			if (map != null) {
				return map;
			} else {
				// These hardcoded attributes shall later be supplied through
				// command-line arguments
				Properties oberbayern_properties = new Properties();
				oberbayern_properties.put("database.host", "172.17.0.1");
				oberbayern_properties.put("database.port", "5432");
				oberbayern_properties.put("database.name", "oberbayern");
				oberbayern_properties.put("database.table", "bfmap_ways");
				oberbayern_properties.put("database.user", "osmuser");
				oberbayern_properties.put("database.password", "pass");

				map = Loader.roadmap(oberbayern_properties, true).construct();
				return map;
			}
		}
	}

	public Matcher getBarefootMatcher() {
		synchronized (this) {
			if (matcher != null) {
				return matcher;
			} else {
				matcher = new Matcher(getRoadMap(),
						new Dijkstra<Road, RoadPoint>(), new TimePriority(),
						new Geography());
				return matcher;
			}
		}
	}

	public Configuration getHbaseConfiguration() {
		synchronized (this) {
			if (hBaseConfiguration != null) {
				return hBaseConfiguration;
			} else {
				hBaseConfiguration = HBaseConfiguration.create();
				return hBaseConfiguration;
			}
		}
	}

	public Connection getConnection() throws IOException {
		synchronized (this) {
			if (connection != null) {
				return connection;
			} else {
				connection = ConnectionFactory
						.createConnection(getHbaseConfiguration());
				return connection;
			}
		}
	}

	public Table getHBaseTable(String tableName) throws IOException {
		synchronized (this) {
				table = getConnection().getTable(TableName.valueOf(tableName));
				return table;
		}
	}

	public boolean saveKstateJSONtoHBase(String key, MatcherKState kState) {

		// Removing funny characters(e.g. \) from the key
		key = key.replace("\\", "");

		// Instantiating Put class
		Put p = new Put(Bytes.toBytes(key));

		// Adding value to save to correct HBase coordinates
		try {
			p.addColumn(Bytes.toBytes("kstate"), Bytes.toBytes("json"), Bytes.toBytes(kState.toJSON().toString()));
		} catch (JSONException e1) {
			e1.printStackTrace();
			return false;
		}

		// Saving the value
		try {
			getHBaseTable("samples").put(p); // <-- "samples" because this table is used to store kState JSONs
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		//Now that kState JSON is in HBase, let's also store Trace object for client consumption
		savePathTraceToHBase(key, kState);

		return true;
	}

	public boolean savePathTraceToHBase(String key, MatcherKState kState) {
		
		// Removing funny characters(e.g. \) from the key
		key = key.replace("\\", "");
		
		Trace trace = new Trace();
		trace.setDeviceID(key);
		
		for(MatcherCandidate cand : kState.sequence()) {
			trace.addCoordinates(cand.point().geometry().getY(), cand.point().geometry().getX());
		}
		
		//Gson to json-ize the trace for storage
		Gson gson = new Gson();
		
		// Instantiating Put class
		Put p = new Put(Bytes.toBytes(key));

		// Adding value to save to correct HBase coordinates
		p.addColumn(Bytes.toBytes("pathTrace"), Bytes.toBytes("json"), Bytes.toBytes(gson.toJson(trace)));

		// Saving the value
		try {
			getHBaseTable("results").put(p); // <-- The results can be pulled from here.
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public String getKstateJSONfromHBase(String key) throws IOException {

		// Removing funny characters(e.g. \) from the key
		key = key.replace("\\", "");

		// Instantiating Get class
		Get g = new Get(Bytes.toBytes(key));

		// Reading the data
		Result result = getHBaseTable("samples").get(g);

		// Reading values from Result class object
		byte[] value = result.getValue(Bytes.toBytes("kstate"),
				Bytes.toBytes("json"));

		// Converting the value to String
		String json = Bytes.toString(value);

		return json;
	}
	
//	public static void main(String[] args) throws JSONException, SourceException, IOException {
//		System.out.println("starting...");
//		Properties oberbayern_properties = new Properties();
//		oberbayern_properties.put("database.host", "172.17.0.1");
//		oberbayern_properties.put("database.port", "5432");
//		oberbayern_properties.put("database.name", "oberbayern");
//		oberbayern_properties.put("database.table", "bfmap_ways");
//		oberbayern_properties.put("database.user", "osmuser");
//		oberbayern_properties.put("database.password", "pass");
//		
//		
//		RoadMap map = Loader.roadmap(oberbayern_properties, true).construct();
//		System.out.println("Map created at = " + System.currentTimeMillis());
//		
//
//		// Instantiate matcher and state data structure
//		Matcher matcher = new Matcher(map, new Dijkstra<Road, RoadPoint>(), new TimePriority(), new Geography());
//		System.out.println("Matcher created as = " + System.currentTimeMillis());
//		
//		/*Performing map-matching for the following position samples
//		 * 1. {"point":"POINT(11.564388282625075 48.16350662940509)","time":"2014-09-10 06:54:07+0200","id":"\\x0001"},
//		 * 2. {"point":"POINT(11.563678490482323 48.16198390379898)"
//		 * 		,"time":"2014-09-10 06:54:22+0200","id":"\\x0001"},
//		 * 3. {"point":"POINT(11.563473064247667 48.16122306758928)"
//		 * 		,"time":"2014-09-10 06:54:37+0200","id":"\\x0001"}
//		 */
//		
//		
//		// Input as sample batch (offline) or sample stream (online)
//		List<MatcherSample> samples = new ArrayList<>();
//		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.564388282625075 48.16350662940509)\" ,\"time\":\"2014-09-10 06:54:07+0200\",\"id\":\"\\x0001\"}")));
//		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.563678490482323 48.16198390379898)\" ,\"time\":\"2014-09-10 06:54:22+0200\",\"id\":\"\\x0001\"}")));
//		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.563473064247667 48.16122306758928)\" ,\"time\":\"2014-09-10 06:54:37+0200\",\"id\":\"\\x0001\"}")));
//		System.out.println("Samples initialized = " + System.currentTimeMillis());
//		
//
//		// Match full sequence of samples
//		MatcherKState state = matcher.mmatch(samples, 1, 500);
//		System.out.println("Matching done at = " + System.currentTimeMillis());
//		
//		// Access map matching result: sequence for all samples
//		for (MatcherCandidate cand : state.sequence()) {
//			System.out.println("GPS position = " + cand.point().geometry().getY() + ", " + cand.point().geometry().getX() + ", at time = ");; // GPS position (on the road)
//		}
//		
//		System.out.println(state.toJSON());
//	}

}
