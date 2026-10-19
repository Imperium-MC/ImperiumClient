/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.wecui;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;

/**
 * The current WorldEdit selection, as reported by the server over the {@code worldedit:cui}
 * plugin channel (see {@link WorldEditCui} for the wire format). Pure state: packets mutate
 * it, the renderer reads it, no Minecraft classes involved, so the parser is unit-testable
 * in the headless gametest harness.
 *
 * <p>The holder tracks a <em>single</em> selection (the classic one-region model): a shape, the
 * world id the server named (informational — Bukkit world names need not match client dimension
 * ids, so it never vetoes rendering; the selection is cleared on client world change instead),
 * and the two defining points:
 *
 * <ul>
 *   <li>cuboid — {@code point0}/{@code point1} are the two corners ({@code //pos1} / {@code //pos2}),
 *       either order; a point that has not been sent yet is {@code null} (an "incomplete" selection).</li>
 *   <li>ellipsoid — {@code point0} is the center block, {@code point1} carries the radii (x=rx, y=ry, z=rz).</li>
 * </ul>
 *
 * <p>Cleared on disconnect and on client world change; the server re-announces the selection
 * whenever it changes, so a stale box never survives a world switch.
 */
public final class WESelectionHolder {

	public enum Shape {
		CUBOID("cuboid"),
		ELLIPSOID("ellipsoid"),
		/** Parsed and stored (points via {@code p2}), but not rendered yet. */
		POLYGON2D("polygon2d"),
		/** Parsed and stored, but not rendered yet. */
		CYLINDER("cylinder");

		private final String wireName;

		Shape(String wireName) {
			this.wireName = wireName;
		}

		static Shape byWireName(String name) {
			for (Shape shape : values()) {
				if (shape.wireName.equals(name)) {
					return shape;
				}
			}
			return null;
		}
	}

	private static final WESelectionHolder INSTANCE = new WESelectionHolder();

	public static WESelectionHolder getInstance() {
		return INSTANCE;
	}

	private Shape shape;
	/** World id as named by the server's CUI events; may be null when the server omits it. */
	private String worldId;
	private double[] point0;
	private double[] point1;
	/** Polygon2d outline points ({@code x, z} pairs) and their y bounds from {@code mm}. */
	private final List<double[]> polygonPoints = new ArrayList<>();
	private double polygonMinY, polygonMaxY;
	private boolean polygonBoundsSet;

	private WESelectionHolder() {
	}

	public boolean hasSelection() {
		return shape != null;
	}

	public Shape getShape() {
		return shape;
	}

	public String getWorldId() {
		return worldId;
	}

	/** First corner (cuboid) or center (ellipsoid); null until the server sends it. */
	public double[] getPoint0() {
		return point0;
	}

	/** Second corner (cuboid) or radii rx/ry/rz (ellipsoid); null until the server sends it. */
	public double[] getPoint1() {
		return point1;
	}

	public List<double[]> getPolygonPoints() {
		return polygonPoints;
	}

	public double getPolygonMinY() {
		return polygonMinY;
	}

	public double getPolygonMaxY() {
		return polygonMaxY;
	}

	public boolean hasPolygonBounds() {
		return polygonBoundsSet;
	}

	public void clear() {
		shape = null;
		worldId = null;
		point0 = null;
		point1 = null;
		polygonPoints.clear();
		polygonBoundsSet = false;
	}

	/** True when both defining points are known for the current shape. */
	public boolean isComplete() {
		if (shape == Shape.CUBOID || shape == Shape.ELLIPSOID) {
			return point0 != null && point1 != null;
		}
		return polygonPoints.size() >= 2 && polygonBoundsSet;
	}

	/**
	 * Applies one decoded CUI message to the selection. Tolerant by design: unknown event
	 * types, malformed numbers and wrong arities are logged at debug level and skipped —
	 * a bad packet must never throw on the network thread or break later messages.
	 *
	 * @param multi  the message carried the {@code +} multi-selection prefix (we render the
	 *               last-announced region only)
	 * @param type   event id ({@code s}, {@code p}, {@code p2}, {@code e}, {@code mm}, ...)
	 * @param args   everything after the event id
	 */
	public void handle(boolean multi, String type, List<String> args) {
		switch (type) {
			case "s" -> handleSelection(args);
			case "p" -> handlePoint3d(args);
			case "p2" -> handlePoint2d(args);
			case "e" -> handleEllipsoid(args);
			case "mm" -> handleMinMax(args);
			// "u" (update tick), "col"/"grid" (colour hints) and future events: no state for us.
			default -> AxolotlClientCommon.getInstance().getLogger()
				.debug("Ignoring worldedit:cui event '" + type + "' with " + args.size() + " args");
		}
	}

	/** {@code s|<shape>} — the server starts a new selection; all prior points are dropped. */
	private void handleSelection(List<String> args) {
		if (args.isEmpty()) {
			clear();
			return;
		}
		Shape parsed = Shape.byWireName(args.getFirst().toLowerCase());
		if (parsed == null) {
			// Unknown shape id: classic clients clear the selection here.
			AxolotlClientCommon.getInstance().getLogger()
				.debug("worldedit:cui selection event with unknown shape '" + args.getFirst() + "', clearing");
			clear();
			return;
		}
		shape = parsed;
		worldId = null;
		point0 = null;
		point1 = null;
		polygonPoints.clear();
		polygonBoundsSet = false;
	}

	/**
	 * {@code p|<id>|<x>|<y>|<z>|[area]} — a 3D selection point (id 0 = first, 1 = second).
	 * Also accepts the legacy one-shot cuboid line {@code p|world|minX|minY|minZ|maxX|maxY|maxZ|cuboid}.
	 */
	private void handlePoint3d(List<String> args) {
		if (args.size() == 8) {
			Shape legacy = Shape.byWireName(args.get(7).toLowerCase());
			if (legacy != null) {
				// Legacy format: both corners and the shape in a single message.
				double[] min = triple(args, 1);
				double[] max = triple(args, 4);
				if (min != null && max != null) {
					shape = legacy;
					worldId = args.get(0);
					point0 = min;
					point1 = max;
					polygonPoints.clear();
					polygonBoundsSet = false;
				}
				return;
			}
		}
		if (args.size() < 4) {
			return;
		}
		int id = integer(args.get(0));
		double[] pos = triple(args, 1);
		if (id < 0 || pos == null) {
			return;
		}
		if (id == 0) {
			point0 = pos;
		} else if (id == 1) {
			point1 = pos;
		}
	}

	/**
	 * {@code p2|<id>|<x>|<z>|[area]} — a 2D outline point (polygon selections), plus the
	 * legacy 3-arg form {@code p2|blockX|blockY|blockZ} which sets the second cuboid corner.
	 */
	private void handlePoint2d(List<String> args) {
		if (args.size() == 3) {
			double[] pos = triple(args, 0);
			if (pos != null) {
				point1 = pos;
			}
			return;
		}
		if (args.size() < 3) {
			return;
		}
		double x = number(args.get(1));
		double z = number(args.get(2));
		if (!Double.isNaN(x) && !Double.isNaN(z)) {
			polygonPoints.add(new double[]{x, z});
		}
	}

	/** {@code e|<id>|<x>|<y>|<z>} — id 0 sets the ellipsoid center, id 1 the radii. */
	private void handleEllipsoid(List<String> args) {
		if (args.size() < 4) {
			return;
		}
		int id = integer(args.get(0));
		double[] pos = triple(args, 1);
		if (pos == null) {
			return;
		}
		if (id == 0) {
			point0 = pos;
		} else if (id == 1) {
			point1 = pos;
		}
	}

	/** {@code mm|<minY>|<maxY>} — the y bounds of a polygon selection. */
	private void handleMinMax(List<String> args) {
		if (args.size() < 2) {
			return;
		}
		double min = number(args.get(0));
		double max = number(args.get(1));
		if (Double.isNaN(min) || Double.isNaN(max)) {
			return;
		}
		polygonMinY = min;
		polygonMaxY = max + 1;
		polygonBoundsSet = true;
	}

	private static double[] triple(List<String> args, int offset) {
		double x = number(args.get(offset));
		double y = number(args.get(offset + 1));
		double z = number(args.get(offset + 2));
		if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
			AxolotlClientCommon.getInstance().getLogger()
				.debug("Dropping malformed worldedit:cui coordinate");
			return null;
		}
		return new double[]{x, y, z};
	}

	private static int integer(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static double number(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException | NullPointerException e) {
			return Double.NaN;
		}
	}
}
