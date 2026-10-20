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

/**
 * The media shell (W5-B), same pattern as the boosts: the now-playing HUD reads this registry
 * and nothing else. Whatever provider is active — today the best-effort Spotify desktop poller
 * ([SpotifyLocalPoller]), tomorrow an OS media session bridge — writes its snapshot here
 * with [update] and the HUD starts drawing; [clear] hides it again.
 *
 * One volatile slot, not a map: there is exactly one "now playing" per client. Writers are
 * background threads, the reader is the render thread, and [MediaStatus] is immutable,
 * so the volatile publish is the whole story.
 */
object MediaStatusRegistry {

	@Volatile
	private var current: MediaStatus? = null

	/** Publishes a snapshot; `null` or unplayable snapshots hide the HUD. */
	@JvmStatic
	fun update(status: MediaStatus?) {
		current = if (status != null && status.playable()) status else null
	}

	/** Hides the HUD (provider error, playback stopped, provider shut down). */
	@JvmStatic
	fun clear() {
		current = null
	}

	/** The latest playable snapshot, or `null` when nothing should draw. */
	@JvmStatic
	fun current(): MediaStatus? = current
}
