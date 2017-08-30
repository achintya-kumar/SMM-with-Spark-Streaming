package tracker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.swing.JOptionPane;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;String to port the Monitor Server subscribes to

import com.bmwcarit.barefoot.matcher.MatcherFactory;
import com.bmwcarit.barefoot.matcher.MatcherKState;
import com.bmwcarit.barefoot.roadmap.Loader;
import com.bmwcarit.barefoot.roadmap.RoadMap;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 * 
 * This class serves as a server that retrieves KState JSON from HBase to create
 * a KState object and sends it to Monitor Server to plot it on Open Street Map  
 * 
 */

public class Tracker {
	
	private static String dataStoreAddress, trackerIP;
	private static RoadMap map;
	private static int trackerPort;
	
	/**
	 * Fetches results from the HBase 'samples' table and publish JSON data via ZMQ
	 * @param rowKey ID of the device
	 * @param dataStoreLink Link to the HBase master
	 * @param trackerIP IP address where the monitor server is running
	 * @param trackerPort tcp port to publish JSON data for monitor server
	 * @throws Exception
	 */
	
	public static void publishToMonitorServer(String rowKey, String dataStoreLink) throws Exception {
		
		dataStoreAddress = "httpString to port the Monitor Server subscribes to://192.168.0.102:20550";
		trackerIP = "127.0.0.1";
		trackerPort = 1235;
		
		
		if(dataStoreLink != null)
			dataStoreAddress = dataStoreLink;
		
		String url = dataStoreAddress + "/samples/" + rowKey;
		
		//HBase connection 
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //Request header
        con.setRequestProperty("Accept", "application/json");
		
		//Properties to create a RoadMap instance 
		Properties oberbayern_properties = new Properties();
		oberbayern_properties.put("database.host", "172.17.0.1");
		oberbayern_properties.put("database.port", "5432");
		oberbayern_properties.put("database.name", "oberbayern");
		oberbayern_properties.put("database.table", "bfmap_ways");
		oberbayern_properties.put("database.user", "osmuser");
		oberbayern_properties.put("database.password", "pass");

		map = Loader.roadmap(oberbayern_properties, true).construct();
		
		//Queue to publish JSON data for Monitor Server
		BlockingQueue<String> queue = new LinkedBlockingDeque<>();
	    Context context = ZMQ.context(1);
	    Socket socket = context.socket(ZMQ.PUB);
	    socket.bind("tcp://" + trackerIP + ":" + trackerPort);
	    
	    //Constructing the result from HBase table  
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);	
        }
        in.close();
        
        //Converting Base64 binary data to String
        JSONObject json = new JSONObject(response.toString());
        String receivedValue = new String(Base64.getDecoder().decode(json.getJSONArray("Row").getJSONObject(0).getJSONArray("Cell").getJSONObject(0).get("$").toString()), StandardCharsets.UTF_8);
        
        //Creating a MatcherKState object from received data 
        MatcherKState state = new MatcherKState(new JSONObject(receivedValue), new MatcherFactory(map));
        JSONObject monitorJson;
    	
        //Publishing JSON data
    	monitorJson = state.toMonitorJSON();
    	monitorJson.put("id", rowKey);
        queue.put(monitorJson.toString());
        String message = queue.take();
        socket.send(message);
      	socket.close();
      	context.term();
    }
	
	public static void main(String[] args) throws Exception {
        
		publishToMonitorServer("x0001", null);

    }

}
