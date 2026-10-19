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

package io.github.axolotlclient.modules.waypoints;

import com.google.gson.JsonObject;

/**
 * A single named world location (Alpine "waypoints" module, parity spec M2).
 *
 * @param name      display name
 * @param x         block-space x
 * @param y         block-space y
 * @param z         block-space z
 * @param dimension dimension identifier, e.g. {@code minecraft:the_nether}
 * @param color     ARGB label color
 * @param enabled   whether the HUD renders this waypoint
 * @param death     whether this is the auto-created death point (at most one per dimension)
 */
public record Waypoint(String name, double x, double y, double z, String dimension, int color, boolean enabled,
					   boolean death) {

	public static final String DEATH_NAME = "Death Point";
	public static final int DEATH_COLOR = 0xFFFF5555;

	public Waypoint withEnabled(boolean newEnabled) {
		return new Waypoint(name, x, y, z, dimension, color, newEnabled, death);
	}

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("name", name);
		obj.addProperty("x", x);
		obj.addProperty("y", y);
		obj.addProperty("z", z);
		obj.addProperty("dimension", dimension);
		obj.addProperty("color", color);
		obj.addProperty("enabled", enabled);
		obj.addProperty("death", death);
		return obj;
	}

	public static Waypoint fromJson(JsonObject obj) {
		return new Waypoint(
			obj.has("name") ? obj.get("name").getAsString() : "waypoint",
			obj.has("x") ? obj.get("x").getAsDouble() : 0,
			obj.has("y") ? obj.get("y").getAsDouble() : 0,
			obj.has("z") ? obj.get("z").getAsDouble() : 0,
			obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld",
			obj.has("color") ? obj.get("color").getAsInt() : 0xFFFFFFFF,
			!obj.has("enabled") || obj.get("enabled").getAsBoolean(),
			obj.has("death") && obj.get("death").getAsBoolean());
	}
}
