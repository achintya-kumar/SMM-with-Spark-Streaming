package response;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 * This class acts as a holder of latitude/longitude pair.
 * This would be later used to convert received JSON into a 
 * Java object.
 */

public class Coordinates {
	private Long timestamp;
	private Double latitude;
	private Double longitude;
	
	public Coordinates() {}
	
	public Coordinates(Long timestamp, Double latitude, Double longitude) {
		this.timestamp = timestamp;
		this.latitude = latitude;
		this.longitude = longitude;
	}
	
	public Coordinates(Double latitude, Double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
	
	public Double getLatitude() {
		return latitude;
	}
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}
	public Double getLongitude() {
		return longitude;
	}
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}
}
