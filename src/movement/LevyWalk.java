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
	 * Namespace for {@link #miu} in the setting.
	 */
	private static final String MIU_S = "miu";

	/**
	 * Alpha value defines the slope parameter (flight lengths).
	 */
	protected double alpha;
	/**
	 * miu defines the pause times.
	 */
	protected double miu;

	protected Coord location;

	public LevyWalk(Settings s) {
		super(s);

		if (s.contains(ALPHA_S)) {
			this.alpha = s.getDouble(ALPHA_S);
		} else this.alpha = 3.0f;

		if (s.contains(MIU_S)) {
			this.alpha = s.getDouble(MIU_S);
		} else this.alpha = 1.0f;

		this.location = randomCoord();
	}

	public LevyWalk(LevyWalk lw) {
		super(lw);
		this.alpha = lw.alpha;
		this.miu = lw.miu;
		this.location = randomCoord();
	}

	@Override
	public Path getPath() {
		final Path path = new Path(generateSpeed());
		path.addWaypoint(location.clone());

		int nextX;
		int nextY;
		do {
			double step_length = nextPareto(alpha);
			System.out.printf("Step length: %f\n", step_length);

			/* Calculating a random direction (circle) */
			double theta = rng.nextDouble(0, 2 * Math.PI);

			/* Calculate the next X and Y according to the direction */
			nextX = (int) (location.getX() + step_length * Math.cos(theta));
			nextY = (int) (location.getY() + step_length * Math.sin(theta));
		} while (nextX >= getMaxX() || nextY >= getMaxY() || nextX <= 0 || nextY <= 0);
//		} while (nextX >= getMaxX() || nextY >= getMaxY() || nextX <= 0 + 200 || nextY <= 0 + 200);
//		} while (!(nextX < getMaxX() && nextY < getMaxY() && nextX > 0 && nextY > 0));
		Coord nextLocation = new Coord(nextX, nextY);
		path.addWaypoint(nextLocation);
		location = nextLocation;

		return path;
	}

	@Override
	public Coord getInitialLocation() {
		assert rng != null : "MovementModel not initialized!";
		return randomCoord();
	}

	@Override
	public LevyWalk replicate() {
		return new LevyWalk(this);
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
