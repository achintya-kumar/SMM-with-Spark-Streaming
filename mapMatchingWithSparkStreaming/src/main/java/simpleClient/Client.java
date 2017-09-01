package simpleClient;

import java.awt.Frame;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;

import javax.swing.JOptionPane;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import response.Trace;

import com.google.gson.Gson;

import java.util.Date;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 * 
 * This class serves as a mock-client and is capable of sending data to the
 * Kafka server and also requesting map-matched results, both in JSON.
 *
 */

public class Client {
	
	private static String dataStoreAddress = "http://localhost:20550";
	
	/**
	 * Fetches results from the HBase 'results' table
	 * @param rowKey ID of the device
	 * @param dataStoreLink Link to the HBase master
	 * @throws Exception
	 */
	public static void sendGet(String rowKey, String dataStoreLink) throws Exception {
		System.out.println("Sending HTTP GET request");
		if(dataStoreLink != null)
			dataStoreAddress = dataStoreLink;
		
		
        String url = dataStoreAddress + "/results/" + rowKey;

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //Request header
        con.setRequestProperty("Accept", "application/json");

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        System.out.println(response.toString());
        JSONObject json = new JSONObject(response.toString());
        String receivedRowKey = new String(Base64.getDecoder().decode(json.getJSONArray("Row").getJSONObject(0).get("key").toString()), StandardCharsets.UTF_8);
        //System.out.println(receivedRowKey);
        String receivedValue = new String(Base64.getDecoder().decode(json.getJSONArray("Row").getJSONObject(0).getJSONArray("Cell").getJSONObject(0).get("$").toString()), StandardCharsets.UTF_8);
        System.out.println(receivedValue);
        
        Gson gson = new Gson();
        
        System.out.println("Displaying timestamps...");
        Trace trace = gson.fromJson(receivedValue, Trace.class);
        trace.getCoordinates().forEach(c -> {
        	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX");
        	try {
				System.out.println(simpleDateFormat.format(new Date(c.getTimestamp())));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        });
        
    }
	
	/**
	 * Posts GPS sample data to the Kafka server
	 * @param requestJson GPS sample in prescribed JSON format
	 * @param dataStoreLink Link to Kafka server
	 * @throws IOException
	 */
	public static void sentPost(String requestJson, String dataStoreLink) throws IOException {
		//"curl -X POST -H \"Content-Type: application/vnd.kafka.json.v1+json\" --data '{\"records\":[{\"value\": %s}]}' 'http://10.0.2.15:8082/topics/gps'" % (json.dumps(sample))
		
		if(dataStoreLink != null)
			dataStoreAddress = dataStoreLink;
		
		
        String url = dataStoreAddress;
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		 
		// Setting basic post request
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type","application/vnd.kafka.json.v1+json");
		 
		String postJsonData = "{\"records\":[{\"value\":" + requestJson + "}]}";
		  
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(postJsonData);
		wr.flush();
		wr.close();
		 
		int responseCode = con.getResponseCode();
		System.out.println("nSending 'POST' request to URL : " + url);
		System.out.println("Post Data : " + postJsonData);
		System.out.println("Response Code : " + responseCode);
		 
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String output;
		StringBuffer response = new StringBuffer();
		 
		while ((output = in.readLine()) != null) {
			response.append(output);
		}
		in.close();
		  
		//printing result from response
		System.out.println(response.toString());
	}
	
	public static void feedKafka() throws Exception {
		//Delegating the job of streaming a sample every 3 seconds to a separate thread.
        Thread stream = new Thread(new Runnable() {
			@Override
			public void run() {
				String completeJSON = new String();
				BufferedReader bufferedReader; 
				try {
					bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("/home/cloudera/barefoot/src/test/resources/com/bmwcarit/barefoot/matcher/x0001-001.json")));
					if(bufferedReader != null) {
						String line;
						while((line = bufferedReader.readLine()) != null) {
							completeJSON += (line);
						}
					}
					bufferedReader.close();
					JSONArray array = new JSONArray(completeJSON);
					for(int i = 0; i < array.length(); i++) {
						Thread.sleep(3000); // <-- Sends out a sample every 3 seconds
						System.out.println(array.get(i));
						sentPost(array.get(i).toString(), "http://localhost:8082/topics/gps"); // <-- Sending POST request to Kafka server
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
					
			}
		});
        
        int choice = JOptionPane.showConfirmDialog(new Frame(), "Are you sure you want to stream?", "Confirm Streaming...", JOptionPane.YES_NO_OPTION);
        if(choice == JOptionPane.YES_OPTION)
        	stream.start();
        else
        	System.exit(0);
        
        //Thread.sleep(30000);
        //sendGet("x0001", null);
	}
	
	public static void main(String[] args) throws Exception {
		sendGet("x0001", null);
	}

}
