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

import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a {@link WESelectionHolder} into world-space line geometry, the classic WorldEdit CUI
 * look: the bounding-box wireframe, the per-block grid on the three faces touching the
 * minimum corner, and the two corner markers in their own colours.
 *
 * <p>Split in two halves on purpose: {@link #buildSegments} is pure geometry (plain doubles,
 * no Minecraft state) so the gametest can assert the exact lines a given selection produces;
 * {@link #submit} then feeds those segments to the vanilla gizmo renderer. Rendering goes
 * through {@code Gizmos.line}: gizmo lines with alpha below 255 land in the translucent line
 * batch ({@code linesTranslucentNoDepthWrite}) — depth-tested against terrain but never
 * occluding, which is how the classic client behaved too.
 *
 * <p>Grid safety valve: a selection axis longer than {@link #MAX_GRID_LINES} blocks stops
 * emitting interior grid lines on the faces spanning that axis (the wireframe stays); a
 * 1000-block selection would otherwise cost thousands of line primitives per frame.
 */
public final class WESelectionRenderer {

	/** Interior grid lines per axis before the grid degrades to the wireframe on that axis. */
	static final int MAX_GRID_LINES = 256;
	/** Corner markers are the corner block's outline inflated by this, to avoid z-fighting. */
	private static final double CORNER_INFLATE = 0.02;
	/** Segments used to approximate each ellipsoid great circle. */
	private static final int ELLIPSE_SEGMENTS = 32;

	public record Segment(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
	}

	private WESelectionRenderer() {
	}

	/**
	 * Pure geometry: every line segment of the current selection, in world coordinates.
	 * The caller decides colours (already opacity-multiplied ARGB) via the palette.
	 */
	public static List<Segment> buildSegments(WESelectionHolder selection, Palette palette, boolean showGrid) {
		List<Segment> segments = new ArrayList<>();
		if (!selection.hasSelection()) {
			return segments;
		}
		switch (selection.getShape()) {
			case CUBOID -> buildCuboid(selection, palette, showGrid, segments);
			case ELLIPSOID -> buildEllipsoid(selection, palette, showGrid, segments);
			// Parsed but not rendered (yet); classic clients render these, we draw nothing.
			case POLYGON2D, CYLINDER -> {
			}
		}
		return segments;
	}

	private static void buildCuboid(WESelectionHolder selection, Palette palette, boolean showGrid,
		List<Segment> out) {
		double[] p0 = selection.getPoint0();
		double[] p1 = selection.getPoint1();
		if (p0 != null) {
			boxSegments(p0[0] - CORNER_INFLATE, p0[1] - CORNER_INFLATE, p0[2] - CORNER_INFLATE,
				p0[0] + 1 + CORNER_INFLATE, p0[1] + 1 + CORNER_INFLATE, p0[2] + 1 + CORNER_INFLATE,
				palette.minCorner(), out);
		}
		if (p1 != null) {
			boxSegments(p1[0] - CORNER_INFLATE, p1[1] - CORNER_INFLATE, p1[2] - CORNER_INFLATE,
				p1[0] + 1 + CORNER_INFLATE, p1[1] + 1 + CORNER_INFLATE, p1[2] + 1 + CORNER_INFLATE,
				palette.maxCorner(), out);
		}
		if (p0 == null || p1 == null) {
			return; // incomplete selection: corner markers only
		}
		double minX = Math.min(p0[0], p1[0]);
		double minY = Math.min(p0[1], p1[1]);
		double minZ = Math.min(p0[2], p1[2]);
		// Block coordinates are inclusive: the box extends one block past the max corner.
		double maxX = Math.max(p0[0], p1[0]) + 1;
		double maxY = Math.max(p0[1], p1[1]) + 1;
		double maxZ = Math.max(p0[2], p1[2]) + 1;
		boxSegments(minX, minY, minZ, maxX, maxY, maxZ, palette.grid(), out);
		if (showGrid) {
			minFaceGrid(minX, minY, minZ, maxX, maxY, maxZ, palette.grid(), out);
		}
	}

	/**
	 * The classic WE grid: lines every block on the three faces that touch the minimum
	 * corner (x=minX, y=minY, z=minZ). Edge positions themselves are the box wireframe,
	 * already emitted, so interior offsets start at +1 and stop before the far edge.
	 */
	private static void minFaceGrid(double minX, double minY, double minZ, double maxX, double maxY,
		double maxZ, int color, List<Segment> out) {
		for (double x = minX + 1; x < maxX - 0.5 && x <= minX + MAX_GRID_LINES; x++) {
			out.add(new Segment(x, minY, minZ, x, minY, maxZ, color)); // y=minY face, along z
			out.add(new Segment(x, minY, minZ, x, maxY, minZ, color)); // z=minZ face, along y
		}
		for (double y = minY + 1; y < maxY - 0.5 && y <= minY + MAX_GRID_LINES; y++) {
			out.add(new Segment(minX, y, minZ, minX, y, maxZ, color)); // x=minX face, along z
			out.add(new Segment(minX, y, minZ, maxX, y, minZ, color)); // z=minZ face, along x
		}
		for (double z = minZ + 1; z < maxZ - 0.5 && z <= minZ + MAX_GRID_LINES; z++) {
			out.add(new Segment(minX, minY, z, minX, maxY, z, color)); // x=minX face, along y
			out.add(new Segment(minX, minY, z, maxX, minY, z, color)); // y=minY face, along x
		}
	}

	private static void buildEllipsoid(WESelectionHolder selection, Palette palette, boolean showGrid,
		List<Segment> out) {
		double[] center = selection.getPoint0();
		double[] radii = selection.getPoint1();
		if (center != null) {
			boxSegments(center[0] - CORNER_INFLATE, center[1] - CORNER_INFLATE, center[2] - CORNER_INFLATE,
				center[0] + 1 + CORNER_INFLATE, center[1] + 1 + CORNER_INFLATE, center[2] + 1 + CORNER_INFLATE,
				palette.minCorner(), out);
		}
		if (center == null || radii == null) {
			return; // center marker only until the server sends radii
		}
		double cx = center[0] + 0.5, cy = center[1] + 0.5, cz = center[2] + 0.5;
		double rx = Math.abs(radii[0]), ry = Math.abs(radii[1]), rz = Math.abs(radii[2]);
		// Bounding box around the full ellipsoid volume.
		boxSegments(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz, palette.grid(), out);
		if (showGrid) {
			ellipse(cx, cy, cz, rx, ry, 0, palette.grid(), out);
			ellipse(cx, cy, cz, rx, 0, rz, palette.grid(), out);
			ellipse(cx, cy, cz, 0, ry, rz, palette.grid(), out);
		}
	}

	/** One great-circle ellipse in the plane given by the two non-zero radii, as a polyline. */
	private static void ellipse(double cx, double cy, double cz, double rx, double ry, double rz,
		int color, List<Segment> out) {
		double px = 0, py = 0, pz = 0;
		for (int i = 0; i <= ELLIPSE_SEGMENTS; i++) {
			double angle = Math.TAU * i / ELLIPSE_SEGMENTS;
			double x = rx * Math.cos(angle);
			double y = ry * Math.sin(angle);
			double z = rz * Math.sin(angle);
			if (i > 0) {
				out.add(new Segment(cx + px, cy + py, cz + pz, cx + x, cy + y, cz + z, color));
			}
			px = x;
			py = y;
			pz = z;
		}
	}

	/** The 12 edges of an axis-aligned box. */
	private static void boxSegments(double minX, double minY, double minZ, double maxX, double maxY,
		double maxZ, int color, List<Segment> out) {
		// bottom rectangle (y=min)
		out.add(new Segment(minX, minY, minZ, maxX, minY, minZ, color));
		out.add(new Segment(maxX, minY, minZ, maxX, minY, maxZ, color));
		out.add(new Segment(maxX, minY, maxZ, minX, minY, maxZ, color));
		out.add(new Segment(minX, minY, maxZ, minX, minY, minZ, color));
		// top rectangle (y=max)
		out.add(new Segment(minX, maxY, minZ, maxX, maxY, minZ, color));
		out.add(new Segment(maxX, maxY, minZ, maxX, maxY, maxZ, color));
		out.add(new Segment(maxX, maxY, maxZ, minX, maxY, maxZ, color));
		out.add(new Segment(minX, maxY, maxZ, minX, maxY, minZ, color));
		// verticals
		out.add(new Segment(minX, minY, minZ, minX, maxY, minZ, color));
		out.add(new Segment(maxX, minY, minZ, maxX, maxY, minZ, color));
		out.add(new Segment(maxX, minY, maxZ, maxX, maxY, maxZ, color));
		out.add(new Segment(minX, minY, maxZ, minX, maxY, maxZ, color));
	}

	/**
	 * Feeds built segments to the vanilla gizmo renderer. Must run inside a gizmo collection
	 * window (see WorldEditCui's BEFORE_GIZMOS hook) or {@link Gizmos} refuses the submission.
	 */
	static void submit(List<Segment> segments, float lineWidth) {
		for (Segment segment : segments) {
			Gizmos.line(new Vec3(segment.x1(), segment.y1(), segment.z1()),
				new Vec3(segment.x2(), segment.y2(), segment.z2()), segment.color(), lineWidth);
		}
	}

	/** Line colours for one frame, already opacity-multiplied ARGB ints. */
	public record Palette(int grid, int minCorner, int maxCorner) {
	}
}
