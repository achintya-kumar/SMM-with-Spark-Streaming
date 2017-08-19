package mapMatchingWithSparkStreaming;

import java.io.IOException;
import java.io.Serializable;
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

import com.bmwcarit.barefoot.matcher.Matcher;
import com.bmwcarit.barefoot.roadmap.Loader;
import com.bmwcarit.barefoot.roadmap.Road;
import com.bmwcarit.barefoot.roadmap.RoadMap;
import com.bmwcarit.barefoot.roadmap.RoadPoint;
import com.bmwcarit.barefoot.roadmap.TimePriority;
import com.bmwcarit.barefoot.spatial.Geography;
import com.bmwcarit.barefoot.topology.Dijkstra;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 */

public class BroadcastUtility implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private RoadMap map;
	
	private Matcher matcher;
	
	Configuration hBaseConfiguration;
	
	Connection connection;
	
	Table table;
	
	public BroadcastUtility() {}
	
	public RoadMap getRoadMap() {
		synchronized (this) {
			if(map != null) {
				return map;
			} else {
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
	
	public Matcher getMatcher() {
		synchronized (this) {
			if(matcher != null) {
				return matcher;
			} else {
				matcher = new Matcher(getRoadMap(), new Dijkstra<Road, RoadPoint>(), new TimePriority(), new Geography());
				return matcher;
			}
		}
	}
	
	public Configuration getHbaseConfiguration() {
		synchronized (this) {
			if(hBaseConfiguration != null) {
				return hBaseConfiguration;
			} else {
				hBaseConfiguration = HBaseConfiguration.create();
				return hBaseConfiguration;
			}
		}
	}
	
	public Connection getConnection() throws IOException {
		synchronized (this) {
			if(connection != null) {
				return connection;
			} else {
				connection = ConnectionFactory.createConnection(getHbaseConfiguration());
				return connection;
			}
		}
	}
	
	public Table getTable() throws IOException{
		synchronized (this) {
			if(table != null) {
				return table;
			} else {
				table = getConnection().getTable(TableName.valueOf("samples"));
				return table;
			}
		}
	} 
	
	public boolean savePair(String key, String value) throws IOException {
		
		Put p = new Put(Bytes.toBytes(key)); 
		
		p.addColumn(Bytes.toBytes("kstate"), Bytes.toBytes("json"),Bytes.toBytes(value));
		
		getTable().put(p);
		
		return true;
	}
	
	public String getKstateJSON(String key) throws IOException {
		
		// Instantiating Get class
	    Get g = new Get(Bytes.toBytes(key));

	    // Reading the data
	    Result result = getTable().get(g);

	    // Reading values from Result class object
	    byte [] value = result.getValue(Bytes.toBytes("kstate"),Bytes.toBytes("json"));

	    // Printing the values
	    String json = Bytes.toString(value);
	      
	    System.out.println("name: " + json);
	    
	    return json;
	}
	

}
