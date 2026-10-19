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

/**
 * A snapshot of what a media provider says is playing (W5-B now-playing HUD). Positions are
 * carried with the wall-clock fetch time so the HUD can interpolate a smooth progress bar
 * between 5-second polls without its own playback clock.
 */
public record MediaStatus(String track, String artist, boolean playing, long positionMs, long lengthMs, long fetchedAtEpochMs) {

	public MediaStatus {
		track = track == null ? "" : track.trim();
		artist = artist == null ? "" : artist.trim();
		positionMs = Math.max(0, positionMs);
		lengthMs = Math.max(0, lengthMs);
	}

	/** A snapshot stamped "now" — the shape providers produce and tests control explicitly. */
	public static MediaStatus now(String track, String artist, boolean playing, long positionMs, long lengthMs) {
		return new MediaStatus(track, artist, playing, positionMs, lengthMs, System.currentTimeMillis());
	}

	/** True when this status would draw pixels: a track name and a non-negative length. */
	public boolean playable() {
		return !track.isEmpty() && lengthMs > 0;
	}

	/** "track — artist" (em dash), or just the track when the artist is unknown. */
	public String titleLine() {
		return artist.isEmpty() ? track : track + " — " + artist;
	}

	/**
	 * The playback position at {@code nowMs}: advanced from the fetch-time position while
	 * playing, clamped to the track length, never negative.
	 */
	public long progressMs(long nowMs) {
		long position = positionMs;
		if (playing) {
			position += Math.max(0, nowMs - fetchedAtEpochMs);
		}
		if (lengthMs > 0) {
			position = Math.min(position, lengthMs);
		}
		return Math.max(0, position);
	}

	/** Progress 0..1 at {@code nowMs} for bar rendering; 0 when there is no length. */
	public float fraction(long nowMs) {
		return lengthMs <= 0 ? 0 : Math.clamp(progressMs(nowMs) / (float) lengthMs, 0, 1);
	}
}
