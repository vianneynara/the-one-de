package report;

import core.*;
import routing.DecisionEngineRouter;
import routing.community.CentralityCount;
import routing.community.DistributedBubbleRap;

import java.util.*;

/**
 * This reports the number of communities within the algorithm per defined interval.
 *
 * @author narwa
 */
public class PeriodicCommunityUniquesReporter extends Report {

	public static final String UPDATE_INTERVAL_S = "windowInterval";
	public static final int DEFAULT_UPDATE_INTERVAL = 24 * 60 * 60; // 24 hours
	public static final String SEPARATOR = ";";

	private double lastRecord;
	private int interval;

	private Map<DTNHost, List<Set<DTNHost>>> recordedUniqueEncounters;

	public PeriodicCommunityUniquesReporter() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		Settings settings = getSettings();

		lastRecord = Double.MIN_VALUE;

		if (settings.contains(UPDATE_INTERVAL_S)) {
			interval = settings.getInt(UPDATE_INTERVAL_S);
		} else {
			interval = -1; /* not found; use default */
		}

		if (interval < 0) { /* not found or invalid value -> use default */
			interval = DEFAULT_UPDATE_INTERVAL;
		}

		recordedUniqueEncounters = new HashMap<>();
	}

//	@Override
//	public void updated(List<DTNHost> hosts) {
//
//		final int currTime = SimClock.getIntTime();
//		if (currTime - lastRecord >= interval) {
//			lastRecord = currTime;
//			updateReportPerInterval();
//		}
//	}
//
//	private void updateHosts(List<DTNHost> hosts) {
//		for (DTNHost host : hosts) {
//			if (!(host.getRouter() instanceof DecisionEngineRouter der)) {
//				continue;
//			}
//			if (!(der.getDecisionEngine() instanceof DistributedBubbleRap dbr)) {
//				continue;
//			}
//			dbr.update(host);
//		}
//	}
//
//	private void updateReportPerInterval() {
//		final List<DTNHost> hosts = SimScenario.getInstance().getHosts();
//
//		// retrieving all unique encounters within the time
//		for (DTNHost host : hosts) {
//			if (!(host.getRouter() instanceof DecisionEngineRouter der)) {
//				continue;
//			}
//			if (!(der.getDecisionEngine() instanceof DistributedBubbleRap dbr)) {
//				continue;
//			}
//
////			final List<Set<DTNHost>> allHostEncounters = recordedUniqueEncounters.getOrDefault(host, new ArrayList<>());
////			final Set<DTNHost> encounters = new HashSet<>();
//
////			dbr.getConnHistory().forEach((h, durations) -> {
////				if (durations != null) {
////					final Duration recentDuration = durations.get(durations.size() - 1);
////					final int currTime = SimClock.getIntTime();
////					if (recentDuration.start >= lastRecord && recentDuration.end <= currTime) {
////						encounters.add(h);
////					}
////				}
////			});
//
//			recordedUniqueEncounters.put(host, dbr.getPeriodicEncounters());
//
////			allHostEncounters.add(encounters);
////			recordedUniqueEncounters.put(host, allHostEncounters);
//		}
//	}

	@Override
	public void done() {
		final List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		StringBuilder sb = new StringBuilder();
		// adding header
		sb.append("Node-ID").append(SEPARATOR).append("Popularity").append(";\n");

//		write("SIZE OF recordedUniqueEncounters: " + recordedUniqueEncounters.size());
		for (var host : hosts) {
			if (!(host.getRouter() instanceof DecisionEngineRouter der)) {
				continue;
			}
//			if (!(der.getDecisionEngine() instanceof CentralityCount cc)) {
//				continue;
//			}
			if (!(der.getDecisionEngine() instanceof DistributedBubbleRap dbr)) {
				continue;
			}
			sb.append(host.getAddress()).append(SEPARATOR).append("global").append(SEPARATOR);
			dbr.getPeriodicEncounters().forEach((encounters) -> {
				sb.append(encounters.size()).append(SEPARATOR);
			});
//			dbr.getGlobalEncounters().forEach((encounters) -> {
//				sb.append(encounters.size()).append(SEPARATOR);
//			});
			sb.append("\n");
		}

		write(sb.toString());

		super.done();
	}
}
