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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.Equippable
import kotlin.math.min

/**
 * Auto-Armor (wave 4 native utility) — QoL tier per docs/server-gated-features.md: ships
 * enabled, only a server deny (features or block_mods channel) force-offs it via
 * ServerGating.enforceQoL.
 */
class AutoArmor : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", true)
    @JvmField
    val checkInterval = IntegerOption("check_interval", 20, 5, 200)

    private val category = OptionCategory.create("autoarmor")

    private var lastTimesChanged = -1
    private var cooldown = 0

    override fun init() {
        category.add(enabled, checkInterval)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_ARMOR, enabled)
    }

    override fun tick() {
        ServerGating.enforceQoL(enabled, ServerGating.AUTO_ARMOR, LOCK_REASON_KEY)
        val player = client.player
        if (!enabled.get() || player == null || client.gui.screen() != null) {
            cooldown = 0
            return
        }

        val inventory = player.inventory
        val timesChanged = inventory.getTimesChanged()
        val inventoryChanged = timesChanged != lastTimesChanged
        lastTimesChanged = timesChanged
        if (!inventoryChanged && cooldown-- > 0) {
            return
        }
        cooldown = checkInterval.get()

        equipBetterArmor(player)
    }

    /** One pass over the four worn slots, equipping strictly better pieces found in the inventory. */
    private fun equipBetterArmor(player: LocalPlayer) {
        val inventory = player.inventory
        for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val worn = player.getItemBySlot(slot)
            val candidate = bestInventoryArmor(inventory, slot, armorScore(worn))
            if (candidate < 0) {
                continue
            }
            val menuSlot = menuSlotForInventoryIndex(candidate)
            val armorSlot = ARMOR_MENU_SLOTS[slot]!!
            // Pick the piece up, drop it into the worn slot (vanilla swaps if occupied), put
            // whatever is left on the cursor back where the piece came from.
            player.inventoryMenu.clicked(menuSlot, 0, ContainerInput.PICKUP, player)
            player.inventoryMenu.clicked(armorSlot, 0, ContainerInput.PICKUP, player)
            if (!player.inventoryMenu.carried.isEmpty) {
                player.inventoryMenu.clicked(menuSlot, 0, ContainerInput.PICKUP, player)
            }
            return // one slot per pass; the inventory-change signal re-triggers the rest
        }
    }

    companion object {
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.auto_armor"

        /** Worn-slot menu indexes in the player's own inventory menu (ARMOR_SLOT_START order). */
        private val ARMOR_MENU_SLOTS: Map<EquipmentSlot, Int> = mapOf(
            EquipmentSlot.HEAD to InventoryMenu.ARMOR_SLOT_START,
            EquipmentSlot.CHEST to InventoryMenu.ARMOR_SLOT_START + 1,
            EquipmentSlot.LEGS to InventoryMenu.ARMOR_SLOT_START + 2,
            EquipmentSlot.FEET to InventoryMenu.ARMOR_SLOT_START + 3
        )

        private val Instance = AutoArmor()

        @JvmStatic
        fun getInstance(): AutoArmor = Instance

        /**
         * Best strictly-better-than-[currentScore] armor piece for [slot] among the
         * carried items (hotbar + main inventory, indexes 0-35). Earliest index wins ties.
         */
        @JvmStatic
        fun bestInventoryArmor(inventory: Inventory, slot: EquipmentSlot, currentScore: Double): Int {
            var best = -1
            var bestScore = currentScore
            val size = min(Inventory.INVENTORY_SIZE, inventory.nonEquipmentItems.size)
            for (index in 0 until size) {
                val stack = inventory.getItem(index)
                if (stack.isEmpty) {
                    continue
                }
                val equippable = stack.get(DataComponents.EQUIPPABLE) ?: continue
                if (equippable.slot() != slot) {
                    continue
                }
                val score = armorScore(stack)
                if (score > bestScore) {
                    best = index
                    bestScore = score
                }
            }
            return best
        }

        /** Armor points carried by the stack (armor attribute total, toughness as tie-break weight). */
        @JvmStatic
        fun armorScore(stack: ItemStack): Double {
            if (stack.isEmpty) {
                return 0.0
            }
            val modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: return 0.0
            var armor = 0.0
            var toughness = 0.0
            for (entry in modifiers.modifiers()) {
                if (entry.attribute() == Attributes.ARMOR) {
                    armor += entry.modifier().amount()
                } else if (entry.attribute() == Attributes.ARMOR_TOUGHNESS) {
                    toughness += entry.modifier().amount()
                }
            }
            return armor + toughness * 0.01
        }

        /** Inventory index -> slot index in the player's own inventory menu (hotbar sits after main). */
        private fun menuSlotForInventoryIndex(index: Int): Int =
            if (Inventory.isHotbarSlot(index)) index + InventoryMenu.USE_ROW_SLOT_START else index
    }
}
