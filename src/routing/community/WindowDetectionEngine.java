package routing.community;

import core.DTNHost;

import java.util.List;
import java.util.Set;

public interface WindowDetectionEngine {

	/**
	 * To report periodic encounters set.
	 *
	 * @author narwa
	 * */
	List<Set<DTNHost>> getGlobalEncounters();

	/**
	 * To report periodic encounters counts.
	 *
	 * @author narwa
	 * */
	List<Integer> getGlobalEncountersCounts();
}
