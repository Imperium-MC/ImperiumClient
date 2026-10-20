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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.util.notifications.Notifications
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * The pre-channel dispatcher (W5-A): "sending" an emote means logging it locally and showing the
 * player a self-toast ("Emote sent: wave"), exactly what the owner spec pins for the shell. The
 * log/toast calls are protected hooks so tests can subclass and record them headless, and the
 * future `imperiumclient:cosmetics` channel replaces this whole class via
 * [EmoteDispatch.set], not by editing it.
 */
open class LocalEmoteDispatcher : EmoteDispatcher {

	final override fun dispatch(emote: Emote) {
		log(emote)
		showToast(emote)
	}

	protected open fun log(emote: Emote) {
		AxolotlClientCommon.getInstance().logger.info("[emotes] dispatched '{}' (id '{}')", emote.name(), emote.id())
	}

	protected open fun showToast(emote: Emote) {
		val client = Minecraft.getInstance() ?: return
		client.execute(Runnable {
			Notifications.getInstance().addStatus(
				Component.translatable("imperiumclient.emotes.title"),
				Component.translatable("imperiumclient.emotes.sent", emote.name())
			)
		})
	}
}
