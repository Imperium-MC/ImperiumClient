/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hypixel

import java.util.function.Consumer
import java.util.regex.Pattern

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent

class AutoTip : AbstractHypixelMod {
    private val category: OptionCategory = OptionCategory.create("autotip")

    private val enabled = BooleanOption("enabled", false)
    private val hideMessages = BooleanOption("hideTipMessages", false)

    private val messagePattern = Pattern.compile("^You tipped [0-9]+ players in [0-9]+ different games!\$")
    private val tippedPattern =
        Pattern.compile("^You already tipped everyone that has boosters active, so there isn't anybody to be tipped right now!\$")

    private var lastTime = 0L
    private var initialized = false

    override fun init() {
        category.add(enabled, hideMessages)
        initialized = true

        Events.RECEIVE_CHAT_MESSAGE.register(Consumer { event -> onChatMessage(event) })
    }

    override fun getCategory(): OptionCategory = category

    override fun tick() {
        if (initialized) {
            val client = AxoMinecraftClient.getInstance()
            val serverAddress = AxoMinecraftClient.getInstance().`br$getServerAddress`()
            val isHypixel = serverAddress != null && serverAddress.contains("hypixel")

            if (System.currentTimeMillis() - lastTime > 1200000 && isHypixel && enabled.get() &&
                client.`br$getPlayer`() != null
            ) {
                client.`br$sendToServer`("/tip all")
                lastTime = System.currentTimeMillis()
            }
        }
    }

    override fun tickable(): Boolean = true

    fun onChatMessage(event: ReceiveChatMessageEvent) {
        event.isCancelled =
            enabled.get() && hideMessages.get() &&
                (messagePattern.matcher(event.originalMessage).matches() ||
                    tippedPattern.matcher(event.originalMessage).matches())
    }

    companion object {
        private val Instance = AutoTip()

        @JvmStatic
        fun getInstance(): AutoTip = Instance
    }
}
