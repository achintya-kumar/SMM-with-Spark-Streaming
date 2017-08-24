package simpleClient;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Client {
	private static String dataStoreAddress = "http://localhost:20550";
	
	public static void sendGet(String rowKey, String dataStoreLink) throws Exception {
		System.out.println("Sending HTTP GET request");
		if(dataStoreLink != null)
			dataStoreAddress = dataStoreLink;
		
		
        String url = dataStoreAddress + "/samples/" + rowKey;

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
        System.out.println(receivedRowKey.equals("x0001")?"Test Passed":"Test Failed");
    }
	
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
	
	public static void main(String[] args) throws Exception {

        Thread stream = new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("\n\n");
				System.out.println("from thread!");
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
						Thread.sleep(3000);
						System.out.println(array.get(i));
						sentPost(array.get(i).toString(), "http://10.0.2.15:8082/topics/gps");
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
        //stream.start();
        sendGet("x0001", null);

    }

}
