/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.waypoints

import java.util.concurrent.CopyOnWriteArrayList

import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import kotlin.math.max

/**
 * Owns the waypoint list and its persistence (Alpine "waypoints" module, parity spec M2).
 */
class WaypointManager private constructor() {
    private val data = StringOption("waypoints_data", "[]") { loadFromOption() }

    val waypoints: MutableList<Waypoint> = CopyOnWriteArrayList()

    fun getDataOption(): StringOption = data

    /** Re-parses the waypoint list from the persisted option value. */
    fun loadFromOption() {
        val parsed: MutableList<Waypoint> = ArrayList()
        try {
            val array = JsonParser.parseString(data.get() ?: "[]").asJsonArray
            array.forEach { element ->
                if (element.isJsonObject) {
                    parsed.add(Waypoint.fromJson(element.asJsonObject))
                }
            }
        } catch (e: IllegalStateException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to parse waypoints, resetting list!", e)
        } catch (e: JsonParseException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to parse waypoints, resetting list!", e)
        }
        waypoints.clear()
        waypoints.addAll(parsed)
    }

    fun save() {
        val array = JsonArray()
        waypoints.forEach { wp -> array.add(wp.toJson()) }
        data.set(array.toString())
        AxolotlClientCommon.getInstance().saveConfig()
    }

    fun add(waypoint: Waypoint) {
        waypoints.add(waypoint)
        save()
    }

    fun remove(waypoint: Waypoint) {
        waypoints.remove(waypoint)
        save()
    }

    fun replace(oldWaypoint: Waypoint, newWaypoint: Waypoint) {
        val index = waypoints.indexOf(oldWaypoint)
        if (index >= 0) {
            waypoints[index] = newWaypoint
            save()
        }
    }

    /**
     * Records a death point (Alpine parity M6).
     */
    fun setDeathPoint(x: Double, y: Double, z: Double, dimension: String, color: Int, limit: Boolean, max: Int) {
        var existing = waypoints.stream().filter { wp -> wp.death && wp.dimension == dimension }.count()
        if (limit) {
            // Evict oldest beyond max-1 so the new point fits; max<=1 reproduces the
            // classic single-slot replace.
            while (existing >= max(1, max.toLong())) {
                waypoints.stream()
                    .filter { wp -> wp.death && wp.dimension == dimension }
                    .reduce { _, second -> second }
                    .ifPresent { wp -> waypoints.remove(wp) }
                existing--
            }
        }
        val name = if (existing > 0) Waypoint.DEATH_NAME + " " + (existing + 1) else Waypoint.DEATH_NAME
        waypoints.add(0, Waypoint(name, x, y, z, dimension, color, true, true))
        save()
    }

    fun getForDimension(dimension: String): List<Waypoint> {
        val out: MutableList<Waypoint> = ArrayList()
        for (wp in waypoints) {
            if (wp.dimension == dimension) {
                out.add(wp)
            }
        }
        return out
    }

    fun nextColor(): Int = PALETTE[waypoints.size % PALETTE.size]

    companion object {
        /** Color rotation for manually added waypoints (Alpine-like spread). */
        private val PALETTE = intArrayOf(
            0xFF55FF55.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFAA00.toInt(),
            0xFFAA55FF.toInt(), 0xFF55AAFF.toInt(), 0xFFFF55AA.toInt(), 0xFFAAFF55.toInt()
        )

        private val instance = WaypointManager()

        @JvmStatic
        fun getInstance(): WaypointManager = instance
    }
}
