package response;

/**
 * 
 * @author Achintya Kumar, Nishanth EV
 *
 *This class acts as a holder of latitude/longitude pair.
 *This would be later used to convert received JSON into a 
 *Java object.
 */

public class Coordinates {
	private Double latitude;
	private Double longitude;
	
	public Coordinates() {}
	
	public Coordinates(Double latitude, Double longitude) {
		super();
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
}
