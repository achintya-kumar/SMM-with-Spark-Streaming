package simpleClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONObject;

public class Client {
	private static final String USER_AGENT = "Mozilla/5.0";
	
	private static void sendGet(String rowKey) throws Exception {

        String url = "http://localhost:20550/samples/" + rowKey;

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
        System.out.println(receivedRowKey);
        String receivedValue = new String(Base64.getDecoder().decode(json.getJSONArray("Row").getJSONObject(0).getJSONArray("Cell").getJSONObject(0).get("$").toString()), StandardCharsets.UTF_8);
        System.out.println(receivedValue);
    }
	
	public static void main(String[] args) throws Exception {

        System.out.println("Sending HTTP GET request");
        sendGet("achintya");

    }

}
