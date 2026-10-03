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

import com.google.common.base.Preconditions;
import io.github.axolotlclient.bridge.impl.AxoEnchantImpl;
import io.github.axolotlclient.bridge.item.AxoEnchant;
import io.github.axolotlclient.bridge.item.AxoItem;
import io.github.axolotlclient.bridge.item.AxoItemStack;
import io.github.axolotlclient.bridge.util.AxoText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements AxoItemStack {
	@Shadow
	public abstract Item getItem();

	@Shadow
	public abstract ItemStack copy();

	@Shadow
	public abstract int getMaxDamage();

	@Shadow
	public abstract void setCount(int count);

	@Shadow
	public abstract int getCount();

	@Shadow
	public abstract int getDamageValue();

	@Shadow
	public abstract Component getStyledHoverName();

	@Shadow
	public abstract int getBarColor();

	@Override
	public AxoItem br$getItem() {
		return getItem();
	}

	@Override
	public AxoItemStack br$copy() {
		return copy();
	}

	@Override
	public void br$setCount(int count) {
		setCount(count);
	}

	@Override
	public int br$getCount() {
		return getCount();
	}

	@Override
	public int br$getDamage() {
		return getDamageValue();
	}

	@Override
	public int br$getMaxDamage() {
		return getMaxDamage();
	}

	@Override
	public int br$getEnchantment(AxoEnchant enchant) {
		Preconditions.checkArgument(enchant != null, "enchant != null");
		Preconditions.checkArgument(enchant instanceof AxoEnchantImpl, "enchant instanceof Enchantment");

		final var self = (ItemStack) (Object) this;
		return ((AxoEnchantImpl) enchant).lookup()
			.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, self))
			.orElse(0);
	}

	@Override
	public void br$setEnchantment(AxoEnchant enchant, int level) {
		Preconditions.checkArgument(enchant != null, "enchant != null");
		Preconditions.checkArgument(level > 1, "level > 1");
		Preconditions.checkArgument(enchant instanceof AxoEnchantImpl, "enchant instanceof Enchantment");

		final var self = (ItemStack) (Object) this;
		((AxoEnchantImpl) enchant).lookup().ifPresent(
			holder -> EnchantmentHelper.updateEnchantments(self, builder -> builder.set(holder, level))
		);
	}

	@Override
	public void br$removeEnchantment(AxoEnchant enchant) {
		Preconditions.checkArgument(enchant != null, "enchant != null");
		Preconditions.checkArgument(enchant instanceof AxoEnchantImpl, "enchant instanceof Enchantment");

		final var self = (ItemStack) (Object) this;
		((AxoEnchantImpl) enchant).lookup().ifPresent(
			holder -> EnchantmentHelper.updateEnchantments(self, builder -> builder.removeIf(holder::equals))
		);
	}

	@Override
	public AxoText br$getHoverName() {
		return getStyledHoverName();
	}

	@Override
	public int br$getBarColor() {
		return getBarColor();
	}
}
