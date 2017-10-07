package mapMatchingWithSparkStreaming;

import java.io.Serializable;

import com.bmwcarit.barefoot.matcher.Matcher;
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

public class BroadcastedUtilities implements Serializable {

	private static final long serialVersionUID = 1L;

	private RoadMap map;
	
	private Matcher matcher; 
	
	boolean unconstructedMap = true;
	
	
	public BroadcastedUtilities(RoadMap map) {
		System.out.println("CONSTRUCTING MAP");
		this.map = map;
	}
	
	/**
	 * This returns the lazily-initialized barefoot matcher.
	 * @return
	 */
	public Matcher getBarefootMatcher() {
		synchronized (this) {
			if(matcher == null) {
				this.matcher = new Matcher(getRoadMap(), new Dijkstra<Road, RoadPoint>(), new TimePriority(), new Geography());
				return matcher;
			} else {
				return matcher;
			}
				
		}
	}
	
	/**
	 * This returns a RoadMap instance with constructed index.
	 * Serializing the class somehow disrupts the index and therefore 
	 * needs reconstruction.
	 * @return
	 */
	public RoadMap getRoadMap() {
		synchronized (this) {
			if(unconstructedMap) {
				this.map = map.construct();
				this.unconstructedMap = false;
			}
			return map;
		}
	}
}
