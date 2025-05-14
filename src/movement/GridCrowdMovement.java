/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;

/**
 * Random waypoint movement model. Creates zig-zag paths within the simulation
 * area.
 * <p>
 * Modified to simulate according to the paper of prophet.
 */
public class GridCrowdMovement extends MovementModel {

	/**
	 * how many waypoints should there be per path
	 */
	private static final int PATH_LENGTH = 1;
	public static final int GATHERING_AREA = 12;
	public static Integer ROW_SIZE;
	public static Integer COL_SIZE;

	private Coord lastWaypoint;
	private int homeArea;

	private int area;

	public GridCrowdMovement(Settings settings) {
		super(settings);

		if (settings.contains("homeArea")) {
			this.homeArea = settings.getInt("homeArea");
		} else {
			this.homeArea = rng.nextInt(1, 12);
		}

		/* Initialize world grid size */
		if (ROW_SIZE == null || COL_SIZE == null) {
			initWorldGrids(settings);
		}
	}

	protected GridCrowdMovement(GridCrowdMovement rwp) {
		super(rwp);
		this.homeArea = rwp.homeArea;
		this.area = rwp.area;
	}

	protected void initWorldGrids(Settings settings) {
		if (settings.contains("rowSize")) {
			GridCrowdMovement.ROW_SIZE = settings.getInt("rowSize");
		} else {
			GridCrowdMovement.ROW_SIZE = 3;
		}

		if (settings.contains("colSize")) {
			GridCrowdMovement.COL_SIZE = settings.getInt("colSize");
		} else {
			GridCrowdMovement.COL_SIZE = 4;
		}
	}

	/**
	 * Returns a possible (random) placement for a host
	 *
	 * @return Random position on the map
	 */
	@Override
	public Coord getInitialLocation() {
		assert rng != null : "MovementModel not initialized!";
//		Coord c = randomCoord();

		/* Initialize the node within its home area */
		Coord c = coordFromArea(homeArea);

		this.lastWaypoint = c;
		return c;
	}

	@Override
	public Path getPath() {
		Path p;
		p = new Path(generateSpeed());
		p.addWaypoint(lastWaypoint.clone());
		Coord c = lastWaypoint;

		for (int i = 0; i < PATH_LENGTH; i++) {
			c = randomCoord();
			p.addWaypoint(c);
		}

		this.lastWaypoint = c;
		return p;
	}

	@Override
	public GridCrowdMovement replicate() {
		return new GridCrowdMovement(this);
	}

	protected Coord randomCoord() {
		if (lastWaypoint != null) {
			area = chooseArea();
		} else {
			area = homeArea;
		}

		return coordFromArea(area);
	}

	private Coord coordFromArea(int area) {
		// get the cell row and column of the area from enum
		GridCell cell = GridCell.fromId(area);
		final int row = cell.getRow();
		final int column = cell.getColumn();

		// calculate x based on column
		final double xMin = column * getMaxX() / 4.0;
		final double x = xMin + (rng.nextDouble() * (getMaxX() / 4.0));

		// calculate y based on row (y increases downwards)
		double yMin = row * getMaxY() / 3.0;
		double y = yMin + (rng.nextDouble() * (getMaxY() / 3.0));

		return new Coord(x, y);
	}

	protected int chooseArea() {
		double probability = rng.nextDouble();

		/*
		 * We assume that area 1 to 11 are the homes, while 12 is the gathering place.
		 * */

		if (area == homeArea) { /* AT HOME */
			if (probability <= 0.2) {
				return rng.nextInt(1, 11);
			} else {
				return GATHERING_AREA;
			}
		} else { /* ELSEWHERE */
			if (probability <= 0.1) {
				return rng.nextInt(1, 11);
			} else {
				return homeArea;
			}
		}
	}

	/**
	 * Enum defining the 3x4 grid cells with row and column indices.
	 */
	public enum GridCell {
		C1(1, 0, 0), C2(2, 0, 1), C3(3, 0, 2), C4(4, 0, 3),
		C5(5, 1, 0), C6(6, 1, 1), C7(7, 1, 2), C8(8, 1, 3),
		C9(9, 2, 0), C10(10, 2, 1), C11(11, 2, 2), C12(12, 2, 3);

		private final int id;
		private final int row;
		private final int column;

		GridCell(int id, int row, int column) {
			this.id = id;
			this.row = row;
			this.column = column;
		}

		public int getId() {
			return id;
		}

		public int getRow() {
			return row;
		}

		public int getColumn() {
			return column;
		}

		public static GridCell fromId(int id) {
			for (GridCell cell : values()) {
				if (cell.id == id) {
					return cell;
				}
			}
			throw new IllegalArgumentException("Invalid cell ID: " + id);
		}
	}
}
