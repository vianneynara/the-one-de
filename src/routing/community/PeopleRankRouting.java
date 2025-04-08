package routing.community;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

import java.util.*;

/**
 * PeopleRank routing implementation for distributed social network.
 * This class is built based on the paper: "PeopleRank: Social Opportunistic Forwarding".
 * PeopleRank uses social rankings to determine suitable relays for a message to be sent.
 *
 * @author narwa
 */
public class PeopleRankRouting implements RoutingDecisionEngine {

	/* Settings identifier */
	public static final String DAMPING_FACTOR = "dampingFactor";
	public static final String FRIEND_THRESHOLD = "friendThreshold";
	public static final String FRIEND_DECIDER = "friendDecider";

	/* Algorithm parameters */
	/**
	 * Damping factor (d) defined as the probability, at any encounter, that the
	 * social relation between the nodes helps to improve the rank
	 * of these nodes. This means that, the higher the value of d, the
	 * more the algorithm accounts for the social relation between
	 * the nodes.
	 */
	private double dampingFactor;
	/**
	 * This constant defines the threshold value to determine whether a contact can be considered friend
	 * based on how long the contact went for. (in engine seconds)
	 */
	private double thresholdDuration;
	private double thresholdInterconn;
	private int thresholdFrequency;

	/**
	 * The current's configuration friend decider.
	 */
	private FriendDecider friendDecider;

	public static final double DEFAULT_DAMPING_FACTOR = 0.8;
	public static final double DEFAULT_FRIEND_THRESHOLD = 300;
	public static final FriendDecider DEFAULT_FRIEND_DECIDER = FriendDecider.INTERACTIONDURATION;

	/**
	 * Defines this host's current friends (neighbors).
	 */
	private Set<DTNHost> friends;
	/**
	 * List of durations of contacts per other nodes with this host.
	 */
	private Map<DTNHost, List<Duration>> connectionHistory;
	/**
	 * Stores this host's knowledge of the rankings of other hosts.
	 */
	private Map<DTNHost, Tuple<Double, Integer>> rankingKnowledge;
	/**
	 * Start times of ongoing contacts with other nodes. Necessary to store and
	 * handle multiple connections at the same time.
	 */
	private Map<DTNHost, Double> ongoingStartTimes;
	/**
	 * The rank calculated for this current router/relay.
	 */
	private double rank;

	/**
	 * Base constructor.
	 */
	public PeopleRankRouting(Settings s) {
		if (s.contains("dampingFactor")) {
			this.dampingFactor = s.getDouble("dampingFactor");
		} else {
			this.dampingFactor = DEFAULT_DAMPING_FACTOR;
		}

		if (s.contains("friendThreshold")) {
			this.thresholdDuration = s.getDouble("friendThreshold");
		} else {
			this.thresholdDuration = DEFAULT_FRIEND_THRESHOLD;
		}

		if (s.contains("thresholdInterconn")) {
			this.thresholdInterconn = s.getDouble("thresholdInterconn");
		} else {
			this.thresholdInterconn = DEFAULT_FRIEND_THRESHOLD;
		}

		if (s.contains("thresholdFrequency")) {
			this.thresholdFrequency = s.getInt("thresholdFrequency");
		} else {
			this.thresholdFrequency = 1;
		}

		if (s.contains("friendDecider")) {
			this.friendDecider = FriendDecider.fromString(s.getSetting("friendDecider"));
		} else {
			this.friendDecider = DEFAULT_FRIEND_DECIDER;
		}

		this.friends = new HashSet<>();
		this.connectionHistory = new HashMap<>();
		this.rankingKnowledge = new HashMap<>();
		this.ongoingStartTimes = new HashMap<>();
		this.rank = 0.0;
	}

	/**
	 * Copy constructor.
	 */
	public PeopleRankRouting(PeopleRankRouting r) {
		this.dampingFactor = r.dampingFactor;
		this.thresholdDuration = r.thresholdDuration;
		this.friendDecider = r.friendDecider;

		this.friends = new HashSet<>(r.friends);
		this.connectionHistory = new HashMap<>(r.connectionHistory);
		this.rankingKnowledge = new HashMap<>(r.rankingKnowledge);
		this.ongoingStartTimes = new HashMap<>(r.ongoingStartTimes);
		this.rank = r.rank;
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {

	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
		/* Get the last duration of the exchange between thisHost and peer */
		final double endTime = SimClock.getTime();
		final double startTime = ongoingStartTimes.getOrDefault(peer, 0.0);

		/* Have we met that peer yet? */
		List<Duration> durations;
		if (connectionHistory.containsKey(peer)) {
			durations = connectionHistory.get(peer);
		} else {
			durations = new LinkedList<>();
			connectionHistory.put(peer, durations);
		}
		durations.add(Duration.from(startTime, endTime));

		/* Use friend decider to determine whether the peer's contact can be considered friend. */
		switch (friendDecider) {
			case INTERACTIONDURATION -> {
				final double duration = endTime - startTime;
				if (duration >= thresholdDuration) {
					friends.add(peer);
				}
			}
			case INTERCONNECTIVITY -> {
				final double avgInterConnectivity = calculateAverageInterconnectivity(durations);
				if (avgInterConnectivity <= thresholdInterconn) {
					friends.add(peer);
				}
			}
			case CONTACTFREQUENCY -> {
				final int frequency = durations.size();
				if (frequency >= thresholdFrequency) {
					friends.add(peer);
				}
			}
		}

		/* Update the peer's rank */
		for (var entry : connectionHistory.entrySet()) {
			DTNHost currHost = entry.getKey();
			double currHostRank = calculatePeopleRankOf(currHost);

			// Have this host meet that peer? (simulating behavior when adding to set datatype)
			// If yes: then it won't add to the set
			// if not: then add itself to set (to set (+1))
			final int totalFriends = connectionHistory.containsKey(peer)
				? getDecisionEngineRouter(peer).friends.size() + 1	// this host met that peer once
				: getDecisionEngineRouter(peer).friends.size();		// this host has not met that peer yet

			rankingKnowledge.put(currHost, Tuple.of(currHostRank, totalFriends));
		}
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		/* Get self DTNHost and the peer's routing */
		this.ongoingStartTimes.put(peer, SimClock.getTime());
	}

	@Override
	public boolean newMessage(Message m) {
		return true;
	}

	@Override
	public boolean isFinalDest(Message m, DTNHost aHost) {
		return aHost.equals(m.getTo());
	}

	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
		return !thisHost.equals(m.getTo());
	}

	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
		if (isFinalDest(m, otherHost)) {
			return true;
		}

		return calculatePeopleRankOf(thisHost) < calculatePeopleRankOf(otherHost);
	}

	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		return true;
	}

	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		return true;
	}

	@Override
	public void update(DTNHost thisHost) {

	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new PeopleRankRouting(this);
	}

	/**
	 * <b>Calculates the PeopleRank using the formula:</b>
	 * <p>PeR(N_i) = (1 - d) + d * sum(PeR(N_j) / |F(N_j)| for N_j in F(N_i))</p>
	 *
	 * @return PeopleRank in the form of double (0.0 to 1.0)
	 */
	private double calculatePeopleRankOf(DTNHost host) {
		double rankSum = 0.0;

		PeopleRankRouting hostRouting = getDecisionEngineRouter(host);
		// Calculates sum(PeR(N_j) / |F(N_j)| for N_j in F(N_i))
		for (Tuple<Double, Integer> tuple : hostRouting.rankingKnowledge.values()) {
			rankSum += tuple.getKey() / tuple.getValue();
		}

		return (1 - dampingFactor) + dampingFactor * rankSum;
	}

	/**
	 * <b>Calculates the interconnectivity average of a duration:</b>
	 * <p>A[start,end] ~ B[start,end] ~ C[start,end] ~ D[start,end] ~ E[start,end]</p>
	 * Given that list of durations, we calculate <code>interconnectivity(A,B) = B.start - A.end</code>.
	 *
	 * @return the average of interconnectivity in the form of double.
	 */
	private double calculateAverageInterconnectivity(List<Duration> durations) {
		double sum = 0.0;
		Iterator<Duration> iterator = durations.iterator();
		if (!iterator.hasNext()) {
			return 0.0;
		}
		// start iterating by moving index to the first element
		Duration nextDuration = iterator.next();
		Duration prevDuration;
		while (iterator.hasNext()) {
			prevDuration = nextDuration;
			nextDuration = iterator.next();
			sum += nextDuration.end - prevDuration.start;
		}
		return sum / durations.size();
	}

	/**
	 * Helper method to get another {@link PeopleRankRouting} from another host.
	 */
	@SuppressWarnings("DuplicatedCode")
	private PeopleRankRouting getDecisionEngineRouter(DTNHost otherHost) {
		MessageRouter otherRouter = otherHost.getRouter();

		if (!(otherRouter instanceof DecisionEngineRouter)) {
			throw new IllegalStateException("This router only works with another DecisionEngineRouter");
		}
		RoutingDecisionEngine deRouting = ((DecisionEngineRouter) otherRouter).getDecisionEngine();

		if (PeopleRankRouting.class.isAssignableFrom(deRouting.getClass())) {
			return (PeopleRankRouting) deRouting;
		}
		throw new IllegalStateException("This router only works with another " + this.getClass().getName() + " routing");
	}

	public enum FriendDecider {
		/**
		 * <b>INTERACTION mode:</b>
		 * <p>
		 * Deciding whether a peer should be considered friend based on the current contact's duration.
		 * </p>
		 */
		INTERACTIONDURATION,
		/**
		 * <b>INTERCONNECTIVITY mode:</b>
		 * <p>
		 * Deciding whether a peer should be considered friend based on the history of a peer's contact
		 * interactivity. Done by getting the average duration of all contacts.
		 * </p>
		 */
		INTERCONNECTIVITY,
		/**
		 * <b>INTERCONNECTIVITY mode:</b>
		 * <p>
		 *     Deciding whether a peer should be considered friend based on the frequency of a peer's contacts
		 *     with this router.
		 * </p>
		 * */
		CONTACTFREQUENCY;

		public static FriendDecider fromString(String type) {
			if (type == null) {
				throw new IllegalArgumentException("Friend decider should not be null");
			}
			return switch (type.toUpperCase()) {
				case "INTERACTIONDURATION" -> INTERACTIONDURATION;
				case "INTERCONNECTIVITY" -> INTERCONNECTIVITY;
				case "CONTACTFREQUENCY" -> CONTACTFREQUENCY;
				default -> throw new IllegalArgumentException("Invalid FriendDecider: " + type);
			};
		}
	}
}
