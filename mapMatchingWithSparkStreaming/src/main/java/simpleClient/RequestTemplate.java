package simpleClient;

import java.text.SimpleDateFormat;
import java.util.Random;

import com.google.gson.Gson;

public class RequestTemplate {
	private String point;
	private String time;
	private String id;

	public RequestTemplate() {}

	public RequestTemplate(String point, String time, String id) {
		this.point = point;
		this.time = time;
		this.id = id;
	}

	public RequestTemplate(Double latitude, Double longitude, long date, String id) {
		this.point = "POINT(" + longitude + " " + longitude + ")";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
		this.setTime(simpleDateFormat.format(date).toString());
		this.id = id;
	}

	public String getPoint() {
		return point;
	}

	public void setPoint(String point) {
		this.point = point;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String toRequestJSONformat() {
		return null;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}
	
	//This shows what the JSON-ization would result in.
	public static void main(String[] args) {
		RequestTemplate ra = new RequestTemplate(new Random().nextDouble(), new Random().nextDouble(), System.currentTimeMillis(), "sup");
		Gson gson = new Gson();
		System.out.println(gson.toJson(ra));
	}
}
