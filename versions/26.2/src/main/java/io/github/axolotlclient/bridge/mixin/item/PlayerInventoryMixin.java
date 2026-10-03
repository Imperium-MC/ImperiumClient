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

package io.github.axolotlclient.bridge.mixin.item;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.axolotlclient.bridge.item.AxoItemStack;
import io.github.axolotlclient.bridge.item.AxoPlayerInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin implements AxoPlayerInventory {
	@Shadow
	public abstract int getContainerSize();

	@Shadow
	public abstract ItemStack getItem(int slot);

	@Shadow
	public abstract ItemStack getSelectedItem();

	@Shadow
	@Final
	private EntityEquipment equipment;

	@Shadow
	@Final
	private NonNullList<ItemStack> items;

	@Override
	public AxoItemStack br$getMainHand() {
		return getSelectedItem();
	}

	@Override
	public List<? extends AxoItemStack> br$getItems() {
		return IntStream.range(0, getContainerSize())
			.mapToObj(this::getItem)
			.toList();
	}

	@Override
	public List<? extends AxoItemStack> br$getArmor() {
		return Stream.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
			.map(slot -> equipment.get(slot))
			.toList();
	}

	@Override
	public List<? extends AxoItemStack> br$getNonEquipmentItems() {
		return items.subList(9, 36);
	}

	@Override
	public AxoItemStack br$getOffHand() {
		return equipment.get(EquipmentSlot.OFFHAND);
	}
}
