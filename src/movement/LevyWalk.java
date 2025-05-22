package movement;

import core.Coord;
import core.Settings;

/**
 * Levy walk movement, adapted from Lévy flight model.
 *
 * @see <a href="https://en.wikipedia.org/wiki/L%C3%A9vy_flight">Lévy flight</a>
 * @see <a href="https://ieeexplore.ieee.org/document/5750071">On the Levy-Walk Nature of Human Mobility</a>
 */
public class LevyWalk extends MovementModel {
	/**
	 * Namespace for {@link #alpha} in the setting.
	 */
	private static final String ALPHA_S = "alpha";

	/**
	 * Alpha value defines the slope parameter (flight lengths). Typically, between 1.0 and 3.0.
	 */
	protected double alpha;

	protected Coord location;

	public LevyWalk(Settings s) {
		super(s);

		if (s.contains(ALPHA_S)) {
			this.alpha = s.getDouble(ALPHA_S);
		} else this.alpha = 3.0f;
	}

	public LevyWalk(LevyWalk lw) {
		super(lw);
		this.alpha = lw.alpha;
	}

	@Override
	public Path getPath() {
		final Path path = new Path(generateSpeed());
		path.addWaypoint(location.clone());

		double nextX;
		double nextY;
		do {
			double step_length = nextPareto(alpha);
//			System.out.printf("Step length: %f\n", step_length);

			/* Calculating a random direction (circle) */
			double theta = rng.nextDouble() * 2 * Math.PI;

			/* Calculate the next X and Y according to the direction */
			nextX = location.getX() + step_length * Math.cos(theta);
			nextY = location.getY() + step_length * Math.sin(theta);
		} while (nextX > getMaxX() || nextY > getMaxY() || nextX < 0 || nextY < 0);

		Coord nextLocation = new Coord(nextX, nextY);
		path.addWaypoint(nextLocation);
		location = nextLocation;

		return path;
	}

	@Override
	public Coord getInitialLocation() {
		assert rng != null : "MovementModel not initialized!";
		Coord c = randomCoord();
		this.location = c;
		return c;
	}

	@Override
	public LevyWalk replicate() {
		return new LevyWalk(this);
	}

	/**
	 * Generates an integer from calculating with pareto.
	 *
	 * @param alpha slope parameter
	 * @param miu scale parameter
	 * @return integer
	 */
	private double nextPareto(double alpha, double miu) {
		// uniform variable to inverse cumulative distribution function -> [0,1]
		double uniformRandom = rng.nextDouble();
		return miu / (Math.pow(1.0 - uniformRandom, -1.0 / alpha));
	}

	/**
	 * Generates an integer from calculating with pareto.
	 *
	 * @param alpha slope parameter
	 * @return integer
	 */
	private double nextPareto(double alpha) {
		// uniform variable to inverse cumulative distribution function -> [0,1]
		double uniformRandom = rng.nextDouble();
		return Math.pow(1.0 - uniformRandom, -1.0 / alpha);
	}

	protected Coord randomCoord() {
		return new Coord(rng.nextDouble() * getMaxX(), rng.nextDouble() * getMaxY());
	}
}
