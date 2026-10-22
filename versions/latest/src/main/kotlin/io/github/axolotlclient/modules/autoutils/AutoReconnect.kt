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

package io.github.axolotlclient.modules.autoutils

import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.TransferState
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.min

/**
 * Auto-Reconnect (wave 4 native utility) — QoL tier per docs/server-gated-features.md.
 */
class AutoReconnect : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", true)
    @JvmField
    val delaySeconds = IntegerOption("delay_seconds", 5, 1, 300)
    @JvmField
    val maxAttempts = IntegerOption("max_attempts", 3, 1, 50)

    private val category = OptionCategory.create("autoreconnect")

    /** The one and only reconnect target: the server this session actually joined. */
    private var lastServer: ServerData? = null

    /** Reconnects already fired since the last successful join. */
    private var attempts = 0

    /** True while counting down on a disconnect screen we own. */
    private var counting = false

    /** The disconnect screen the countdown is attached to (leaving it cancels). */
    private var attachedScreen: Screen? = null

    /** The countdown label on that screen; null until the screen mixin attached one. */
    private var countdownLabel: StringWidget? = null
    private var lastRenderedSeconds = -1

    /** When the countdown started (Util.getMillis); the deadline is this plus the delay. */
    private var attachAtMs = 0L

    override fun init() {
        category.add(enabled, delaySeconds, maxAttempts)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_RECONNECT, enabled)

        // Remember the joined server (the only reconnect target) and reset the streak on join.
        Events.CONNECTION_PLAY_READY.register(Consumer { info: Any? ->
            attempts = 0
            cancel()
            if (info is ServerData) {
                lastServer = info
            }
        })
    }

    override fun tick() {
        ServerGating.enforceQoL(enabled, ServerGating.AUTO_RECONNECT, LOCK_REASON_KEY)
        if (!counting) {
            return
        }
        val client = Minecraft.getInstance()
        if (!enabled.get() || client.player != null || attachedScreen == null || client.gui.screen() !== attachedScreen) {
            cancel() // module off, joined somewhere else, or the player moved on — their choice wins
            return
        }
        updateLabel()
        if (Util.getMillis() >= attachAtMs + delaySeconds.get() * 1000L) {
            fire()
        }
    }

    /** True when the disconnect screen should attach a countdown (module on, target known, attempts left). */
    fun wantsCountdown(): Boolean =
        enabled.get() && lastServer != null && attempts < maxAttempts.get()

    /**
     * Called by DisconnectedScreenMixin when the disconnect screen builds: the label
     * lives on the screen, the countdown itself starts here. Screens can re-init (resize),
     * so attaching twice must restart rather than double-count.
     */
    fun attachCountdown(screen: DisconnectedScreen, label: StringWidget) {
        attachedScreen = screen
        countdownLabel = label
        counting = true
        attachAtMs = Util.getMillis()
        lastRenderedSeconds = -1
        updateLabel()
    }

    private fun updateLabel() {
        val label = countdownLabel ?: return
        val remainingMs = attachAtMs + delaySeconds.get() * 1000L - Util.getMillis()
        val seconds = Mth.positiveCeilDiv(min(Int.MAX_VALUE.toLong(), max(0, remainingMs)).toInt(), 1000)
        if (seconds != lastRenderedSeconds) {
            lastRenderedSeconds = seconds
            label.setMessage(
                Component.translatable(
                    "imperiumclient.autoreconnect.countdown",
                    seconds, attempts + 1, maxAttempts.get()
                )
            )
        }
        // keep the label centered as the text width changes
        val screen = attachedScreen ?: return
        val width = label.width
        val x = Mth.clamp((screen.width - width) / 2, 0, max(0, screen.width - width))
        label.setX(x)
    }

    private fun fire() {
        counting = false
        val target = lastServer
        val parent = attachedScreen
        if (target == null || parent == null) {
            cancel()
            return
        }
        attempts++
        ConnectScreen.startConnecting(
            parent, client, ServerAddress.parseString(target.ip), target, false,
            TransferState(mapOf(), mapOf(), false)
        )
    }

    private fun cancel() {
        counting = false
        attachedScreen = null
        countdownLabel = null
        lastRenderedSeconds = -1
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_reconnect"

        private val Instance = AutoReconnect()

        @JvmStatic
        fun getInstance(): AutoReconnect = Instance
    }
}
