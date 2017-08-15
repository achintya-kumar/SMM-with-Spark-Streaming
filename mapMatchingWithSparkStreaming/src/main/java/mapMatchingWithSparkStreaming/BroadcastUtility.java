package mapMatchingWithSparkStreaming;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.security.auth.login.Configuration;

import org.json.JSONException;
import org.json.JSONObject;

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

public class BroadcastUtility implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Matcher matcher;
	
	public BroadcastUtility() {}
	
	public Matcher getMatcher() {
		synchronized (this) {
			if(matcher != null) {
				return matcher;
			} else {
				RoadMap map;
				try {
					Properties oberbayern_properties = new Properties();
					oberbayern_properties.put("database.host", "172.17.0.1");
					oberbayern_properties.put("database.port", "5432");
					oberbayern_properties.put("database.name", "oberbayern");
					oberbayern_properties.put("database.table", "bfmap_ways");
					oberbayern_properties.put("database.user", "osmuser");
					oberbayern_properties.put("database.password", "pass");
					
					map = Loader.roadmap(oberbayern_properties, true).construct();
					matcher = new Matcher(map, new Dijkstra<Road, RoadPoint>(), new TimePriority(), new Geography());
					return matcher;
				} catch (SourceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		return null;
	}
	
	public static void main(String[] args) throws JSONException, SourceException, IOException {
		System.out.println("starting...");
		Properties oberbayern_properties = new Properties();
		oberbayern_properties.put("database.host", "172.17.0.1");
		oberbayern_properties.put("database.port", "5432");
		oberbayern_properties.put("database.name", "oberbayern");
		oberbayern_properties.put("database.table", "bfmap_ways");
		oberbayern_properties.put("database.user", "osmuser");
		oberbayern_properties.put("database.password", "pass");
		
		
		RoadMap map = Loader.roadmap(oberbayern_properties, true).construct();
		System.out.println("Map created at = " + System.currentTimeMillis());
		

		// Instantiate matcher and state data structure
		Matcher matcher = new Matcher(map, new Dijkstra<Road, RoadPoint>(), new TimePriority(), new Geography());
		System.out.println("Matcher created as = " + System.currentTimeMillis());
		
		/*Performing map-matching for the following position samples
		 * 1. {"point":"POINT(11.564388282625075 48.16350662940509)"
		 * 		,"time":"2014-09-10 06:54:07+0200","id":"\\x0001"},
		 * 2. {"point":"POINT(11.563678490482323 48.16198390379898)"
		 * 		,"time":"2014-09-10 06:54:22+0200","id":"\\x0001"},
		 * 3. {"point":"POINT(11.563473064247667 48.16122306758928)"
		 * 		,"time":"2014-09-10 06:54:37+0200","id":"\\x0001"}
		 */
		
		
		// Input as sample batch (offline) or sample stream (online)
		List<MatcherSample> samples = new ArrayList<>();
		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.564388282625075 48.16350662940509)\" ,\"time\":\"2014-09-10 06:54:07+0200\",\"id\":\"\\x0001\"}")));
		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.563678490482323 48.16198390379898)\" ,\"time\":\"2014-09-10 06:54:22+0200\",\"id\":\"\\x0001\"}")));
		samples.add(new MatcherSample(new JSONObject("{\"point\":\"POINT(11.563473064247667 48.16122306758928)\" ,\"time\":\"2014-09-10 06:54:37+0200\",\"id\":\"\\x0001\"}")));
		System.out.println("Samples initialized = " + System.currentTimeMillis());
		

		// Match full sequence of samples
		MatcherKState state = matcher.mmatch(samples, 1, 500);
		System.out.println("Matching done at = " + System.currentTimeMillis());
		
		// Access map matching result: sequence for all samples
		for (MatcherCandidate cand : 
			state.sequence()) {
			System.out.println("GPS position = " + cand.point().geometry().getY() + ", " + cand.point().geometry().getX());; // GPS position (on the road)
		}
		
		System.out.println(state.toJSON());
	}

}
