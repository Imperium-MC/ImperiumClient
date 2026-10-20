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
 * The seam the future ImperiumMC cosmetics channel plugs into (W5-A). The wheel never sends
 * anything itself: selecting a slot hands the [Emote] to whatever dispatcher is installed
 * in [EmoteDispatch] — today the local-only [LocalEmoteDispatcher], tomorrow the
 * `imperiumclient:cosmetics` payload receiver that broadcasts the emote to the server.
 *
 * Implementations may be called from the client thread only (screen input and tick paths);
 * they must not block.
 */
fun interface EmoteDispatcher {

	/**
	 * Performs whatever "sending" an emote means on this client. Must not throw on unknown ids —
	 * log and move on; a broken emote must never take the wheel down with it.
	 */
	fun dispatch(emote: Emote)
}
