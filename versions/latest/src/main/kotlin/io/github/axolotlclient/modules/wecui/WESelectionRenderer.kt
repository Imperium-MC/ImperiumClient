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

package io.github.axolotlclient.modules.wecui

import io.github.axolotlclient.modules.wecui.WESelectionHolder.Shape
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a [WESelectionHolder] into world-space line geometry, the classic WorldEdit CUI
 * look: the bounding-box wireframe, the per-block grid on the three faces touching the
 * minimum corner, and the two corner markers in their own colours.
 *
 * Split in two halves on purpose: [buildSegments] is pure geometry (plain doubles,
 * no Minecraft state) so the gametest can assert the exact lines a given selection produces;
 * [submit] then feeds those segments to the vanilla gizmo renderer. Rendering goes
 * through `Gizmos.line`: gizmo lines with alpha below 255 land in the translucent line
 * batch (`linesTranslucentNoDepthWrite`) — depth-tested against terrain but never
 * occluding, which is how the classic client behaved too.
 *
 * Grid safety valve: a selection axis longer than [MAX_GRID_LINES] blocks stops
 * emitting interior grid lines on the faces spanning that axis (the wireframe stays); a
 * 1000-block selection would otherwise cost thousands of line primitives per frame.
 */
object WESelectionRenderer {

	/** Interior grid lines per axis before the grid degrades to the wireframe on that axis. */
	const val MAX_GRID_LINES = 256

	/** Corner markers are the corner block's outline inflated by this, to avoid z-fighting. */
	private const val CORNER_INFLATE = 0.02

	/** Segments used to approximate each ellipsoid great circle. */
	private const val ELLIPSE_SEGMENTS = 32

	/** Line colours for one frame, already opacity-multiplied ARGB ints. */
	@JvmRecord
	data class Palette(val grid: Int, val minCorner: Int, val maxCorner: Int)

	@JvmRecord
	data class Segment(
		val x1: Double, val y1: Double, val z1: Double,
		val x2: Double, val y2: Double, val z2: Double, val color: Int
	)

	/**
	 * Pure geometry: every line segment of the current selection, in world coordinates.
	 * The caller decides colours (already opacity-multiplied ARGB) via the palette.
	 */
	@JvmStatic
	fun buildSegments(selection: WESelectionHolder, palette: Palette, showGrid: Boolean): List<Segment> {
		val segments = ArrayList<Segment>()
		if (!selection.hasSelection()) {
			return segments
		}
		when (selection.shape) {
			Shape.CUBOID -> buildCuboid(selection, palette, showGrid, segments)
			Shape.ELLIPSOID -> buildEllipsoid(selection, palette, showGrid, segments)
			// Parsed but not rendered (yet); classic clients render these, we draw nothing.
			Shape.POLYGON2D, Shape.CYLINDER, null -> {}
		}
		return segments
	}

	private fun buildCuboid(selection: WESelectionHolder, palette: Palette, showGrid: Boolean, out: MutableList<Segment>) {
		val p0 = selection.point0
		val p1 = selection.point1
		if (p0 != null) {
			boxSegments(
				p0[0] - CORNER_INFLATE, p0[1] - CORNER_INFLATE, p0[2] - CORNER_INFLATE,
				p0[0] + 1 + CORNER_INFLATE, p0[1] + 1 + CORNER_INFLATE, p0[2] + 1 + CORNER_INFLATE,
				palette.minCorner, out
			)
		}
		if (p1 != null) {
			boxSegments(
				p1[0] - CORNER_INFLATE, p1[1] - CORNER_INFLATE, p1[2] - CORNER_INFLATE,
				p1[0] + 1 + CORNER_INFLATE, p1[1] + 1 + CORNER_INFLATE, p1[2] + 1 + CORNER_INFLATE,
				palette.maxCorner, out
			)
		}
		if (p0 == null || p1 == null) {
			return // incomplete selection: corner markers only
		}
		val minX = minOf(p0[0], p1[0])
		val minY = minOf(p0[1], p1[1])
		val minZ = minOf(p0[2], p1[2])
		// Block coordinates are inclusive: the box extends one block past the max corner.
		val maxX = maxOf(p0[0], p1[0]) + 1
		val maxY = maxOf(p0[1], p1[1]) + 1
		val maxZ = maxOf(p0[2], p1[2]) + 1
		boxSegments(minX, minY, minZ, maxX, maxY, maxZ, palette.grid, out)
		if (showGrid) {
			minFaceGrid(minX, minY, minZ, maxX, maxY, maxZ, palette.grid, out)
		}
	}

	/**
	 * The classic WE grid: lines every block on the three faces that touch the minimum
	 * corner (x=minX, y=minY, z=minZ). Edge positions themselves are the box wireframe,
	 * already emitted, so interior offsets start at +1 and stop before the far edge.
	 */
	private fun minFaceGrid(
		minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double,
		maxZ: Double, color: Int, out: MutableList<Segment>
	) {
		var x = minX + 1
		while (x < maxX - 0.5 && x <= minX + MAX_GRID_LINES) {
			out.add(Segment(x, minY, minZ, x, minY, maxZ, color)) // y=minY face, along z
			out.add(Segment(x, minY, minZ, x, maxY, minZ, color)) // z=minZ face, along y
			x++
		}
		var y = minY + 1
		while (y < maxY - 0.5 && y <= minY + MAX_GRID_LINES) {
			out.add(Segment(minX, y, minZ, minX, y, maxZ, color)) // x=minX face, along z
			out.add(Segment(minX, y, minZ, maxX, y, minZ, color)) // z=minZ face, along x
			y++
		}
		var z = minZ + 1
		while (z < maxZ - 0.5 && z <= minZ + MAX_GRID_LINES) {
			out.add(Segment(minX, minY, z, minX, maxY, z, color)) // x=minX face, along y
			out.add(Segment(minX, minY, z, maxX, minY, z, color)) // y=minY face, along x
			z++
		}
	}

	private fun buildEllipsoid(selection: WESelectionHolder, palette: Palette, showGrid: Boolean, out: MutableList<Segment>) {
		val center = selection.point0
		val radii = selection.point1
		if (center != null) {
			boxSegments(
				center[0] - CORNER_INFLATE, center[1] - CORNER_INFLATE, center[2] - CORNER_INFLATE,
				center[0] + 1 + CORNER_INFLATE, center[1] + 1 + CORNER_INFLATE, center[2] + 1 + CORNER_INFLATE,
				palette.minCorner, out
			)
		}
		if (center == null || radii == null) {
			return // center marker only until the server sends radii
		}
		val cx = center[0] + 0.5
		val cy = center[1] + 0.5
		val cz = center[2] + 0.5
		val rx = abs(radii[0])
		val ry = abs(radii[1])
		val rz = abs(radii[2])
		// Bounding box around the full ellipsoid volume.
		boxSegments(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz, palette.grid, out)
		if (showGrid) {
			ellipse(cx, cy, cz, rx, ry, 0.0, palette.grid, out)
			ellipse(cx, cy, cz, rx, 0.0, rz, palette.grid, out)
			ellipse(cx, cy, cz, 0.0, ry, rz, palette.grid, out)
		}
	}

	/** One great-circle ellipse in the plane given by the two non-zero radii, as a polyline. */
	private fun ellipse(
		cx: Double, cy: Double, cz: Double, rx: Double, ry: Double, rz: Double,
		color: Int, out: MutableList<Segment>
	) {
		var px = 0.0
		var py = 0.0
		var pz = 0.0
		for (i in 0..ELLIPSE_SEGMENTS) {
			val angle = Math.TAU * i / ELLIPSE_SEGMENTS
			val x = rx * cos(angle)
			val y = ry * sin(angle)
			val z = rz * sin(angle)
			if (i > 0) {
				out.add(Segment(cx + px, cy + py, cz + pz, cx + x, cy + y, cz + z, color))
			}
			px = x
			py = y
			pz = z
		}
	}

	/** The 12 edges of an axis-aligned box. */
	private fun boxSegments(
		minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double,
		maxZ: Double, color: Int, out: MutableList<Segment>
	) {
		// bottom rectangle (y=min)
		out.add(Segment(minX, minY, minZ, maxX, minY, minZ, color))
		out.add(Segment(maxX, minY, minZ, maxX, minY, maxZ, color))
		out.add(Segment(maxX, minY, maxZ, minX, minY, maxZ, color))
		out.add(Segment(minX, minY, maxZ, minX, minY, minZ, color))
		// top rectangle (y=max)
		out.add(Segment(minX, maxY, minZ, maxX, maxY, minZ, color))
		out.add(Segment(maxX, maxY, minZ, maxX, maxY, maxZ, color))
		out.add(Segment(maxX, maxY, maxZ, minX, maxY, maxZ, color))
		out.add(Segment(minX, maxY, maxZ, minX, maxY, minZ, color))
		// verticals
		out.add(Segment(minX, minY, minZ, minX, maxY, minZ, color))
		out.add(Segment(maxX, minY, minZ, maxX, maxY, minZ, color))
		out.add(Segment(maxX, minY, maxZ, maxX, maxY, maxZ, color))
		out.add(Segment(minX, minY, maxZ, minX, maxY, maxZ, color))
	}

	/**
	 * Feeds built segments to the vanilla gizmo renderer. Must run inside a gizmo collection
	 * window (see WorldEditCui's BEFORE_GIZMOS hook) or [Gizmos] refuses the submission.
	 */
	internal fun submit(segments: List<Segment>, lineWidth: Float) {
		for (segment in segments) {
			Gizmos.line(
				Vec3(segment.x1, segment.y1, segment.z1),
				Vec3(segment.x2, segment.y2, segment.z2), segment.color, lineWidth
			)
		}
	}
}
