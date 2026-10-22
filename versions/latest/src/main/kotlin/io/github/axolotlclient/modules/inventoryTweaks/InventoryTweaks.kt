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

package io.github.axolotlclient.modules.inventoryTweaks

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.modules.AbstractModule

/**
 * inventory_tweaks completion (wave 5): the two toggles Alpine groups under inventory_tweaks,
 * plus compact stack counts. Every option is standalone — none needs the custom PotionsHud.
 */
class InventoryTweaks : AbstractModule() {
    @JvmField
    val hideVanillaPotionHud = BooleanOption("hide_vanilla_potion_hud", false)
    @JvmField
    val keepCentered = BooleanOption("keep_centered", false)
    @JvmField
    val compactItemCounts = BooleanOption("compact_item_counts", false)

    private val category = OptionCategory.create("inventoryTweaks")

    override fun init() {
        category.add(hideVanillaPotionHud, keepCentered, compactItemCounts)
        AxolotlClient.config().general.add(category)
    }

    /** Whether the vanilla effect icons must not be drawn at all (HUD corner or container panel). */
    fun hidesVanillaEffects(): Boolean = hideVanillaPotionHud.get()

    /** Whether the in-inventory effect panel is suppressed so the container stays centered. */
    fun suppressesEffectPanel(): Boolean = keepCentered.get() || hideVanillaPotionHud.get()

    /** Whether container screens must be (re-)centered by the mixin. */
    fun forcesCenteredContainers(): Boolean = keepCentered.get()

    /** Formats a stack count, honouring the compact_item_counts toggle. */
    fun formatCount(count: Int): String =
        if (compactItemCounts.get()) CompactCounts.format(count) else count.toString()

    companion object {
        private val Instance = InventoryTweaks()

        @JvmStatic
        fun getInstance(): InventoryTweaks = Instance
    }
}
