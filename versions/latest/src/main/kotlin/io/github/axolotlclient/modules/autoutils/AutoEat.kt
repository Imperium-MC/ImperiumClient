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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.mixin.MinecraftClientAccessor
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * Auto-Eat (wave 4 native utility) — QoL tier per docs/server-gated-features.md.
 */
class AutoEat : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", true)
    @JvmField
    val hungerThreshold = IntegerOption("hunger_threshold", 14, 1, 19)
    @JvmField
    val swapBack = BooleanOption("swap_back", true)

    private val category = OptionCategory.create("autoeat")

    /** True while WE hold the use key for an eat in progress (a physical press is never touched). */
    private var eatingByUs = false

    /** Hotbar slot held before we swapped to food; -1 while we hold the player's own selection. */
    private var previousSlot = -1

    override fun init() {
        category.add(enabled, hungerThreshold, swapBack)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        // Server-denyable through the block_mods channel too (FeatureDisabler pattern).
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_EAT, enabled)
        Events.DISCONNECT.register(Runnable { this.stop() })
    }

    override fun tick() {
        // Re-evaluated every tick: a mid-session deny stops an in-flight eat on the next tick.
        ServerGating.enforceQoL(enabled, ServerGating.AUTO_EAT, LOCK_REASON_KEY)
        if (!enabled.get()) {
            stop()
            return
        }

        val player = client.player
        if (player == null) {
            stop()
            return
        }
        if (client.gui.screen() != null) {
            return // menu/chat open: hold state (handleKeybinds is skipped anyway), never click through screens
        }

        if (player.isUsingItem) {
            if (!eatingByUs) {
                return // the player is using an item themselves — do not interfere
            }
            if (!isEdible(player.mainHandItem)) {
                // the stack ran out (or the hand changed underneath us): stop holding use
                stop()
                return
            }
            // keep the simulated key held for the whole eat duration
            client.options.keyUse.setDown(true)
            return
        }

        if (eatingByUs) {
            // eat finished: release and restore the hand
            stop()
        }

        if (player.foodData.foodLevel >= hungerThreshold.get() || !player.canEat(false)) {
            return
        }

        val foodSlot = chooseFoodSlot(player.inventory)
        if (foodSlot < 0) {
            return // nothing edible in the hotbar
        }

        val inventory = player.inventory
        if (inventory.selectedSlot != foodSlot) {
            previousSlot = inventory.selectedSlot
            inventory.setSelectedSlot(foodSlot)
        }
        eatingByUs = true
        client.options.keyUse.setDown(true)
        (client as MinecraftClientAccessor).`axolotlclient$startUseItem`()
    }

    /** Releases the simulated use key and restores the pre-eat selection (if swap_back). */
    private fun stop() {
        if (eatingByUs) {
            client.options.keyUse.setDown(false)
        }
        eatingByUs = false
        if (previousSlot >= 0 && swapBack.get() && client.player != null) {
            val inventory = client.player!!.inventory
            if (inventory.selectedSlot != previousSlot && !client.player!!.isUsingItem) {
                inventory.setSelectedSlot(previousSlot)
            }
        }
        previousSlot = -1
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_eat"

        private val Instance = AutoEat()

        @JvmStatic
        fun getInstance(): AutoEat = Instance

        @JvmStatic
        fun isEdible(stack: ItemStack): Boolean = !stack.isEmpty && stack.has(DataComponents.FOOD)

        /**
         * Best hotbar slot to eat: highest nutrition first, saturation as tie-break, earliest
         * slot on full ties. Pure over Inventory.getNonEquipmentItems() (hotbar is
         * indexes 0-8) so the choice is unit-testable without a world.
         */
        @JvmStatic
        fun chooseFoodSlot(inventory: Inventory): Int {
            var bestSlot = -1
            var bestNutrition = -1
            var bestSaturation = -1.0f
            for (slot in 0 until min(Inventory.getSelectionSize(), inventory.nonEquipmentItems.size)) {
                val food: FoodProperties = inventory.getItem(slot).get(DataComponents.FOOD) ?: continue
                if (food.nutrition() > bestNutrition ||
                    (food.nutrition() == bestNutrition && food.saturation() > bestSaturation)
                ) {
                    bestSlot = slot
                    bestNutrition = food.nutrition()
                    bestSaturation = food.saturation()
                }
            }
            return bestSlot
        }
    }
}
