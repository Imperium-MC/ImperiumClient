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

package io.github.axolotlclient.imperium.emotes

/**
 * Holder for the active [EmoteDispatcher] — the single swap point of the emote shell.
 * Today the wheel runs on the [LocalEmoteDispatcher]; when the
 * `imperiumclient:cosmetics` channel lands, its receiver installs a dispatcher that turns
 * an [Emote] into the channel payload instead. Swap and reset are both exercised by tests
 * to prove the seam actually re-routes wheel selections.
 */
object EmoteDispatch {

	@Volatile
	private var dispatcher: EmoteDispatcher = LocalEmoteDispatcher()

	@JvmStatic
	fun get(): EmoteDispatcher = dispatcher

	/** Installs a dispatcher; `null` restores the default local dispatcher. */
	@JvmStatic
	fun set(replacement: EmoteDispatcher?) {
		dispatcher = replacement ?: LocalEmoteDispatcher()
	}

	/** Restores the default local dispatcher (what disconnect / channel loss should do). */
	@JvmStatic
	fun reset() {
		set(null)
	}

	@JvmStatic
	fun dispatch(emote: Emote) {
		dispatcher.dispatch(emote)
	}
}
