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

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.util.MathUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

@Suppress("unused")
interface AxoItemStack {
	@RequiresImpl
	fun `br$getItem`(): AxoItem {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$copy`(): AxoItemStack {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$setCount`(count: Int) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getCount`(): Int {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getDamage`(): Int {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getMaxDamage`(): Int {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$isEmpty`(): Boolean = `br$getCount`() == 0 || `br$getItem`() == AxoItems.AIR

	@RequiresImpl
	fun `br$getEnchantment`(enchant: AxoEnchant): Int {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$setEnchantment`(enchant: AxoEnchant, level: Int) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$removeEnchantment`(enchant: AxoEnchant) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$hasEnchantment`(enchant: AxoEnchant): Boolean = `br$getEnchantment`(enchant) != 0

	fun `br$getBarColor`(): Int {
		val f = max(0f, (`br$getMaxDamage`() - `br$getDamage`()).toFloat() / `br$getMaxDamage`().toFloat())
		return MathUtil.hsvToRgb(f / 3.0f, 1.0f, 1.0f)
	}

	@RequiresImpl
	fun `br$getHoverName`(): AxoText {
		throw BridgeUtil.noImpl()
	}

	companion object {
		@JvmStatic
		fun of(item: AxoItem, count: Int): AxoItemStack = PlatformImplInternal.createItemStack(item, count)

		@JvmStatic
		fun of(item: AxoItem): AxoItemStack = PlatformImplInternal.createItemStack(item, 1)
	}
}
