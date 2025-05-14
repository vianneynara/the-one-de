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
public class ProphetRandomRouter extends ProphetRouter {
	/**
	 * Prophet router's setting namespace ({@value})
	 */
	public static final String PROPHET_NS = "ProphetRandomRouter";

	public static Integer RNG_SEED;
	public static Random randomizer;

	public ProphetRandomRouter(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		init();

		if (RNG_SEED == null) {
			RNG_SEED = prophetSettings.getInt("rngSeed");
			randomizer = new Random(RNG_SEED);
		}
	}

	protected ProphetRandomRouter(ProphetRouter r) {
		super(r);
		init();
	}

	private ProphetRandomRouter(ProphetRandomRouter r) {
		super(r);
	}

	private void init() {
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
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
	 * Modified version of {@link ProphetRouter#tryOtherMessages()} for the proposed algorithm with a more
	 * restrictive rule.
	 *
	 * @author narwa
	 * */
	@Override
	protected Tuple<Message, Connection> tryOtherMessages() {
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> messages = new ArrayList<>();

		for (Connection con : getConnections()) {
			DTNHost peer = con.getOtherNode(getHost());
			ProphetRandomRouter peerRouter = (ProphetRandomRouter) peer.getRouter();

			// skip hosts that are transferring
			if (peerRouter.isTransferring()) {
				continue;
			}

			for (Message m : msgCollection) {
				// if peer has the message, skip it
				if (peerRouter.hasMessage(m.getId())) {
					continue;
				}

//				if (peer == m.getTo()) {
//					messages.add(new Tuple<>(m, con));
//
//					// Updates the previous probability of peer with the destination
//					continue;
//				}

//				tryAllMessagesToAllConnections();

				// RANDOMIZE IT!

				final boolean forwardTheMessage = randomizer.nextBoolean();

				if (forwardTheMessage) {
					messages.add(new Tuple<>(m, con));
					peerRouter.ageDeliveryPreds();
				}
			}
		}

//		// sort the message-connection tuples
//		messages.sort(new TupleComparator());

		// sort shuffle
		Collections.shuffle(messages);
		return tryMessagesForConnected(messages);
	}

	/**
	 * May or may not be used (currently unused and calls original class's)
	 * */
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		int resultStatus = super.receiveMessage(m, from);
//		updatePrevDeliveryPredFor(from, m.getTo());
		return resultStatus;
	}

	@Override
	public MessageRouter replicate() {
		return new ProphetRandomRouter(this);
	}
}
