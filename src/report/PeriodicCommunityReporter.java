package report;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.community.CommunityDetectionEngine;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This reports the communities within the algorithm per defined interval.
 *
 * @author narwa
 */
public class PeriodicCommunityReporter extends ContactTimesReport implements UpdateListener {

	public static final String UPDATE_INTERVAL_S = "updateInterval";
	public static final int DEFAULT_UPDATE_INTERVAL = 2000;

	private int totalContacts;
	private double lastRecord;
	private int interval;

	private List<List<Set<DTNHost>>> recordedCommunities;

	public PeriodicCommunityReporter() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		Settings settings = getSettings();

		totalContacts = 0;
		lastRecord = Double.MIN_VALUE;

		if (settings.contains(UPDATE_INTERVAL_S)) {
			interval = settings.getInt(UPDATE_INTERVAL_S);
		} else {
			interval = -1; /* not found; use default */
		}

		if (interval < 0) { /* not found or invalid value -> use default */
			interval = DEFAULT_UPDATE_INTERVAL;
		}

		recordedCommunities = new ArrayList<>();

//		System.out.println("PeriodicCommunityReporter interval: " + this.interval);
	}

	/**
	 * This is specifically overridden to properly handle big intervals.
	 * Regular UpdateListener's {@link UpdateListener#updated(List)} does not properly handle
	 * big intervals.
	 */
	@Override
	public void hostsConnected(DTNHost host1, DTNHost host2) {
		super.hostsConnected(host1, host2);

		this.totalContacts += 1;

		// Uses contact interval

		if (totalContacts - lastRecord >= interval) {
			lastRecord = totalContacts;
			updateReportPerInterval();
		}
	}

	/**
	 * Use whenn handling time based interval (milliseconds).
	 * */
	@Override
	public void updated(List<DTNHost> hosts) {
		/* Per interval */
//		final double rn = SimClock.getTime();
//		if (SimClock.getTime() - lastRecord >= interval) {
//			lastRecord = rn;
//			updateReportPerInterval();
//		}
	}

	private void updateReportPerInterval() {
		final List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		final List<Set<DTNHost>> currentCommunities = new LinkedList<>();

		for (DTNHost host : hosts) {
			MessageRouter router = host.getRouter();

			if (!(router instanceof DecisionEngineRouter der))
				continue;
			if (!(der.getDecisionEngine() instanceof CommunityDetectionEngine cde))
				continue;

			boolean isCommunityRecorded = false;
			Set<DTNHost> hostCommunity = cde.getLocalCommunity();

			// Check whether the recorded current communities already has the same community as the current host.
			for (Set<DTNHost> community : currentCommunities) {
				if (community.containsAll(hostCommunity) && hostCommunity.containsAll(community)) {
					isCommunityRecorded = true;
					break;
				}
			}

			// If host's community hasn't been added to the current communities, add it.
			if (!isCommunityRecorded && !hostCommunity.isEmpty()) {
				currentCommunities.add(hostCommunity);
			}
		}

		// Java out of memory error :skull:
//		recordedCommunities.add(currentCommunities);

		String sb = SimClock.getTime() + " " +
			totalContacts + " " +
			currentCommunities.size() + " " +

			// Comment/remove if only want to record the (previous) count of communities
			currentCommunities;

		write(sb);
//		System.out.println(sb);}
	}

	@Override
	public void done() {
		super.done();
	}
}
