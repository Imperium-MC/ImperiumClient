/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.bridge.mixin.item

import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItems
import net.minecraft.world.item.Items
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AxoItems::class, remap = false)
abstract class AxoItemsMixin {
	companion object {
		// Static shadow fields live on the companion so they compile to real static fields of
		// this mixin class; mixin redirects the accesses to the AxoItems fields.
		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var AIR: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_HELMET: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_LEGGINGS: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_CHESTPLATE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_BOOTS: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_SWORD: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var ARROW: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_INGOT: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var GOLD_INGOT: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var DIAMOND: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var EMERALD: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE_SWORD: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var DIAMOND_SWORD: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var DIAMOND_CHESTPLATE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var DIAMOND_BOOTS: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var TIPPED_ARROW: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var SPECTRAL_ARROW: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE_PICKAXE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE_AXE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE_SHOVEL: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var STONE_HOE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var GLOWSTONE_DUST: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var ENDER_EYE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var OBSIDIAN: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var DIAMOND_PICKAXE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var GOLD_PICKAXE: AxoItem? = null

		@field:Mutable
		@field:Shadow
		@field:Final
		@JvmField
		var IRON_PICKAXE: AxoItem? = null

		@Inject(method = ["<clinit>"], at = [At("HEAD")], cancellable = true)
		@JvmStatic
		private fun setStaticValues(info: CallbackInfo) {
			AIR = Items.AIR
			IRON_HELMET = Items.IRON_HELMET
			IRON_CHESTPLATE = Items.IRON_CHESTPLATE
			IRON_LEGGINGS = Items.IRON_LEGGINGS
			IRON_BOOTS = Items.IRON_BOOTS
			IRON_SWORD = Items.IRON_SWORD
			ARROW = Items.ARROW
			IRON_INGOT = Items.IRON_INGOT
			GOLD_INGOT = Items.GOLD_INGOT
			DIAMOND = Items.DIAMOND
			EMERALD = Items.EMERALD
			STONE_SWORD = Items.STONE_SWORD
			DIAMOND_SWORD = Items.DIAMOND_SWORD
			DIAMOND_CHESTPLATE = Items.DIAMOND_CHESTPLATE
			DIAMOND_BOOTS = Items.DIAMOND_BOOTS
			TIPPED_ARROW = Items.TIPPED_ARROW
			SPECTRAL_ARROW = Items.SPECTRAL_ARROW
			STONE = Items.STONE
			STONE_PICKAXE = Items.STONE_PICKAXE
			STONE_AXE = Items.STONE_AXE
			STONE_SHOVEL = Items.STONE_SHOVEL
			STONE_HOE = Items.STONE_HOE
			GLOWSTONE_DUST = Items.GLOWSTONE_DUST
			ENDER_EYE = Items.ENDER_EYE
			OBSIDIAN = Items.OBSIDIAN
			DIAMOND_PICKAXE = Items.DIAMOND_PICKAXE
			GOLD_PICKAXE = Items.GOLDEN_PICKAXE
			IRON_PICKAXE = Items.IRON_PICKAXE
			info.cancel()
		}
	}
}
