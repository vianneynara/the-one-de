package routing.community;

import core.DTNHost;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Additional interface to support {@link report.PeriodicCommunityUniquesReporter}.
 *
 * @author narwa
 * */
public interface CentralityCount {
	/**
	 * Returns a sequential list of each encounter period/window given a connection history of a host.
	 *
	 * @param connHistory host's connection history.
	 * @return List of unique encounters.
	 * */
	List<Set<DTNHost>> getGlobalEncounters(Map<DTNHost, List<Duration>> connHistory);

	/**
	 * Returns a sequential list of the size of each encounter period/window given a connection history of a host.
	 *
	 * @param connHistory host's connection history.
	 * @return List of unique encounters sizes.
	 * */
	List<Integer> getGlobalEncountersCounts(Map<DTNHost, List<Duration>> connHistory);
}
