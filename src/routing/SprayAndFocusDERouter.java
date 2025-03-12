package routing;

import core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Decision Engine implementation for Spray And Wait algorithm.
 *
 * @author narwa
 */
public class SprayAndFocusDERouter implements RoutingDecisionEngine {
	/**
	 * identifier for the initial number of copies setting ({@value})
	 */
	public static final String NROF_COPIES = "nrofCopies";
	/**
	 * identifier for the binary-mode setting ({@value})
	 */
	public static final String BINARY_MODE = "binaryMode";
	/**
	 * identifier for the transitivity threshold setting ({@value})
	 */
	public static final String TRANSITIVITY_THRESHOLD = "transitivityThreshold";
	/**
	 * SprayAndWait router's settings name space ({@value})
	 */
	public static final String SPRAYANDFOCUS_NS = "SprayAndFocusDERouter";
	/**
	 * Message property key
	 */
	public static final String MSG_COUNT_PROP = SPRAYANDFOCUS_NS + "." + "copies";

//	/* Focus router properties */
//	protected static final double DEFAULT_TRANSITIVITY_THRESHOLD = 1.0;

	/* Spray And Focus router properties */
	protected int initialNrofCopies;
	protected boolean isBinary;
	protected double transitivityTimerThreshold;

	/* Holds the contacts between this host and other hosts */
	protected Map<DTNHost, Double> localEncounters;

	/**
	 * Settings constructor.
	 */
	public SprayAndFocusDERouter(Settings s) {
		if (s == null) {
			s = new Settings(SPRAYANDFOCUS_NS);
		}

		initialNrofCopies = s.getInt(NROF_COPIES);
		isBinary = s.getBoolean(BINARY_MODE);

//		if (s.contains(TRANSITIVITY_THRESHOLD)) {
//			transitivityTimerThreshold = s.getDouble(TRANSITIVITY_THRESHOLD);
//		} else {
//			transitivityTimerThreshold = DEFAULT_TRANSITIVITY_THRESHOLD;
//		}

		localEncounters = new HashMap<>();
	}

	/**
	 * Copy constructor.
	 */
	public SprayAndFocusDERouter(SprayAndFocusDERouter r) {
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
//		this.transitivityTimerThreshold = r.transitivityTimerThreshold;

		this.localEncounters = new HashMap<>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
	}

	/**
	 * This function is called during encounter between two decision engines to exchange and update
	 * information in a simultaneous fashion.
	 *
	 * @implNote  Definition 2.3 (Timer Transitivity) Let a node A encounter a node B at distance dAB.
	 * Let further tm(d) denote the expected time it takes a node to move a distance d
	 * under a given mobility model. Then: ∀j= B : τB(j) < τA(j) −tm(dAB),set τA(j)=τB(j)+tm(dAB).
	 */
	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
		DTNHost self = con.getOtherNode(peer);
		SprayAndFocusDERouter peerRouter = getDecisionEngineRouter(peer);

		// update encounters of each host
		this.localEncounters.put(peer, SimClock.getTime());
		peerRouter.localEncounters.put(self, SimClock.getTime());

		// update transitivity
		//  ∀j= B : τB(j) < τA(j) −tm(dAB), set τA(j)=τB(j)+tm(dAB)
		// untuk setiap host (j) pada B (peer), lakukan:
		for (Map.Entry<DTNHost, Double> entry : peerRouter.localEncounters.entrySet()) {
			DTNHost host = entry.getKey();
			double peerLastEncounterTime = entry.getValue();
			// make the default zero of non-existent contact big to let it be overridden.
			double selfLastEncounterTime = this.localEncounters.getOrDefault(host, Double.POSITIVE_INFINITY);

			double distanceAB = self.getLocation().distance(peer.getLocation());

			// set default value since path could be null (i got NullPointerException lol)
			double selfSpeed = self.getPath() != null ? self.getPath().getSpeed() : 0.0;
			double peerSpeed = peer.getPath() != null ? peer.getPath().getSpeed() : 0.0;
			double expectedTimeToMove = distanceAB / Math.max(selfSpeed, peerSpeed);

			if (peerLastEncounterTime < selfLastEncounterTime - expectedTimeToMove) {
				this.localEncounters.put(host, peerLastEncounterTime + expectedTimeToMove);
			}
		}
	}

	/**
	 * What is executed/processed before deciding whether a message should be forwarded on.
	 */
	@Override
	public boolean newMessage(Message m) {
		// adding spray and wait spray special property
		m.addProperty(MSG_COUNT_PROP, initialNrofCopies);

		// message creation is always allowed
		return true;
	}

	/**
	 * <b>(Sender side)</b>
	 * From this current router, the message is in the process of being passed on.
	 * The rule that checks whether a host qualifies as the final destination.
	 */
	@Override
	public boolean isFinalDest(Message m, DTNHost otherHost) {
//		if (otherHost.equals(m.getTo()))
//			System.out.printf("%s is arriving to finalDest: %s %n", m.getId(), otherHost);

		// checks whether the other host is the message's destination
		return otherHost.equals(m.getTo());
	}

	/**
	 * <b>(Receiver side)</b>
	 * Called when receiving a message from a peer to determine whether to be saved.
	 *
	 * @return True if the message should be saved, false otherwise.
	 */
	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
		// this host which receives the message is not the destination thus,
		// saving this message to be further routed.
		if (!m.getTo().equals(thisHost)) {

			// Upon receiving the message, split it//decrement it
//			System.out.printf("shouldSaveReceivedMessage: %s, has copies of %d\n", m.toString(), (int) m.getProperty(MSG_COUNT_PROPERTY));
			int nrofCopies = (int) m.getProperty(MSG_COUNT_PROP);
			if (isBinary) {
				// use ceil (upper bound) on the receiving end
				nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
				m.updateProperty(MSG_COUNT_PROP, nrofCopies);
			} else {
				m.updateProperty(MSG_COUNT_PROP, --nrofCopies);
			}
//			System.out.printf("shouldSaveReceivedMessage: %s, has copies of %d\n", m.toString(), (int) m.getProperty(MSG_COUNT_PROPERTY));
			return true;
		}

		return false;
	}

	/**
	 * <b>(Sender side)</b>
	 * Called when this router exchanges connection with a peer, to decide whether the message
	 * should be passed or not.
	 *
	 * @return True if the message should be forwarded, false otherwise.
	 */
	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
		// why would we need to forward the message if thisHost is the destination?
		if (m.getTo().equals(thisHost)) {
			return false;
		}

		// the other host is the message's destination! yay
		if (m.getTo().equals(otherHost)) {
			return true;
		}

		// not within the focus phase, give remaining copies
		if (((int) m.getProperty(MSG_COUNT_PROP) > 1) && (otherHost != null)) {
			return true;
		}

		/* FOCUS PHASE */

		DTNHost destination = m.getTo();
		//
		assert otherHost != null : "Other host should not be null!";
		SprayAndFocusDERouter peerRouter = getDecisionEngineRouter(otherHost);

		// other host has never encountered the destination
		if (!getDecisionEngineRouter(otherHost).localEncounters.containsKey(destination)) {
			return false;
		}

		// this host have never encountered the destination, but the other host has
		if (!this.localEncounters.containsKey(destination)) {
			return true;
		}

		// send message if the peer has more recent encounter than this host
		if (peerRouter.localEncounters.get(destination) > this.localEncounters.get(destination)) {
			return true;
		}

		return false;
	}

	/**
	 * <b>(Sender side)</b>
	 * Called after a message is sent to other peer, this determines whether the current
	 * message should be deleted from the current host.
	 *
	 * @return True if the message should be deleted from the host, false otherwise.
	 */
	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		// gather determining property
		int nrofCopies = (int) m.getProperty(MSG_COUNT_PROP);

		// message does not have any more copies to share, delete it.
		// this may indicate that the message has been received by its destination
		if (nrofCopies <= 1) {
			return true;
		}

		/*
		Don't delete the message since it has not reached destination yet.
		Message copy is decremented in this method as it's being executed in #transferDone()
		*/

		// check if the current SnW uses binary mode to decrement
		if (isBinary) {
			// use floor (lower bound) on the sending end
			nrofCopies = (int) Math.floor(nrofCopies / 2.0);
		} else {
			nrofCopies--;
		}

		m.updateProperty(MSG_COUNT_PROP, nrofCopies);

		return false;
	}

	/**
	 * <b>(Sender side)</b>
	 * Called if an attempt to transfer the message was unsuccessful OR already delivered to a peer.
	 * Deletes old message when the TTL has expired, message property has reached zerp,
	 * or the destination host told the message has expired/is old.
	 *
	 * @return True if the old message should be deleted, false otherwise.
	 */
	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		return m.getTtl() < 1 || (int) m.getProperty(MSG_COUNT_PROP) < 1 || m.getTo().equals(hostReportingOld);
	}

	@Override
	public void update(DTNHost thisHost) {

	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new SprayAndFocusDERouter(this);
	}

	/**
	 * Helper method to get another {@link SprayAndFocusDERouter} from another host.
	 * This replicates Bryan's version of the method when trying to directly access other router.
	 * We don't need it here.
	 */
	private SprayAndFocusDERouter getDecisionEngineRouter(DTNHost otherHost) {
		MessageRouter otherRouter = otherHost.getRouter();

		if (!(otherRouter instanceof DecisionEngineRouter)) {
			throw new IllegalStateException("This router only works with another DecisionEngineRouter");
		}
		RoutingDecisionEngine deRouting = ((DecisionEngineRouter) otherRouter).getDecisionEngine();

		if (SprayAndFocusDERouter.class.isAssignableFrom(deRouting.getClass())) {
			return (SprayAndFocusDERouter) deRouting;
		}
		throw new IllegalStateException("This router only works with another SprayAndWaitDERouter routing");
	}
}
