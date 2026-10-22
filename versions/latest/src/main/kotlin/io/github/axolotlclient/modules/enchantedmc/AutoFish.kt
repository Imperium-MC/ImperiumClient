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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.mixin.FishingHookAccessor
import io.github.axolotlclient.mixin.MinecraftClientAccessor
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.FishingRodItem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Auto-Fish (Alpine "enchantedmc/fishing" parity) — server-gated per docs/server-gated-features.md.
 */
class AutoFish : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    @JvmField
    val delayMin = IntegerOption("delay_min", 500, 100, 5000)
    @JvmField
    val delayMax = IntegerOption("delay_max", 2000, 100, 5000)
    @JvmField
    val autoRecast = BooleanOption("auto_recast", true)

    // Alpine profile settings (M6): whether an open screen pauses the automation (the
    // pre-M6 behaviour, still the default) and a keybind flipping the module toggle.
    @JvmField
    val pauseInMenus = BooleanOption("pause_in_menus", true)
    @JvmField
    val toggleKey: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autofish.toggle")

    private val category = OptionCategory.create("autofish")
    private val random = Random()

    /** True once the reel use was sent for the current bobber; cleared when the bobber is gone. */
    private var reelSent = false

    /** Ticks until the recast; negative when no recast is scheduled. */
    private var recastCountdown = -1

    override fun init() {
        category.add(enabled, delayMin, delayMax, autoRecast, pauseInMenus)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        toggleKey.`br$registerOnConsumeClick`(Runnable {
            if (ServerGating.granted(ServerGating.AUTO_FISH)) {
                enabled.toggle()
            }
        })
        // Server-denyable through the block_mods channel too (FeatureDisabler pattern).
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_FISH, enabled)
        Events.DISCONNECT.register(Runnable { this.resetState() })
    }

    override fun tick() {
        // The gate is re-evaluated before EVERY automation step: a grant revoked mid-session
        // (replacement features packet, or a deny) stops the module on the next tick.
        ServerGating.enforce(enabled, ServerGating.AUTO_FISH, LOCK_REASON_KEY)
        if (!enabled.get()) {
            resetState()
            return
        }

        val player = client.player
        if (player == null) {
            resetState()
            return
        }
        if (pauseInMenus.get() && client.gui.screen() != null) {
            return // menu/chat open: hold state, do not click through screens
        }

        val hook = player.fishing
        if (hook != null) {
            recastCountdown = -1
            val biting = hook.entityData.get(FishingHookAccessor.`axolotlclient$dataBiting`())
            if (biting && !reelSent) {
                (client as MinecraftClientAccessor).`axolotlclient$startUseItem`()
                reelSent = true
            } else if (!biting && reelSent) {
                // The bite ended without the bobber being removed (fish escaped or the reel
                // was ignored): re-arm so the next bite on this cast still triggers.
                reelSent = false
            }
        } else if (reelSent) {
            // The bobber entity is gone: the reel completed (catch pulled or line lost).
            reelSent = false
            recastCountdown = if (autoRecast.get()) ticks(nextRecastDelayMs()) else -1
        } else if (recastCountdown > 0) {
            recastCountdown--
            if (recastCountdown == 0) {
                recastCountdown = -1
                if (holdingRod(player)) {
                    (client as MinecraftClientAccessor).`axolotlclient$startUseItem`()
                }
            }
        }
    }

    /** Uniform in [min, max], then ±20% jitter (clamped back into range) so fixed bounds don't metronome. */
    fun nextRecastDelayMs(): Int {
        val min = min(delayMin.get(), delayMax.get())
        val max = max(delayMin.get(), delayMax.get())
        val base = min + random.nextInt(max - min + 1)
        val jittered = base * (0.8 + random.nextDouble() * 0.4)
        return Mth.clamp(jittered.roundToInt(), min, max)
    }

    private fun resetState() {
        reelSent = false
        recastCountdown = -1
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_fish"

        private val Instance = AutoFish()

        @JvmStatic
        fun getInstance(): AutoFish = Instance

        private fun holdingRod(player: LocalPlayer): Boolean =
            player.mainHandItem.item is FishingRodItem ||
                player.getItemInHand(InteractionHand.OFF_HAND).item is FishingRodItem

        private fun ticks(ms: Int): Int = max(1, (ms + 49) / 50)
    }
}
