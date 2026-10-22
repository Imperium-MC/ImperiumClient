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

import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoPlayerInventory
import net.minecraft.core.NonNullList
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EntityEquipment
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.stream.IntStream
import java.util.stream.Stream

@Mixin(Inventory::class)
abstract class PlayerInventoryMixin : AxoPlayerInventory {
	@Shadow
	abstract fun getContainerSize(): Int

	@Shadow
	abstract fun getItem(slot: Int): ItemStack

	@Shadow
	abstract fun getSelectedItem(): ItemStack

	@Shadow
	@field:Final
	private val equipment: EntityEquipment? = null

	@Shadow
	@field:Final
	private val items: NonNullList<ItemStack>? = null

	override fun `br$getMainHand`(): AxoItemStack = getSelectedItem()

	override fun `br$getItems`(): List<out AxoItemStack> = IntStream.range(0, getContainerSize())
		.mapToObj { slot: Int -> getItem(slot) }
		.toList()

	override fun `br$getArmor`(): List<out AxoItemStack> = Stream.of(
		EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
	)
		.map { slot: EquipmentSlot -> equipment!![slot] }
		.toList()

	override fun `br$getNonEquipmentItems`(): List<out AxoItemStack> = items!!.subList(9, 36)

	override fun `br$getOffHand`(): AxoItemStack = equipment!![EquipmentSlot.OFFHAND]
}
