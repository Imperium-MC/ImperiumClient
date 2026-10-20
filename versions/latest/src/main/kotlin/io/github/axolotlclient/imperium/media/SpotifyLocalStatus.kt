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

package io.github.axolotlclient.imperium.media

import kotlin.math.max
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Pure JSON-to-[MediaStatus] parsing for the Spotify desktop local status document
 * (W5-B). The endpoint is undocumented, so this reads defensively and returns `null`
 * for anything it cannot make sense of — a shape change hides the HUD, it never crashes.
 *
 * Accepted shapes (both seen in the wild across desktop versions):
 *  - classic local API: `{"playing":true,"playing_position":42.5,
 *    "track":{"track_resource":{"name":"..."},"artist_resource":{"name":"..."},
 *    "length":213}}`
 *  - flat-ish variant: `track.name` with `track.artists[0].name` and
 *    `position_ms` / `duration_ms` style fields.
 */
object SpotifyLocalStatus {

	/**
	 * Parses one status document, or `null` when it carries no usable track (idle
	 * Spotify, an advert without metadata, a shape change, or garbage).
	 */
	@JvmStatic
	fun parse(document: JsonElement?, fetchedAtEpochMs: Long): MediaStatus? {
		if (document == null || !document.isJsonObject) {
			return null
		}
		val root = document.asJsonObject
		val track = root.obj("track") ?: return null
		val name = firstNonBlank(
			track.obj("track_resource").nameOf(),
			track.str("name"),
			root.str("name")
		) ?: return null
		val artist = firstNonBlank(
			track.obj("artist_resource").nameOf(),
			track.str("artist"),
			artists(track),
			artists(root)
		)
		val playing = root.bool("playing", true)
		var positionMs = roundToMillis(firstNumber(root, "playing_position", "position_s"))
		var lengthMs = roundToMillis(firstNumber(track, "length", "duration_s"))
		if (lengthMs <= 0 || positionMs <= 0) {
			// *_ms variant (already milliseconds — no scaling)
			positionMs = max(positionMs, firstNumber(root, "position_ms").toLong())
			lengthMs = max(lengthMs, firstNumber(root, "duration_ms").toLong())
		}
		return MediaStatus(name, artist ?: "", playing, positionMs, lengthMs, fetchedAtEpochMs)
	}

	private fun JsonObject.obj(key: String): JsonObject? {
		val element = get(key) ?: return null
		return if (element.isJsonObject) element.asJsonObject else null
	}

	private fun JsonObject.str(key: String): String? {
		val element = get(key) ?: return null
		return if (element.isJsonPrimitive) element.asString else null
	}

	private fun JsonObject?.nameOf(): String? = this?.str("name")

	private fun artists(parent: JsonObject): String? {
		val array = parent.get("artists") ?: return null
		if (!array.isJsonArray || array.asJsonArray.isEmpty) {
			return null
		}
		val first = array.asJsonArray[0]
		if (first.isJsonObject) {
			return first.asJsonObject.nameOf()
		}
		return if (first.isJsonPrimitive) first.asString else null
	}

	private fun JsonObject.bool(key: String, fallback: Boolean): Boolean {
		val element = get(key) ?: return fallback
		return if (element.isJsonPrimitive) element.asBoolean else fallback
	}

	private fun firstNumber(parent: JsonObject, vararg keys: String?): Double {
		for (key in keys) {
			if (key == null || key.isEmpty()) continue
			val element = parent.get(key) ?: continue
			if (element.isJsonPrimitive) {
				try {
					return element.asDouble
				} catch (ignored: NumberFormatException) {
				}
			}
		}
		return 0.0
	}

	private fun firstNonBlank(vararg values: String?): String? {
		for (value in values) {
			if (value != null && value.isNotBlank()) {
				return value
			}
		}
		return null
	}

	private fun roundToMillis(secondsOrMillis: Double): Long =
		if (secondsOrMillis <= 0) 0 else Math.round(secondsOrMillis * 1000)
}
