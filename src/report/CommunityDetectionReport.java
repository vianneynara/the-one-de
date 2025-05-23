/*
 * @(#)CommunityDetectionReport.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package report;

import java.util.*;

import core.*;
import routing.*;
import routing.community.CommunityDetectionEngine;

/**
 * <p>Reports the local communities at each node whenever the done() method is
 * called. Only those nodes whose router is a DecisionEngineRouter and whose
 * RoutingDecisionEngine implements the
 * routing.community.CommunityDetectionEngine are reported. In this way, the
 * report is able to output the result of any of the community detection
 * algorithms.</p>
 *
 * @author PJ Dillon, University of Pittsburgh
 */
public class CommunityDetectionReport extends Report {

	public CommunityDetectionReport() {
		init();
	}

	@Override
	public void done() {
		List<DTNHost> nodes = SimScenario.getInstance().getHosts();
		List<Set<DTNHost>> communities = new LinkedList<>();

		for (DTNHost h : nodes) {
			MessageRouter r = h.getRouter();
			if (!(r instanceof DecisionEngineRouter der))
				continue;
			RoutingDecisionEngine de = der.getDecisionEngine();
			if (!(de instanceof CommunityDetectionEngine cde))
				continue;

			boolean alreadyHaveCommunity = false;
			Set<DTNHost> nodeComm = cde.getLocalCommunity();

			// Test to see if another node already reported this community
			for (Set<DTNHost> c : communities) {
				if (c.containsAll(nodeComm) && nodeComm.containsAll(c)) {
					alreadyHaveCommunity = true;
					break;
				}
			}

			if (!alreadyHaveCommunity && !nodeComm.isEmpty()) {
				communities.add(nodeComm);
			}
		}

		// print each community and its size out to the file
		for (Set<DTNHost> c : communities)
			write("" + c.size() + ' ' + c);

		super.done();
	}
}