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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.imperium.emotes.EmoteWheel
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Wave-5 media wiring (W5-A emote wheel + W5-B now-playing HUD), registered as its own client
 * entrypoint so nothing else has to change: the emote wheel's keybind/tick, the media poller's
 * follow-the-toggle lifecycle, and the config categories both features expose.
 *
 * The poller reconcile runs once a second rather than on the option's change listener so a
 * config file edit outside the options screen (profiles, manual edits) is picked up too, and so
 * it can never start before the config has actually loaded.
 */
object ImperiumMediaInit : ClientModInitializer {

	private var tickCounter = 0
	private var lastMediaEnabled = false

	override fun onInitializeClient() {
		AxolotlClientCommon.getInstance().logger.info("[media] wave-5 media init (emote wheel + now-playing)")
		EmoteWheel.init()

		lastMediaEnabled = MediaConfig.media.get()
		if (lastMediaEnabled) {
			SpotifyLocalPoller.start()
		}
		ClientTickEvents.END_CLIENT_TICK.register { _ ->
			if (--tickCounter > 0) {
				return@register
			}
			tickCounter = 20
			val enabled = MediaConfig.media.get()
			if (enabled != lastMediaEnabled) {
				lastMediaEnabled = enabled
				if (enabled) {
					SpotifyLocalPoller.start()
				} else {
					SpotifyLocalPoller.stop()
				}
			}
		}
	}
}
