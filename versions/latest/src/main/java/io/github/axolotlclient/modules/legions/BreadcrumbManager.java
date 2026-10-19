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
 * Owns the breadcrumb list and its persistence (Alpine "factions breadcrumbs" parity,
 * reimplemented for Legions). Follows the WaypointManager serialization pattern — the
 * waypoints files themselves are NOT touched: this is an independent blob in its own
 * {@code breadcrumbs_data} option inside the module config, which keeps it profile-aware
 * for free (profile switches reload the config and {@code postConfigLoad} re-parses).
 *
 * <p>Expiry is lazy: accessors prune against {@code maxAgeMs}, so crumbs vanish at the
 * right time even when the HUD entry is hidden and nothing ticks. Pruning persists on the
 * next structural change (drop / clear), not on every read.</p>
 */
public final class BreadcrumbManager {

	/** Auto-expire choices in minutes; OFF keeps crumbs until cleared manually. */
	public enum Expiry {
		OFF(0), MINUTES_5(5), MINUTES_15(15), MINUTES_60(60);

		public final long maxAgeMs;

		Expiry(int minutes) {
			this.maxAgeMs = minutes * 60_000L;
		}
	}

	@Getter
	private static final BreadcrumbManager instance = new BreadcrumbManager();

	/** The persisted blob; config-UI edits re-parse through the change listener. */
	private final StringOption data = new StringOption("breadcrumbs_data", "[]", s -> loadFromOption());

	@Getter
	private final List<Breadcrumb> breadcrumbs = new CopyOnWriteArrayList<>();

	private BreadcrumbManager() {
	}

	public StringOption getDataOption() {
		return data;
	}

	/** Re-parses the breadcrumb list from the persisted option value. */
	public void loadFromOption() {
		List<Breadcrumb> parsed = new ArrayList<>();
		try {
			JsonArray array = JsonParser.parseString(data.get() == null ? "[]" : data.get()).getAsJsonArray();
			array.forEach(element -> {
				if (element.isJsonObject()) {
					parsed.add(Breadcrumb.fromJson(element.getAsJsonObject()));
				}
			});
		} catch (IllegalStateException | JsonParseException e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to parse breadcrumbs, resetting list!", e);
		}
		breadcrumbs.clear();
		breadcrumbs.addAll(parsed);
	}

	public void save() {
		JsonArray array = new JsonArray();
		breadcrumbs.forEach(crumb -> array.add(crumb.toJson()));
		data.set(array.toString());
		AxolotlClientCommon.getInstance().saveConfig();
	}

	/** Drops a sequentially-named crumb at the given position and persists the list. */
	public Breadcrumb drop(double x, double y, double z, String dimension) {
		Breadcrumb crumb = new Breadcrumb(nextName(), x, y, z, dimension, System.currentTimeMillis());
		breadcrumbs.add(crumb);
		save();
		return crumb;
	}

	/** Names are the next free number, so relogging never restarts at 1. */
	private String nextName() {
		int max = 0;
		for (Breadcrumb crumb : breadcrumbs) {
			try {
				max = Math.max(max, Integer.parseInt(crumb.name()));
			} catch (NumberFormatException ignored) {
				// non-numeric names (future renames) don't participate in the sequence
			}
		}
		return Integer.toString(max + 1);
	}

	public void clearAll() {
		if (breadcrumbs.isEmpty()) {
			return;
		}
		breadcrumbs.clear();
		save();
	}

	/** Unexpired crumbs of one dimension; pruning side effect. */
	public List<Breadcrumb> getForDimension(String dimension, long maxAgeMs) {
		long now = System.currentTimeMillis();
		breadcrumbs.removeIf(crumb -> crumb.isExpired(now, maxAgeMs));
		List<Breadcrumb> out = new ArrayList<>();
		for (Breadcrumb crumb : breadcrumbs) {
			if (crumb.dimension().equals(dimension)) {
				out.add(crumb);
			}
		}
		return out;
	}
}
