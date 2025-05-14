package routing;

import core.*;

import java.util.*;

/// A completely separate class

/**
 * Implementing the History of Delivery Predictability proposed by Euk Han Lee, et al.
 * The proposed method adds another rule whether a message should be passed by comparing
 * the current peer's delivery probability and its previous probability towards contact
 * with the message's final destination.
 *
 * @author narwa
 * @see <a href="https://www.mdpi.com/2076-3417/8/11/2215">An Efficient Routing Protocol Using the History of Delivery Predictability in Opportunistic Networks</a>
 */
public class ProphetPlus2Router extends ActiveRouter {
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
	 * the value of nrof seconds in time unit -setting
	 */
	protected int secondsInTimeUnit;
	/**
	 * value of beta setting
	 */
	protected double beta;

	/**
	 * delivery predictabilities
	 */
	protected Map<DTNHost, Double> preds;
	/**
	 * last delivery predictability update (sim)time
	 */
	protected double lastAgeUpdate;

	/**
	 * To store previous probability of deliverance value for the proposed algorithm.
	 * */
	protected Map<DTNHost, Double> prevPreds;

	public ProphetPlus2Router(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		} else {
			beta = DEFAULT_BETA;
		}
		init();
	}

	protected ProphetPlus2Router(ProphetRouter r) {
		super(r);
		init();
	}

	private ProphetPlus2Router(ProphetPlus2Router r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		this.preds = new HashMap<>(r.preds);
		this.prevPreds = new HashMap<>(r.prevPreds);
	}

	private void init() {
		this.preds = new HashMap<>();
		this.prevPreds = new HashMap<>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			this.updateDeliveryPredFor(otherHost);
			this.updateTransitivePreds(otherHost);
		}
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
		this.tryOtherMessages();
	}

	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>.
	 * This method is used to update the {@link #prevPreds}.
	 *
	 * @param host The host we just met
	 * @author narwa
	 */
	protected void updateDeliveryPredFor(DTNHost host) {
		double oldValue = this.getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		this.preds.put(host, newValue);
		this.prevPreds.put(host, oldValue);
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
		assert otherRouter instanceof ProphetRouter : "PRoPHET only works " +
			" with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds =
			((ProphetPlus2Router) otherRouter).getDeliveryPreds();

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
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
			secondsInTimeUnit;

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

	/**
	 * Updates the passed host's previous delivery probability for the host.
	 *
	 * @param host the prevPreds owner.
	 * @param peer the key of contact probability to be modified.
	 * */
	protected void updatePrevDeliveryPredFor(DTNHost host, DTNHost peer) {
		ProphetPlus2Router hostRouter = (ProphetPlus2Router) host.getRouter();
		hostRouter.prevPreds.put(peer, hostRouter.getPredFor(peer));
	}

	protected void updatePrevDeliveryPredFor(DTNHost peer) {
		ProphetPlus2Router peerRouter = (ProphetPlus2Router) peer.getRouter();
		this.prevPreds.put(peer, peerRouter.getPredFor(peer));
	}

	/**
	 * Modified version of {@link ProphetRouter#tryOtherMessages()} for the proposed algorithm with a more
	 * restrictive rule.
	 *
	 * @author narwa
	 */
	protected Tuple<Message, Connection> tryOtherMessages() {
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> messages = new ArrayList<>();

		for (Connection con : getConnections()) {
			DTNHost peer = con.getOtherNode(getHost());
			ProphetPlus2Router peerRouter = (ProphetPlus2Router) peer.getRouter();

			// skip hosts that are transferring
			if (peerRouter.isTransferring()) {
				continue;
			}

			for (Message m : msgCollection) {
				// if peer has the message, skip it
				if (peerRouter.hasMessage(m.getId())) {
					continue;
				}

				if (peer == m.getTo()) {
					messages.add(new Tuple<>(m, con));

					// Updates the previous probability of peer with the destination
					peerRouter.updatePrevDeliveryPredFor(m.getTo());
					continue;
				}

				tryAllMessagesToAllConnections();

				// simplified parameters
				final double peerPred = peerRouter.getPredFor(m.getTo());
				final double selfPred = getPredFor(m.getTo());

				// if the peer has higher probability of deliverance than self
				if (peerPred > selfPred) {
					// self has previously met destination
					if (preds.containsKey(m.getTo())) {
						final double selfPrevPred = prevPreds.getOrDefault(m.getTo(), 0.0);
						if (peerPred >= selfPrevPred) {
							messages.add(new Tuple<>(m, con));
						} else {
							continue;
						}
					} else {
						messages.add(new Tuple<>(m, con));
					}

					// Updates the previous probability of peer with the destination
					peerRouter.updatePrevDeliveryPredFor(m.getTo());
				}
			}
		}


		// sort the message-connection tuples
		messages.sort(new TupleComparator());
		return tryMessagesForConnected(messages);
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		int resultStatus = super.receiveMessage(m, from);
//		updatePrevDeliveryPredFor(from, m.getTo());
		return resultStatus;
	}

	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	class TupleComparator implements Comparator
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
						   Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((ProphetPlus2Router) tuple1.getValue().
				getOtherNode(getHost()).getRouter()).getPredFor(
				tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((ProphetPlus2Router) tuple2.getValue().
				getOtherNode(getHost()).getRouter()).getPredFor(
				tuple2.getKey().getTo());

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
		RoutingInfo ri = new RoutingInfo(preds.size() +
			" delivery prediction(s)");

		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
				host, value)));
		}

		top.addMoreInfo(ri);
		return top;
	}

	@Override
	public MessageRouter replicate() {
		return new ProphetPlus2Router(this);
	}
}
