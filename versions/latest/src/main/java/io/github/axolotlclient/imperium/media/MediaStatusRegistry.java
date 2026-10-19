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

import org.jetbrains.annotations.Nullable;

/**
 * The media shell (W5-B), same pattern as the boosts: the now-playing HUD reads this registry
 * and nothing else. Whatever provider is active — today the best-effort Spotify desktop poller
 * ({@link SpotifyLocalPoller}), tomorrow an OS media session bridge — writes its snapshot here
 * with {@link #update} and the HUD starts drawing; {@link #clear} hides it again.
 *
 * <p>One volatile slot, not a map: there is exactly one "now playing" per client. Writers are
 * background threads, the reader is the render thread, and {@link MediaStatus} is immutable,
 * so the volatile publish is the whole story.</p>
 */
public final class MediaStatusRegistry {

	private static volatile MediaStatus current;

	private MediaStatusRegistry() {
	}

	/** Publishes a snapshot; {@code null} or unplayable snapshots hide the HUD. */
	public static void update(@Nullable MediaStatus status) {
		current = status != null && status.playable() ? status : null;
	}

	/** Hides the HUD (provider error, playback stopped, provider shut down). */
	public static void clear() {
		current = null;
	}

	/** The latest playable snapshot, or {@code null} when nothing should draw. */
	@Nullable
	public static MediaStatus current() {
		return current;
	}
}
