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

package io.github.axolotlclient.modules.enchantedmc

import java.util.Random

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.mixin.MinecraftClientAccessor
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Auto-Clicker (Alpine "enchantedmc/clicking" parity) — server-gated per
 * docs/server-gated-features.md. Ships disabled and locked ("This server has not enabled
 * auto-clicking") until the current server grants `auto_click` via the
 * `imperiumclient:features` channel; singleplayer counts as granted.
 *
 * <p>Hold-to-click: while the bind is held, left-clicks fire at a CPS drawn uniformly from
 * [cps_min, cps_max] (each click re-rolls, ±20% interval jitter). Attack-only — every click
 * goes through the vanilla `Minecraft#startAttack` path, so guards, packets and hand
 * swing match a physical left click. Clicking stops the moment any screen (menu, chat,
 * inventory) is open, and resumes fresh (no burst) when it closes.</p>
 */
class AutoClick : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    @JvmField
    val cpsMin = IntegerOption("cps_min", 8, 4, 16)
    @JvmField
    val cpsMax = IntegerOption("cps_max", 12, 4, 16)

    private val category = OptionCategory.create("autoclick")
    private val random = Random()

    /** Timestamp (Util.getMillis) of the next scheduled click; 0 while idle. */
    private var nextClickAt = 0L

    override fun init() {
        category.add(enabled, cpsMin, cpsMax)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        // Server-denyable through the block_mods channel too (FeatureDisabler pattern).
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_CLICK, enabled)
        Events.DISCONNECT.register(Runnable { this.stop() })
    }

    override fun tick() {
        // The gate is re-evaluated before EVERY click: a grant revoked mid-session stops
        // the clicker on the next tick.
        ServerGating.enforce(enabled, ServerGating.AUTO_CLICK, LOCK_REASON_KEY)
        val holding = enabled.get() && client.player != null &&
            client.gui.screen() == null &&
            HOLD_KEY.`br$isPressed`()
        if (!holding) {
            stop()
            return
        }

        val now = Util.getMillis()
        if (nextClickAt == 0L) {
            nextClickAt = now + nextIntervalMs()
        }
        while (now >= nextClickAt) {
            (client as MinecraftClientAccessor).`axolotlclient$startAttack`()
            val interval = nextIntervalMs()
            nextClickAt += interval
            if (nextClickAt < now) {
                // catch-up guard after a lag spike: never fire a burst
                nextClickAt = now + interval
            }
        }
    }

    private fun stop() {
        nextClickAt = 0
    }

    /** Interval for one click: uniform CPS in [min, max], ±20% jitter, at least one tick. */
    fun nextIntervalMs(): Long {
        val min = min(cpsMin.get(), cpsMax.get())
        val max = max(cpsMin.get(), cpsMax.get())
        val cps = min + random.nextInt(max - min + 1)
        val jittered = (1000.0 / cps) * (0.8 + random.nextDouble() * 0.4)
        return max(50, jittered.roundToLong())
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_click"

        private val Instance = AutoClick()
        private val HOLD_KEY = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autoclick.hold")

        @JvmStatic
        fun getInstance(): AutoClick = Instance
    }
}
