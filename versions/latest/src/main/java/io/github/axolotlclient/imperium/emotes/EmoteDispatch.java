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

package io.github.axolotlclient.imperium.emotes;

import org.jetbrains.annotations.Nullable;

/**
 * Holder for the active {@link EmoteDispatcher} — the single swap point of the emote shell.
 * Today the wheel runs on the {@link LocalEmoteDispatcher}; when the
 * {@code imperiumclient:cosmetics} channel lands, its receiver installs a dispatcher that turns
 * an {@link Emote} into the channel payload instead. Swap and reset are both exercised by tests
 * to prove the seam actually re-routes wheel selections.
 */
public final class EmoteDispatch {

	private static volatile EmoteDispatcher dispatcher = new LocalEmoteDispatcher();

	private EmoteDispatch() {
	}

	public static EmoteDispatcher get() {
		return dispatcher;
	}

	/** Installs a dispatcher; {@code null} restores the default local dispatcher. */
	public static void set(@Nullable EmoteDispatcher replacement) {
		dispatcher = replacement == null ? new LocalEmoteDispatcher() : replacement;
	}

	/** Restores the default local dispatcher (what disconnect / channel loss should do). */
	public static void reset() {
		set(null);
	}

	public static void dispatch(Emote emote) {
		dispatcher.dispatch(emote);
	}
}
