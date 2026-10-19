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

package io.github.axolotlclient.imperium.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Pure JSON-to-{@link MediaStatus} parsing for the Spotify desktop local status document
 * (W5-B). The endpoint is undocumented, so this reads defensively and returns {@code null}
 * for anything it cannot make sense of — a shape change hides the HUD, it never crashes.
 *
 * <p>Accepted shapes (both seen in the wild across desktop versions):</p>
 * <ul>
 *   <li>classic local API: {@code {"playing":true,"playing_position":42.5,
 *       "track":{"track_resource":{"name":"..."},"artist_resource":{"name":"..."},
 *       "length":213}}}</li>
 *   <li>flat-ish variant: {@code track.name} with {@code track.artists[0].name} and
 *       {@code position_ms} / {@code duration_ms} style fields.</li>
 * </ul>
 */
public final class SpotifyLocalStatus {

	private SpotifyLocalStatus() {
	}

	/**
	 * Parses one status document, or {@code null} when it carries no usable track (idle
	 * Spotify, an advert without metadata, a shape change, or garbage).
	 */
	@Nullable
	public static MediaStatus parse(JsonElement document, long fetchedAtEpochMs) {
		if (document == null || !document.isJsonObject()) {
			return null;
		}
		JsonObject root = document.getAsJsonObject();
		JsonObject track = object(root, "track");
		if (track == null) {
			return null;
		}
		String name = firstNonBlank(
			name(object(track, "track_resource")),
			string(track, "name"),
			string(root, "name"));
		if (name == null) {
			return null;
		}
		String artist = firstNonBlank(
			name(object(track, "artist_resource")),
			string(track, "artist"),
			artists(track),
			artists(root));
		boolean playing = bool(root, "playing", true);
		long positionMs = roundToMillis(firstNumber(root, "playing_position", "position_s"));
		long lengthMs = roundToMillis(firstNumber(track, "length", "duration_s"));
		if (lengthMs <= 0 || positionMs <= 0) {
			// *_ms variant (already milliseconds — no scaling)
			positionMs = Math.max(positionMs, (long) firstNumber(root, "position_ms"));
			lengthMs = Math.max(lengthMs, (long) firstNumber(root, "duration_ms"));
		}
		return new MediaStatus(name, artist == null ? "" : artist, playing, positionMs, lengthMs, fetchedAtEpochMs);
	}

	@Nullable
	private static JsonObject object(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	@Nullable
	private static String string(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	@Nullable
	private static String name(@Nullable JsonObject resource) {
		return resource == null ? null : string(resource, "name");
	}

	@Nullable
	private static String artists(JsonObject parent) {
		JsonElement array = parent.get("artists");
		if (array == null || !array.isJsonArray() || array.getAsJsonArray().isEmpty()) {
			return null;
		}
		JsonElement first = array.getAsJsonArray().get(0);
		if (first.isJsonObject()) {
			return name(first.getAsJsonObject());
		}
		return first.isJsonPrimitive() ? first.getAsString() : null;
	}

	private static boolean bool(JsonObject parent, String key, boolean fallback) {
		JsonElement element = parent.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
	}

	private static double firstNumber(JsonObject parent, String... keys) {
		for (String key : keys) {
			if (key == null || key.isEmpty()) continue;
			JsonElement element = parent.get(key);
			if (element != null && element.isJsonPrimitive()) {
				try {
					return element.getAsDouble();
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	@Nullable
	private static String firstNonBlank(@Nullable String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static long roundToMillis(double secondsOrMillis) {
		return secondsOrMillis <= 0 ? 0 : Math.round(secondsOrMillis * 1000);
	}
}
