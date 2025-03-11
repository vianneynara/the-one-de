package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

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
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitDERouter";
	/**
	 * Message property key
	 */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
	/**
	 * Message property key for summary vector messages exchanged between direct peers
	 */
	public static final String SUMMARY_XCHG_PROP = SPRAYANDWAIT_NS + "." + "protoXchg";

	/* Focus router properties */
	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;

	/* Spray And Focus router properties */
	protected int initialNrofCopies;
	protected boolean isBinary;
	protected double transitivityTimerThreshold;

	/* Holds the encounters between this host and other hosts */
	protected Map<DTNHost, EncounterInfo> localEncounters;
	protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;

	/**
	 * Settings constructor.
	 */
	public SprayAndFocusDERouter(Settings s) {
		if (s == null) {
			s = new Settings(SPRAYANDWAIT_NS);
		}

		initialNrofCopies = s.getInt(NROF_COPIES);
		isBinary = s.getBoolean(BINARY_MODE);

		if (s.contains(TRANSITIVITY_THRESHOLD)) {
			transitivityTimerThreshold = s.getDouble(TRANSITIVITY_THRESHOLD);
		} else {
			transitivityTimerThreshold = defaultTransitivityThreshold;
		}

		localEncounters = new HashMap<>();
		neighborEncounters = new HashMap<>();
	}

	/**
	 * Copy constructor.
	 */
	public SprayAndFocusDERouter(SprayAndFocusDERouter r) {
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.transitivityTimerThreshold = r.transitivityTimerThreshold;

		this.localEncounters = new HashMap<>();
		this.neighborEncounters = new HashMap<>();
	}

	@Override
	public void connectionUp(DTNHost thisHost, DTNHost peer) {
	}

	@Override
	public void connectionDown(DTNHost thisHost, DTNHost peer) {
	}

	@Override
	public void doExchangeForNewConnection(Connection con, DTNHost peer) {
	}

	/**
	 * What is executed/processed before deciding whether a message should be forwarded on.
	 */
	@Override
	public boolean newMessage(Message m) {
		// adding spray and wait spray special property
		m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
		m.addProperty(SUMMARY_XCHG_PROP, localEncounters);

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
			int nrofCopies = (int) m.getProperty(MSG_COUNT_PROPERTY);
			if (isBinary) {
				// use ceil (upper bound) on the receiving end
				nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
				m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			} else {
				m.updateProperty(MSG_COUNT_PROPERTY, --nrofCopies);
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

		return ((int) m.getProperty(MSG_COUNT_PROPERTY) > 1) && (otherHost != null);
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
		int nrofCopies = (int) m.getProperty(MSG_COUNT_PROPERTY);

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

		m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);

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
		return m.getTtl() < 1 || (int) m.getProperty(MSG_COUNT_PROPERTY) < 1 || m.getTo().equals(hostReportingOld);
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
	@Deprecated
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

	/**
	 * Helper method to get last seen information of the host from its {@link #localEncounters}.
	 *
	 * @return The last seen value if exists, 0.0 otherwise.
	 * */
	protected double getLastSeenOfLocalEncounterFromHost(DTNHost host) {
		if (localEncounters.containsKey(host)) {
			return localEncounters.get(host).getLastSeenTime();
		} else {
			return 0.0;
		}
	}

	/**
	 * Stores all necessary info about encounters made by this host to some other host.
	 * At the moment, all that's needed is the timestamp of the <b>LAST TIME SEEN</b>
	 * these two hosts met.
	 *
	 * @author PJ Dillon, University of Pittsburgh
	 */
	protected class EncounterInfo {

		protected double seenAtTime;

		public EncounterInfo(double atTime) {
			this.seenAtTime = atTime;
		}

		public double getLastSeenTime() {
			return seenAtTime;
		}

		public void setLastSeenTime(double atTime) {
			this.seenAtTime = atTime;
		}
	}
}
