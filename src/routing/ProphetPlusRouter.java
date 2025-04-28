package routing;

import core.*;

import java.util.*;

/**
 * Implementing the History of Delivery Predictability proposed by Euk Han Lee, et al.
 * The proposed method adds another rule whether a message should be passed by comparing
 * the current peer's delivery probability and its previous probability towards contact
 * with the message's final destination.
 *
 * @author narwa
 * @see <a href="https://www.mdpi.com/2076-3417/8/11/2215">An Efficient Routing Protocol Using the History of Delivery Predictability in Opportunistic Networks</a>
 */
public class ProphetPlusRouter extends ProphetRouter {

	/**
	 * To store previous probability of deliverance value for the proposed algorithm.
	 * */
	protected Map<DTNHost, Double> prevPreds;

	public ProphetPlusRouter(Settings s) {
		super(s);
		init();
	}

	protected ProphetPlusRouter(ProphetRouter r) {
		super(r);
		init();
	}

	private ProphetPlusRouter(ProphetPlusRouter r) {
		super(r);
		this.prevPreds = new HashMap<>(r.prevPreds);
	}

	private void init() {
		this.prevPreds = new HashMap<>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			this.updateDeliveryPredFor(otherHost);
			super.updateTransitivePreds(otherHost);
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
		double oldValue = super.getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		super.preds.put(host, newValue);
		this.prevPreds.put(host, oldValue);
	}

	/**
	 * Updates the passed host's previous delivery probability for the host.
	 *
	 * @param host the prevPreds owner.
	 * @param peer the key of contact probability to be modified.
	 * */
	protected void updatePrevDeliveryPredFor(DTNHost host, DTNHost peer) {
		ProphetPlusRouter router = (ProphetPlusRouter) host.getRouter();
		router.prevPreds.put(peer, router.getPredFor(peer));
	}

	/**
	 * Modified version of {@link ProphetRouter#tryOtherMessages()} for the proposed algorithm with a more
	 * restrictive rule.
	 *
	 * @author narwa
	 */
	@Override
	protected Tuple<Message, Connection> tryOtherMessages() {
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> messages = new ArrayList<>();

		for (Connection con : getConnections()) {
			DTNHost peer = con.getOtherNode(getHost());
			ProphetPlusRouter peerRouter = (ProphetPlusRouter) peer.getRouter();

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
					this.updatePrevDeliveryPredFor(peer, m.getTo());
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
						}
					} else {
						messages.add(new Tuple<>(m, con));
					}

					// Updates the previous probability of peer with the destination
					this.updatePrevDeliveryPredFor(peer, m.getTo());
				}
			}
		}


		// sort the message-connection tuples
		messages.sort(new TupleComparator());
		return tryMessagesForConnected(messages);
	}

	@Override
	public MessageRouter replicate() {
		return new ProphetPlusRouter(this);
	}
}
