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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.min

/**
 * Auto-Tool (wave 4 native utility) — QoL tier per docs/server-gated-features.md.
 */
class AutoTool : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", true)
    @JvmField
    val swapBack = BooleanOption("swap_back", true)

    private val category = OptionCategory.create("autotool")

    /** True while our swap is active (guards the swap-back). */
    private var swapped = false

    /** Slot held before the swap; -1 while inactive. */
    private var previousSlot = -1

    override fun init() {
        category.add(enabled, swapBack)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_TOOL, enabled)
        Events.DISCONNECT.register(Runnable { this.restore() })
    }

    /**
     * Called from MultiPlayerGameModeMixin at the head of both destroy-block paths:
     * swap to the best tool for [pos]'s state if the current selection is not already it.
     */
    fun beforeDestroy(pos: BlockPos) {
        if (!enabled.get() || client.player == null) {
            return
        }
        val player = client.player!!
        val state = player.level().getBlockState(pos)
        val inventory = player.inventory
        val best = bestToolSlot(inventory, state)
        if (best < 0 || best == inventory.selectedSlot) {
            return
        }
        if (!swapped) {
            previousSlot = inventory.selectedSlot
            swapped = true
        }
        inventory.setSelectedSlot(best)
    }

    override fun tick() {
        ServerGating.enforceQoL(enabled, ServerGating.AUTO_TOOL, LOCK_REASON_KEY)
        if (!swapped) {
            return
        }
        if (!enabled.get() || client.player == null) {
            restore()
            return
        }
        // Mining session over: attack key released, crosshair left the block, or a screen opened.
        if (client.gui.screen() == null &&
            client.options.keyAttack.isDown &&
            client.hitResult is BlockHitResult
        ) {
            return
        }
        restore()
    }

    private fun restore() {
        if (swapped && swapBack.get() && client.player != null) {
            val inventory = client.player!!.inventory
            if (previousSlot >= 0 && previousSlot != inventory.selectedSlot) {
                inventory.setSelectedSlot(previousSlot)
            }
        }
        swapped = false
        previousSlot = -1
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_tool"

        private val Instance = AutoTool()

        @JvmStatic
        fun getInstance(): AutoTool = Instance

        /**
         * Hotbar slot whose stack breaks [state] fastest, or -1 when nothing beats the
         * current selection. Pure over the hotbar so it is unit-testable without a world.
         */
        @JvmStatic
        fun bestToolSlot(inventory: Inventory, state: BlockState): Int {
            val selected = inventory.selectedSlot
            var bestSpeed = destroySpeed(inventory.getItem(selected), state)
            var bestSlot = -1
            for (slot in 0 until min(Inventory.getSelectionSize(), inventory.nonEquipmentItems.size)) {
                if (slot == selected) {
                    continue
                }
                val speed = destroySpeed(inventory.getItem(slot), state)
                if (speed > bestSpeed) {
                    bestSpeed = speed
                    bestSlot = slot
                }
            }
            return bestSlot
        }

        private fun destroySpeed(stack: ItemStack, state: BlockState): Float =
            if (stack.isEmpty) 0.0f else stack.getDestroySpeed(state)
    }
}
