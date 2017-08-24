package response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import scala.Tuple2;

public class Trace implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String deviceID;
	private List<Coordinates> coordinates = new ArrayList<>();
	
	public Trace(String deviceID, List<Coordinates> coordinates) {
		this.deviceID = deviceID;
		this.coordinates = coordinates;
	}
	
	public Trace() {}
	
	public void addCoordinates(Double latitude, Double longitude) {
		this.coordinates.add(new Coordinates(latitude, longitude));
	}
	
	public String getDeviceID() {
		return deviceID;
	}
	public void setDeviceID(String deviceID) {
		this.deviceID = deviceID;
	}
	
	public List<Coordinates> getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(List<Coordinates> coordinates) {
		this.coordinates = coordinates;
	}

	public static void main(String[] args) {
		Trace trace = new Trace();
		trace.setDeviceID("sup1123");
		trace.addCoordinates(22.34343, 33.22323);
		trace.addCoordinates(77.777777, 8.888888);
		trace.addCoordinates(99.998899, 87.87878787);
		
		Gson gson = new Gson();
		System.out.println(gson.toJson(trace));
	}
	
	
	
}
