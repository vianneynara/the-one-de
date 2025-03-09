package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Decision Engine implementation for Spray And Wait algorithm.
 *
 * @author narwa
 * */
public class SprayAndWaitDERouter implements RoutingDecisionEngine {
	/** identifier for the initial number of copies setting ({@value})*/
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String BINARY_MODE = "binaryMode";
	/**
	 * SprayAndWait router's settings name space ({@value})
	 * */
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitDERouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +	"copies";

	protected int initialNrofCopies;
	protected boolean isBinary;

	/**
	 * Settings constructor.
	 * */
	public SprayAndWaitDERouter(Settings s) {
		if (s == null) {
			s = new Settings(SPRAYANDWAIT_NS);
		}

		initialNrofCopies = s.getInt(NROF_COPIES);
		isBinary = s.getBoolean(BINARY_MODE);
	}

	/**
	 * Copy constructor.
	 */
	public SprayAndWaitDERouter(SprayAndWaitDERouter r) {
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
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
	 * */
	@Override
	public boolean newMessage(Message m) {
		// adding spray and wait spray size
		m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);

		// message creation is always allowed
		return true;
	}

	/**
	 * The rule that checks whether a host qualifies as the final destination.
	 * */
	@Override
	public boolean isFinalDest(Message m, DTNHost otherHost) {
		int nrofCopies = (int) m.getProperty(MSG_COUNT_PROPERTY);
        nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);


		// checks whether the other host is the message's destination
		return otherHost.equals(m.getTo());
	}

	/**
	 * Called when receiving a message from a peer to determine whether to be saved.
	 *
	 * @return True if the message should be saved, false otherwise.
	 * */
	@Override
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
		// saves received message when the destination is not this current host
		return !m.getTo().equals(thisHost);
	}

	/**
	 * Called when this router exchanges connection with a peer, to decide whether the message
	 * should be passed or not.
	 *
	 * @return True if the message should be forwarded, false otherwise.
	 * */
	@Override
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
		// the other host is the message's destination! yay
		if (m.getTo().equals(otherHost)) {
			return true;
		}

		return (int) m.getProperty(MSG_COUNT_PROPERTY) > 1 && otherHost != null;
	}

	/**
	 * Called after a message is sent to other peer, this determines whether the current
	 * message should be deleted from the current host.
	 *
	 * @return True if the message should be deleted from the host, false otherwise.
	 * */
	@Override
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
		// gather determining property
		int nrofCopies = (int) m.getProperty(MSG_COUNT_PROPERTY);

		// message does not have any more copies to share, delete it
		if (nrofCopies <= 1) {
			return true;
		}

		// check if the current SnW uses binary mode
		if (isBinary) {
			nrofCopies = (int) Math.floor(nrofCopies / 2.0);
		} else {
			nrofCopies--;
		}

		m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return false;
	}

	/**
	 * Deletes old message when the TTL has expired or the destination host told the message has expired/is old.
	 * */
	@Override
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
		return m.getTtl() < 1 || m.getTo().equals(hostReportingOld);
	}

	@Override
	public void update(DTNHost thisHost) {

	}

	@Override
	public RoutingDecisionEngine replicate() {
		return new SprayAndWaitDERouter(this);
	}

	/**
	 * Helper method to get another {@link SprayAndWaitDERouter} from another host.
	 * */
	private SprayAndWaitDERouter getDecisionEngineRouter(DTNHost otherHost) {
		MessageRouter otherRouter = otherHost.getRouter();

		if (!(otherRouter instanceof DecisionEngineRouter)) {
			throw new IllegalStateException("This router only works with another DecisionEngineRouter");
		}
		RoutingDecisionEngine deRouting = ((DecisionEngineRouter) otherRouter).getDecisionEngine();

		if (SprayAndWaitDERouter.class.isAssignableFrom(deRouting.getClass())) {
			return (SprayAndWaitDERouter) deRouting;
		}
		throw new IllegalStateException("This router only works with another SprayAndWaitDERouter routing");
	}
}
