package routing.community;

import core.DTNHost;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CentralityCount {

	List<Set<DTNHost>> getGlobalEncounters(Map<DTNHost, List<Duration>> connHistory);
}
