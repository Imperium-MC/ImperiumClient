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

package io.github.axolotlclient.modules.legions;

import com.google.gson.JsonObject;

/**
 * A dropped path marker (Alpine "factions breadcrumbs" parity, reimplemented for Legions).
 * Client-side only; unlike a waypoint it is transient by design — auto-expiry prunes it by
 * {@code droppedAtEpochMs} and the HUD shows its age.
 *
 * @param name              display name (sequential number assigned on drop)
 * @param x                 player x at drop time
 * @param y                 player y at drop time
 * @param z                 player z at drop time
 * @param dimension         dimension identifier, e.g. {@code minecraft:the_nether}
 * @param droppedAtEpochMs  wall-clock drop time, drives the age label and expiry
 */
public record Breadcrumb(String name, double x, double y, double z, String dimension, long droppedAtEpochMs) {

	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("name", name);
		obj.addProperty("x", x);
		obj.addProperty("y", y);
		obj.addProperty("z", z);
		obj.addProperty("dimension", dimension);
		obj.addProperty("droppedAt", droppedAtEpochMs);
		return obj;
	}

	public static Breadcrumb fromJson(JsonObject obj) {
		return new Breadcrumb(
			obj.has("name") ? obj.get("name").getAsString() : "?",
			obj.has("x") ? obj.get("x").getAsDouble() : 0,
			obj.has("y") ? obj.get("y").getAsDouble() : 0,
			obj.has("z") ? obj.get("z").getAsDouble() : 0,
			obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld",
			obj.has("droppedAt") ? obj.get("droppedAt").getAsLong() : System.currentTimeMillis());
	}

	/** True when this crumb is older than {@code maxAgeMs} (0 = never expires). */
	public boolean isExpired(long nowMs, long maxAgeMs) {
		return maxAgeMs > 0 && nowMs - droppedAtEpochMs >= maxAgeMs;
	}
}
