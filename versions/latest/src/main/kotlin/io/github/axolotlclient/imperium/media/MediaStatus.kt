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
import kotlin.math.min

/**
 * A snapshot of what a media provider says is playing (W5-B now-playing HUD). Positions are
 * carried with the wall-clock fetch time so the HUD can interpolate a smooth progress bar
 * between 5-second polls without its own playback clock.
 */
class MediaStatus(
	track: String?,
	artist: String?,
	playing: Boolean,
	positionMs: Long,
	lengthMs: Long,
	fetchedAtEpochMs: Long
) {

	private val track: String = track?.trim() ?: ""
	private val artist: String = artist?.trim() ?: ""
	private val playing: Boolean = playing
	private val positionMs: Long = max(0, positionMs)
	private val lengthMs: Long = max(0, lengthMs)
	private val fetchedAtEpochMs: Long = fetchedAtEpochMs

	fun track(): String = track

	fun artist(): String = artist

	fun playing(): Boolean = playing

	fun positionMs(): Long = positionMs

	fun lengthMs(): Long = lengthMs

	fun fetchedAtEpochMs(): Long = fetchedAtEpochMs

	/** A snapshot stamped "now" — the shape providers produce and tests control explicitly. */
	companion object {
		@JvmStatic
		fun now(track: String?, artist: String?, playing: Boolean, positionMs: Long, lengthMs: Long): MediaStatus =
			MediaStatus(track, artist, playing, positionMs, lengthMs, System.currentTimeMillis())
	}

	/** True when this status would draw pixels: a track name and a non-negative length. */
	fun playable(): Boolean = track.isNotEmpty() && lengthMs > 0

	/** "track — artist" (em dash), or just the track when the artist is unknown. */
	fun titleLine(): String = if (artist.isEmpty()) track else "$track — $artist"

	/**
	 * The playback position at `nowMs`: advanced from the fetch-time position while
	 * playing, clamped to the track length, never negative.
	 */
	fun progressMs(nowMs: Long): Long {
		var position = positionMs
		if (playing) {
			position += max(0, nowMs - fetchedAtEpochMs)
		}
		if (lengthMs > 0) {
			position = min(position, lengthMs)
		}
		return max(0, position)
	}

	/** Progress 0..1 at `nowMs` for bar rendering; 0 when there is no length. */
	fun fraction(nowMs: Long): Float =
		if (lengthMs <= 0) 0f else (progressMs(nowMs) / lengthMs.toFloat()).coerceIn(0f, 1f)
}
