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

package io.github.axolotlclient.modules.legions

import com.google.gson.JsonObject

/**
 * A dropped path marker (Alpine "factions breadcrumbs" parity, reimplemented for Legions).
 */
@JvmRecord
data class Breadcrumb(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String,
    val droppedAtEpochMs: Long
) {
    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("name", name)
        obj.addProperty("x", x)
        obj.addProperty("y", y)
        obj.addProperty("z", z)
        obj.addProperty("dimension", dimension)
        obj.addProperty("droppedAt", droppedAtEpochMs)
        return obj
    }

    /** True when this crumb is older than [maxAgeMs] (0 = never expires). */
    fun isExpired(nowMs: Long, maxAgeMs: Long): Boolean =
        maxAgeMs > 0 && nowMs - droppedAtEpochMs >= maxAgeMs

    companion object {
        @JvmStatic
        fun fromJson(obj: JsonObject): Breadcrumb = Breadcrumb(
            if (obj.has("name")) obj.get("name").asString else "?",
            if (obj.has("x")) obj.get("x").asDouble else 0.0,
            if (obj.has("y")) obj.get("y").asDouble else 0.0,
            if (obj.has("z")) obj.get("z").asDouble else 0.0,
            if (obj.has("dimension")) obj.get("dimension").asString else "minecraft:overworld",
            if (obj.has("droppedAt")) obj.get("droppedAt").asLong else System.currentTimeMillis()
        )
    }
}
