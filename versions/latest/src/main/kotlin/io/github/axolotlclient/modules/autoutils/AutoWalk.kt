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

import java.util.Locale

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.imperium.ImperiumClientSession
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption

/**
 * Auto-Walk (wave 4 native utility) — limited tier per docs/server-gated-features.md.
 */
class AutoWalk : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    @JvmField
    val mode = EnumOption("mode", Mode::class.java, Mode.TOGGLE)

    private val category = OptionCategory.create("autowalk")

    /** True while a walk session is paid for and active. */
    private var walking = false

    override fun init() {
        category.add(enabled, mode)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_WALK, enabled)

        WALK_KEY.`br$registerOnConsumeClick`(Runnable {
            if (mode.get() == Mode.TOGGLE && enabled.get()) {
                toggleWalking()
            }
        })
        Events.DISCONNECT.register(Runnable { this.stopWalking() })
    }

    override fun tick() {
        // Tier is re-evaluated before every activation step: a grant revoked mid-session
        // (replacement features packet, deny, or a spent rate budget) stops walking next tick.
        ServerGating.enforce(enabled, ServerGating.AUTO_WALK, LOCK_REASON_KEY, RATE_REASON_KEY)
        if (!enabled.get()) {
            stopWalking()
            return
        }
        if (client.player == null) {
            stopWalking()
            return
        }

        var want = if (mode.get() == Mode.HOLD) WALK_KEY.`br$isPressed`() else walking
        if (want && !walking) {
            // Limited tier: each session start spends one token; no budget, no session.
            want = ImperiumClientSession.getInstance().tryConsumeBudget(ServerGating.AUTO_WALK)
        }
        walking = want
    }

    /**
     * The forward-key state the KeyboardInputMixin ORs into the vanilla input — only
     * while the module is enabled (gate re-checked here so a stale flag can never feed the
     * input after a force-off), a world is up, and no screen holds the input.
     */
    fun isWalking(): Boolean =
        walking && enabled.get() && client.player != null && client.gui.screen() == null

    fun toggleWalking() {
        walking = !walking
        if (walking && !ImperiumClientSession.getInstance().tryConsumeBudget(ServerGating.AUTO_WALK)) {
            walking = false
        }
    }

    private fun stopWalking() {
        walking = false
    }

    enum class Mode {
        HOLD,
        TOGGLE;

        override fun toString(): String = "autowalk.mode." + name.lowercase(Locale.ROOT)
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_walk"
        const val RATE_REASON_KEY: String = "imperiumclient.gate.auto_walk.rate"

        private val WALK_KEY = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autowalk.toggle")

        private val Instance = AutoWalk()

        @JvmStatic
        fun getInstance(): AutoWalk = Instance
    }
}
