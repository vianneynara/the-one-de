/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.util.*;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class ProphetRouter extends ActiveRouter {
	/**
	 * delivery predictability initialization constant
	 */
	public static final double P_INIT = 0.75;
	/**
	 * delivery predictability transitivity scaling constant default value
	 */
	public static final double DEFAULT_BETA = 0.25;
	/**
	 * delivery predictability aging constant
	 */
	public static final double GAMMA = 0.98;

	/**
	 * Prophet router's setting namespace ({@value})
	 */
	public static final String PROPHET_NS = "ProphetRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.
	 */
	public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";

	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/**
	 * Drop policy to decide how the message should be dropped when the buffer is full.
	 */
	public static final String DROP_POLICY_S = "dropPolicy";

	/// ESSENTIAL MESSAGE PROPERTIES

	/**
	 * Message property key for MOFO.
	 */
	public static final String PROP_MOFO = PROPHET_NS + "." + "nrOfForwarded";

	/**
	 * Message property key for MOPR. Favorable Forwarded (Predictability).
	 */
	public static final String PROP_MOPR = PROPHET_NS + "." + "FP";

	/**
	 * the value of nrof seconds in time unit -setting
	 */
	protected int secondsInTimeUnit;
	/**
	 * value of beta setting
	 */
	protected double beta;

	protected DropPolicy dropPolicy;

	/**
	 * delivery predictabilities
	 */
	protected Map<DTNHost, Double> preds;
	/**
	 * last delivery predictability update (sim)time
	 */
	protected double lastAgeUpdate;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 *
	 * @param s The settings object
	 */
	public ProphetRouter(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		} else {
			beta = DEFAULT_BETA;
		}

		/* Drop policy */
		if (prophetSettings.contains(DROP_POLICY_S)) {
			dropPolicy = DropPolicy.of(prophetSettings.getInt(DROP_POLICY_S));
		} else {
			dropPolicy = DropPolicy.FIFO;
		}

		initPreds();
	}

	/**
	 * Copyconstructor.
	 *
	 * @param r The router prototype where setting values are copied from
	 */
	protected ProphetRouter(ProphetRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		this.dropPolicy = r.dropPolicy;
		initPreds();
	}

	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}

	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 *
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}

	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 *
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		} else {
			return 0;
		}
	}

	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 *
	 * @param host The B host who we just met
	 */
	protected void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof ProphetRouter : "PRoPHET only works " + " with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = ((ProphetRouter) otherRouter).getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 *
	 * @see #SECONDS_IN_UNIT_S
	 */
	void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / secondsInTimeUnit;

		if (timeDiff == 0) {
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue() * mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 *
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
//		System.out.println(tryOtherMessages());
		tryOtherMessages();
	}

	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 *
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	protected Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			ProphetRouter othRouter = (ProphetRouter) other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
//				tryAllMessagesToAllConnections();
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}
		// System.out.println(messages);
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);    // try to send messages
	}

	// ActiveRouter's OVERRIDDEN METHODS

	/**
	 * We modify this to support MOFO and MOPR drop policies.
	 *
	 * @author {jordan, narwa}
	 */
	@Override
	public boolean createNewMessage(Message m) {
		switch (dropPolicy) {
			case DropPolicy.MOFO:
				m.addProperty(PROP_MOFO, 0);
				return super.createNewMessage(m);
			case DropPolicy.MOPR:
				m.addProperty(PROP_MOPR, getPredFor(m.getTo()));
				return super.createNewMessage(m);
			default:
				return super.createNewMessage(m);
		}
	}

	/**
	 * We modify this so that we could use other drop policies while trying to free the buffer.
	 *
	 * @author {jordan, narwa}
	 */
	@Override
	protected boolean makeRoomForMessage(int size) {
		// check whether the message is too big
		if (size > this.getBufferSize()) {
			return false;
		}

		// the current free buffer
		int freeBuffer = this.getFreeBufferSize();

		while (freeBuffer < size) {
			Message toBeDropped;
			switch (dropPolicy) {
				case FIFO:
					toBeDropped = getOldestMessage(true);
					break;
				case MOFO:
					toBeDropped = getMostForward(true);
					break;
				case MOPR:
					toBeDropped = getHighestFP(true);
					break;
				case SHLI:
					toBeDropped = getShortestLifeMessage(true);
					break;
				case LEPR:
					toBeDropped = getLeastProbableMessage(true);
					break;
				default:
					throw new Error("Bjir");
			}

			// can't be deleted, there's none
			if (toBeDropped == null) {
				return false;
			}

			/* delete message from the buffer as "drop" */
			deleteMessage(toBeDropped.getId(), true);
			freeBuffer += toBeDropped.getSize();
		}

		return true;
	}

	/**
	 * MOFO – Evict most forwarded first In an attempt to maximize the dispersion of messages through the network,
	 * this policy requires that the routing agent keeps track of the number of times each message has been forwarded.
	 * The message that has been forwarded the largest number of times is the first to be dropped, thus giving messages that
	 * have not been forwarded a few times more chances of getting forwarded.
	 *
	 * @author jordan
	 */
	public Message getMostForward(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message mostForward = null;
		for (Message m : messages) {
			// skip the message(s) that router is sending
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue;
			}

			if (mostForward == null) {
				mostForward = m;
			} else if ((int) mostForward.getProperty(PROP_MOFO) > (int) m.getProperty(PROP_MOFO)) {
				mostForward = m;
			}
		}

		return mostForward;
	}

	/**
	 * MOPR – Evict most favorably forwarded first.
	 *
	 * @author jordan
	 */
	protected Message getHighestFP(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message highestFP = null;
		for (Message m : messages) {
			// skip the message(s) that router is sending
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue;
			}

			if (highestFP == null) {
				highestFP = m;
			} else if ((double) highestFP.getProperty(PROP_MOPR) > (double) m.getProperty(PROP_MOPR)) {
				highestFP = m;
			}
		}

		return highestFP;
	}

	/**
	 * SHLI – Evict shortest life time first In the DTN architecture
	 * [2], each message has a timeout value which specifies when
	 * it is no longer useful and should be deleted. If this policy
	 * is used, the message with the shortest remaining life time is
	 * the first to be dropped.
	 *
	 * @author jordan
	 */
	protected Message getShortestLifeMessage(boolean excludeMsgBeingSent) {
		Message shortestLife = null;
		for (Message m : getMessageCollection()) {
			// skip the message(s) that router is sending
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue;
			}

			if (shortestLife == null) {
				shortestLife = m;
			} else if (shortestLife.getTtl() > m.getTtl()) {
				shortestLife = m;
			}
		}

		return shortestLife;
	}

	/**
	 * LEPR – Evict least probable first Since the node is least
	 * likely to deliver a message for which it has a low P-value,
	 * drop the message for which the node has the lowest P-value.
	 *
	 * @author narwa
	 */
	protected Message getLeastProbableMessage(boolean excludeMsgBeingSent) {
		SortedMap<Double, Message> msgPreds = new TreeMap<>();
		for (Message message : getMessageCollection()) {
			// skip the message(s) that router is sending
			if (excludeMsgBeingSent && isSending(message.getId())) {
				continue;
			}

			double p = this.getPredFor(message.getTo());
			msgPreds.put(p, message);
		}
		return msgPreds.get(msgPreds.firstKey());
	}

	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	class TupleComparator implements Comparator<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((ProphetRouter) tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((ProphetRouter) tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2 - p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			} else if (p2 - p1 < 0) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() + " delivery prediction(s)");

		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", host, value)));
		}

		top.addMoreInfo(ri);
		return top;
	}

	@Override
	public MessageRouter replicate() {
		ProphetRouter r = new ProphetRouter(this);
		return r;
	}

	/**
	 * Drop policies used to decide which message should be dropped when the buffer is full.
	 *
	 * @author narwa
	 * @see "Evaluation of Queueing Policies and Forwarding Strategies for Routing in
	 * Intermittently Connected Networks" by Lindgren et al.
	 */
	enum DropPolicy {
		FIFO(1), MOFO(2), MOPR(3), SHLI(4), LEPR(5);

		final int order;

		DropPolicy(int order) {
			this.order = order;
		}

		public static DropPolicy of(int number) {
			for (DropPolicy dp : DropPolicy.values()) {
				if (dp.order == number) {
					return dp;
				}
			}

			throw new IllegalArgumentException("Unknown drop policy: " + number);
		}
	}
}
