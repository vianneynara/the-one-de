package movement;

import core.Coord;
import core.Settings;

/**
 * Levy walk movement, adapted from Lévy flight model.
 *
 * @see <a href="https://en.wikipedia.org/wiki/L%C3%A9vy_flight">Lévy flight</a>
 * @see <a href="https://ieeexplore.ieee.org/document/5750071">On the Levy-Walk Nature of Human Mobility</a>
 */
public class LevyWalkDraft extends MovementModel {
	/**
	 * Lévy distribution alpha parameter
	 */
	private final double alpha;
	/**
	 * Lévy distribution beta parameter
	 */
	private final double beta;
	private Coord lastWaypoint;

	public LevyWalkDraft(Settings settings) {
		super(settings);
		this.alpha = 1.5;  // default value
		this.beta = 0.5;   // default value
	}

	protected LevyWalkDraft(LevyWalkDraft lw) {
		super(lw);
		this.alpha = lw.alpha;
		this.beta = lw.beta;
	}

	@Override
	public Coord getInitialLocation() {
		assert rng != null : "MovementModel not initialized!";
		Coord c = randomCoord();
		this.lastWaypoint = c;
		return c;
	}

	@Override
	public Path getPath() {
		Path p = new Path(generateSpeed());
		p.addWaypoint(lastWaypoint.clone());

		double length = Math.pow(rng.nextDouble(), -1 / alpha);
		double angle = 2 * Math.PI * rng.nextDouble();

		double x = lastWaypoint.getX() + length * Math.cos(angle);
		double y = lastWaypoint.getY() + length * Math.sin(angle);

		Coord c = new Coord(Math.min(Math.max(0, x), getMaxX()),
			Math.min(Math.max(0, y), getMaxY()));

		p.addWaypoint(c);
		this.lastWaypoint = c;
		return p;
	}

	@Override
	public LevyWalkDraft replicate() {
		return new LevyWalkDraft(this);
	}

	protected Coord randomCoord() {
		return new Coord(rng.nextDouble() * getMaxX(),
			rng.nextDouble() * getMaxY());
	}
}
