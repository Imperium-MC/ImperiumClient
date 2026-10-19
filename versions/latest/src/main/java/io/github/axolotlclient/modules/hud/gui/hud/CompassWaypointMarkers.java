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

package io.github.axolotlclient.modules.hud.gui.hud;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.modules.waypoints.Waypoint;
import io.github.axolotlclient.modules.waypoints.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Latest-side data feed for the common {@link CompassHud} waypoint markers (Alpine parity M2).
 *
 * <p>{@code CompassHud} lives in common and cannot see the version-specific
 * {@code WaypointManager}, so this class converts every enabled waypoint of the
 * current dimension into a {@link CompassHud.CompassMarker} (name, color, absolute
 * bearing) and registers the supplier via {@link CompassHud#setWaypointProvider}.
 * Called once from the mod initializer.</p>
 */
public final class CompassWaypointMarkers {

	private CompassWaypointMarkers() {
	}

	public static void init() {
		CompassHud.setWaypointProvider(CompassWaypointMarkers::collect);
	}

	private static List<CompassHud.CompassMarker> collect() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		String dimension = player.level().dimension().identifier().toString();
		double px = player.getX();
		double pz = player.getZ();
		List<CompassHud.CompassMarker> markers = new ArrayList<>();
		for (Waypoint wp : WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled()) {
				continue;
			}
			double dx = wp.x() - px;
			double dz = wp.z() - pz;
			// MC yaw toward the waypoint (0 = +Z), then the compass transform used by
			// CompassHud: bearing = (yaw + 180) % 360 with N = 0 and E = 90.
			float bearing = (float) (((Math.toDegrees(Math.atan2(-dx, dz)) + 180) % 360 + 360) % 360);
			markers.add(new CompassHud.CompassMarker(wp.name(), wp.color(), bearing));
		}
		return markers;
	}
}
