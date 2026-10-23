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

import io.github.axolotlclient.AxolotlClientCommon

/**
 * The current WorldEdit selection, as reported by the server over the `worldedit:cui`
 * plugin channel (see [WorldEditCui] for the wire format). Pure state: packets mutate
 * it, the renderer reads it, no Minecraft classes involved, so the parser is unit-testable
 * in the headless gametest harness.
 *
 * The holder tracks a *single* selection (the classic one-region model): a shape, the
 * world id the server named (informational — Bukkit world names need not match client dimension
 * ids, so it never vetoes rendering; the selection is cleared on client world change instead),
 * and the two defining points:
 *
 *  - cuboid — [point0]/[point1] are the two corners (`//pos1` / `//pos2`),
 *    either order; a point that has not been sent yet is `null` (an "incomplete" selection).
 *  - ellipsoid — [point0] is the center block, [point1] carries the radii (x=rx, y=ry, z=rz).
 *
 * Cleared on disconnect and on client world change; the server re-announces the selection
 * whenever it changes, so a stale box never survives a world switch.
 */
class WESelectionHolder private constructor() {

	enum class Shape(private val wireName: String) {
		CUBOID("cuboid"),
		ELLIPSOID("ellipsoid"),

		/** Parsed and stored (points via `p2`), but not rendered yet. */
		POLYGON2D("polygon2d"),

		/** Parsed and stored, but not rendered yet. */
		CYLINDER("cylinder");

		companion object {
			fun byWireName(name: String): Shape? = entries.firstOrNull { it.wireName == name }
		}
	}

	/** World id as named by the server's CUI events; may be null when the server omits it. */
	var worldId: String? = null
		private set

	/** First corner (cuboid) or center (ellipsoid); null until the server sends it. */
	var point0: DoubleArray? = null
		private set

	/** Second corner (cuboid) or radii rx/ry/rz (ellipsoid); null until the server sends it. */
	var point1: DoubleArray? = null
		private set

	/** Polygon2d outline points (`x, z` pairs) and their y bounds from `mm`. */
	val polygonPoints: MutableList<DoubleArray> = ArrayList()

	var polygonMinY: Double = 0.0
		private set
	var polygonMaxY: Double = 0.0
		private set

	var shape: Shape? = null
		private set

	val hasPolygonBounds: Boolean
		get() = polygonBoundsSet

	private var polygonBoundsSet = false

	fun hasSelection(): Boolean = shape != null

	fun clear() {
		shape = null
		worldId = null
		point0 = null
		point1 = null
		polygonPoints.clear()
		polygonBoundsSet = false
	}

	/** True when both defining points are known for the current shape. */
	val isComplete: Boolean
		get() = if (shape == Shape.CUBOID || shape == Shape.ELLIPSOID) {
			point0 != null && point1 != null
		} else {
			polygonPoints.size >= 2 && polygonBoundsSet
		}

	/**
	 * Applies one decoded CUI message to the selection. Tolerant by design: unknown event
	 * types, malformed numbers and wrong arities are logged at debug level and skipped —
	 * a bad packet must never throw on the network thread or break later messages.
	 *
	 * @param multi  the message carried the `+` multi-selection prefix (we render the
	 *               last-announced region only)
	 * @param type   event id (`s`, `p`, `p2`, `e`, `mm`, ...)
	 * @param args   everything after the event id
	 */
	fun handle(multi: Boolean, type: String, args: List<String>) {
		when (type) {
			"s" -> handleSelection(args)
			"p" -> handlePoint3d(args)
			"p2" -> handlePoint2d(args)
			"e" -> handleEllipsoid(args)
			"mm" -> handleMinMax(args)
			// "u" (update tick), "col"/"grid" (colour hints) and future events: no state for us.
			else -> AxolotlClientCommon.getInstance().logger
				.debug("Ignoring worldedit:cui event '$type' with ${args.size} args")
		}
	}

	/** `s|<shape>` — the server starts a new selection; all prior points are dropped. */
	private fun handleSelection(args: List<String>) {
		if (args.isEmpty()) {
			clear()
			return
		}
		val parsed = Shape.byWireName(args.first().lowercase())
		if (parsed == null) {
			// Unknown shape id: classic clients clear the selection here.
			AxolotlClientCommon.getInstance().logger
				.debug("worldedit:cui selection event with unknown shape '${args.first()}', clearing")
			clear()
			return
		}
		shape = parsed
		worldId = null
		point0 = null
		point1 = null
		polygonPoints.clear()
		polygonBoundsSet = false
	}

	/**
	 * `p|<id>|<x>|<y>|<z>|[area]` — a 3D selection point (id 0 = first, 1 = second).
	 * Also accepts the legacy one-shot cuboid line `p|world|minX|minY|minZ|maxX|maxY|maxZ|cuboid`.
	 */
	private fun handlePoint3d(args: List<String>) {
		if (args.size == 8) {
			val legacy = Shape.byWireName(args[7].lowercase())
			if (legacy != null) {
				// Legacy format: both corners and the shape in a single message.
				val min = triple(args, 1)
				val max = triple(args, 4)
				if (min != null && max != null) {
					shape = legacy
					worldId = args[0]
					point0 = min
					point1 = max
					polygonPoints.clear()
					polygonBoundsSet = false
				}
				return
			}
		}
		if (args.size < 4) {
			return
		}
		val id = integer(args[0])
		val pos = triple(args, 1)
		if (id < 0 || pos == null) {
			return
		}
		if (id == 0) {
			point0 = pos
		} else if (id == 1) {
			point1 = pos
		}
	}

	/**
	 * `p2|<id>|<x>|<z>|[area]` — a 2D outline point (polygon selections), plus the
	 * legacy 3-arg form `p2|blockX|blockY|blockZ` which sets the second cuboid corner.
	 */
	private fun handlePoint2d(args: List<String>) {
		if (args.size == 3) {
			val pos = triple(args, 0)
			if (pos != null) {
				point1 = pos
			}
			return
		}
		if (args.size < 3) {
			return
		}
		val x = number(args[1])
		val z = number(args[2])
		if (!x.isNaN() && !z.isNaN()) {
			polygonPoints.add(doubleArrayOf(x, z))
		}
	}

	/** `e|<id>|<x>|<y>|<z>` — id 0 sets the ellipsoid center, id 1 the radii. */
	private fun handleEllipsoid(args: List<String>) {
		if (args.size < 4) {
			return
		}
		val id = integer(args[0])
		val pos = triple(args, 1)
		if (pos == null) {
			return
		}
		if (id == 0) {
			point0 = pos
		} else if (id == 1) {
			point1 = pos
		}
	}

	/** `mm|<minY>|<maxY>` — the y bounds of a polygon selection. */
	private fun handleMinMax(args: List<String>) {
		if (args.size < 2) {
			return
		}
		val min = number(args[0])
		val max = number(args[1])
		if (min.isNaN() || max.isNaN()) {
			return
		}
		polygonMinY = min
		polygonMaxY = max + 1
		polygonBoundsSet = true
	}

	companion object {
		private val INSTANCE = WESelectionHolder()

		@JvmStatic
		fun getInstance(): WESelectionHolder = INSTANCE

		private fun triple(args: List<String>, offset: Int): DoubleArray? {
			val x = number(args[offset])
			val y = number(args[offset + 1])
			val z = number(args[offset + 2])
			if (x.isNaN() || y.isNaN() || z.isNaN()) {
				AxolotlClientCommon.getInstance().logger
					.debug("Dropping malformed worldedit:cui coordinate")
				return null
			}
			return doubleArrayOf(x, y, z)
		}

		private fun integer(s: String): Int =
			try {
				s.toInt()
			} catch (e: NumberFormatException) {
				-1
			}

		private fun number(s: String): Double =
			try {
				s.toDouble()
			} catch (e: NumberFormatException) {
				Double.NaN
			}
	}
}
