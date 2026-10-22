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

import com.google.common.base.Preconditions
import io.github.axolotlclient.bridge.impl.AxoEnchantImpl
import io.github.axolotlclient.bridge.item.AxoEnchant
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.util.AxoText
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(ItemStack::class)
abstract class ItemStackMixin : AxoItemStack {
	@Shadow
	abstract fun getItem(): Item

	@Shadow
	abstract fun copy(): ItemStack

	@Shadow
	abstract fun getMaxDamage(): Int

	@Shadow
	abstract fun setCount(count: Int)

	@Shadow
	abstract fun getCount(): Int

	@Shadow
	abstract fun getDamageValue(): Int

	@Shadow
	abstract fun getStyledHoverName(): Component

	@Shadow
	abstract fun getBarColor(): Int

	override fun `br$getItem`(): AxoItem = getItem()

	override fun `br$copy`(): AxoItemStack = copy()

	override fun `br$setCount`(count: Int) {
		setCount(count)
	}

	override fun `br$getCount`(): Int = getCount()

	override fun `br$getDamage`(): Int = getDamageValue()

	override fun `br$getMaxDamage`(): Int = getMaxDamage()

	override fun `br$getEnchantment`(enchant: AxoEnchant): Int {
		Preconditions.checkArgument(enchant != null, "enchant != null")
		Preconditions.checkArgument(enchant is AxoEnchantImpl, "enchant instanceof Enchantment")

		val self = this as ItemStack
		return (enchant as AxoEnchantImpl).lookup()
			.map { holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, self) }
			.orElse(0)
	}

	override fun `br$setEnchantment`(enchant: AxoEnchant, level: Int) {
		Preconditions.checkArgument(enchant != null, "enchant != null")
		Preconditions.checkArgument(level > 1, "level > 1")
		Preconditions.checkArgument(enchant is AxoEnchantImpl, "enchant instanceof Enchantment")

		val self = this as ItemStack
		(enchant as AxoEnchantImpl).lookup().ifPresent { holder ->
			EnchantmentHelper.updateEnchantments(self) { builder -> builder.set(holder, level) }
		}
	}

	override fun `br$removeEnchantment`(enchant: AxoEnchant) {
		Preconditions.checkArgument(enchant != null, "enchant != null")
		Preconditions.checkArgument(enchant is AxoEnchantImpl, "enchant instanceof Enchantment")

		val self = this as ItemStack
		(enchant as AxoEnchantImpl).lookup().ifPresent { holder ->
			EnchantmentHelper.updateEnchantments(self) { builder -> builder.removeIf { it == holder } }
		}
	}

	override fun `br$getHoverName`(): AxoText = getStyledHoverName()

	override fun `br$getBarColor`(): Int = getBarColor()
}
