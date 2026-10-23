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

package io.github.axolotlclient.modules.hud.gui.hud

import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud
import io.github.axolotlclient.modules.waypoints.WaypointManager
import net.minecraft.client.Minecraft
import kotlin.math.atan2

/**
 * Latest-side data feed for the common [CompassHud] waypoint markers (Alpine parity M2).
 *
 * `CompassHud` lives in common and cannot see the version-specific
 * `WaypointManager`, so this class converts every enabled waypoint of the
 * current dimension into a [CompassHud.CompassMarker] (name, color, absolute
 * bearing) and registers the supplier via [CompassHud.setWaypointProvider].
 * Called once from the mod initializer.
 */
object CompassWaypointMarkers {

    @JvmStatic
    fun init() {
        CompassHud.setWaypointProvider(java.util.function.Supplier { collect() })
    }

    private fun collect(): List<CompassHud.CompassMarker> {
        val player = Minecraft.getInstance().player ?: return emptyList()
        val dimension = player.level().dimension().identifier().toString()
        val px = player.x
        val pz = player.z
        val markers = ArrayList<CompassHud.CompassMarker>()
        for (wp in WaypointManager.getInstance().getForDimension(dimension)) {
            if (!wp.enabled) {
                continue
            }
            val dx = wp.x - px
            val dz = wp.z - pz
            // MC yaw toward the waypoint (0 = +Z), then the compass transform used by
            // CompassHud: bearing = (yaw + 180) % 360 with N = 0 and E = 90.
            val bearing = (((Math.toDegrees(atan2(-dx, dz)) + 180) % 360 + 360) % 360).toFloat()
            markers.add(CompassHud.CompassMarker(wp.name, wp.color, bearing))
        }
        return markers
    }
}
