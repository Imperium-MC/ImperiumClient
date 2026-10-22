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

package io.github.axolotlclient.bridge.item

import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl

// Bridge constant holder: the throwing initializers live in this class's <clinit>, which
// the version-side mixin (AxoItemsMixin) cancels at HEAD and then assigns real values.
@Suppress("unused")
class AxoItems {
	companion object {
		@RequiresImpl
		@JvmField
		val AIR: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_LEGGINGS: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_CHESTPLATE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_HELMET: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_SWORD: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_BOOTS: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val ARROW: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_INGOT: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val GOLD_INGOT: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val DIAMOND: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val EMERALD: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE_SWORD: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val DIAMOND_SWORD: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val DIAMOND_CHESTPLATE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val DIAMOND_BOOTS: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl(min = BridgeVersion.V1_20)
		@JvmField
		val TIPPED_ARROW: AxoItem? = BridgeUtil.noImplValue()

		@RequiresImpl(min = BridgeVersion.V1_20)
		@JvmField
		val SPECTRAL_ARROW: AxoItem? = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE_PICKAXE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val IRON_PICKAXE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val GOLD_PICKAXE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val DIAMOND_PICKAXE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE_AXE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE_SHOVEL: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val STONE_HOE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val GLOWSTONE_DUST: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val ENDER_EYE: AxoItem = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val OBSIDIAN: AxoItem = BridgeUtil.noImplValue()
	}
}
