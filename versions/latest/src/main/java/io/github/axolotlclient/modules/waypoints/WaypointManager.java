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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;
import lombok.Getter;

/**
 * Owns the waypoint list and its persistence (Alpine "waypoints" module, parity spec M2).
 *
 * <p>There is no list-shaped option type in AxolotlClientConfig (StringArrayOption is a
 * closed picker, not a dynamic list), so the list is serialized to a JSON array string and
 * stored in a {@link StringOption} that the HUD entry adds to its save options. That keeps
 * the data inside the main module config, which makes it profile-aware for free: profile
 * switches reload the config and {@code postConfigLoad} re-parses the option.</p>
 */
public final class WaypointManager {

	@Getter
	private static final WaypointManager instance = new WaypointManager();

	/** Color rotation for manually added waypoints (Alpine-like spread). */
	private static final int[] PALETTE = {
		0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFFAA00, 0xFFAA55FF, 0xFF55AAFF, 0xFFFF55AA, 0xFFAAFF55
	};

	/**
	 * The persisted blob. Changes through the config UI text field re-parse the list via
	 * the change listener; {@code fromSerializedValue} (config load) does not fire it, which
	 * is why the HUD entry also re-parses in {@code postConfigLoad}.
	 */
	private final StringOption data = new StringOption("waypoints_data", "[]", s -> loadFromOption());

	@Getter
	private final List<Waypoint> waypoints = new CopyOnWriteArrayList<>();

	private WaypointManager() {
	}

	public StringOption getDataOption() {
		return data;
	}

	/** Re-parses the waypoint list from the persisted option value. */
	public void loadFromOption() {
		List<Waypoint> parsed = new ArrayList<>();
		try {
			JsonArray array = JsonParser.parseString(data.get() == null ? "[]" : data.get()).getAsJsonArray();
			array.forEach(element -> {
				if (element.isJsonObject()) {
					parsed.add(Waypoint.fromJson(element.getAsJsonObject()));
				}
			});
		} catch (IllegalStateException | JsonParseException e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to parse waypoints, resetting list!", e);
		}
		waypoints.clear();
		waypoints.addAll(parsed);
	}

	public void save() {
		JsonArray array = new JsonArray();
		waypoints.forEach(wp -> array.add(wp.toJson()));
		data.set(array.toString());
		AxolotlClientCommon.getInstance().saveConfig();
	}

	public void add(Waypoint waypoint) {
		waypoints.add(waypoint);
		save();
	}

	public void remove(Waypoint waypoint) {
		waypoints.remove(waypoint);
		save();
	}

	public void replace(Waypoint oldWaypoint, Waypoint newWaypoint) {
		int index = waypoints.indexOf(oldWaypoint);
		if (index >= 0) {
			waypoints.set(index, newWaypoint);
			save();
		}
	}

	/** Replaces any existing death point in that dimension with a fresh one. */
	public void setDeathPoint(double x, double y, double z, String dimension) {
		waypoints.removeIf(wp -> wp.death() && wp.dimension().equals(dimension));
		waypoints.add(0, new Waypoint(Waypoint.DEATH_NAME, x, y, z, dimension, Waypoint.DEATH_COLOR, true, true));
		save();
	}

	public List<Waypoint> getForDimension(String dimension) {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint wp : waypoints) {
			if (wp.dimension().equals(dimension)) {
				out.add(wp);
			}
		}
		return out;
	}

	public int nextColor() {
		return PALETTE[waypoints.size() % PALETTE.length];
	}
}
