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

import com.google.gson.JsonObject

/**
 * A single named world location (Alpine "waypoints" module, parity spec M2).
 */
@JvmRecord
data class Waypoint(
    val name: String, val x: Double, val y: Double, val z: Double, val dimension: String, val color: Int,
    val enabled: Boolean, val death: Boolean
) {
    fun withEnabled(newEnabled: Boolean): Waypoint = Waypoint(name, x, y, z, dimension, color, newEnabled, death)

    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("name", name)
        obj.addProperty("x", x)
        obj.addProperty("y", y)
        obj.addProperty("z", z)
        obj.addProperty("dimension", dimension)
        obj.addProperty("color", color)
        obj.addProperty("enabled", enabled)
        obj.addProperty("death", death)
        return obj
    }

    companion object {
        const val DEATH_NAME: String = "Death Point"
        const val DEATH_COLOR: Int = 0xFFFF5555.toInt()

        @JvmStatic
        fun fromJson(obj: JsonObject): Waypoint = Waypoint(
            if (obj.has("name")) obj.get("name").asString else "waypoint",
            if (obj.has("x")) obj.get("x").asDouble else 0.0,
            if (obj.has("y")) obj.get("y").asDouble else 0.0,
            if (obj.has("z")) obj.get("z").asDouble else 0.0,
            if (obj.has("dimension")) obj.get("dimension").asString else "minecraft:overworld",
            if (obj.has("color")) obj.get("color").asInt else 0xFFFFFFFF.toInt(),
            !obj.has("enabled") || obj.get("enabled").asBoolean,
            obj.has("death") && obj.get("death").asBoolean
        )
    }
}
