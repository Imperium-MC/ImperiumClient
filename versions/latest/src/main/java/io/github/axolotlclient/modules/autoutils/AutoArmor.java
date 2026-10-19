/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.autoutils;

import java.util.Map;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Armor (wave 4 native utility) — QoL tier per docs/server-gated-features.md: ships
 * enabled, only a server deny (features or block_mods channel) force-offs it via
 * {@link ServerGating#enforceQoL}.
 *
 * <p>Equips better armor from the inventory into empty or replaceable equipment slots.
 * "Armor" is whatever carries an {@link Equippable} component for a worn slot (the vanilla
 * armor-item definition since the equipment refactor), and "better" is the vanilla armor
 * comparison basis: total {@link Attributes#ARMOR} points from the item's attribute
 * modifiers, with {@link Attributes#ARMOR_TOUGHNESS} as tie-break. Swaps go through the
 * player's own {@link InventoryMenu} with PICKUP clicks — the exact container-click packets
 * a player dragging the piece produces, so the server stays in sync (an empty slot is
 * filled; an occupied one is exchanged, the old piece landing back where the new one was).
 *
 * <p>Re-evaluates when the inventory changed ({@code Inventory#getTimesChanged()} — fires
 * on pickups, loot, syncs) or every {@code check_interval} ticks, whichever comes first,
 * and never while a container screen is open.</p>
 */
public class AutoArmor extends AbstractModule {

	private static final AutoArmor Instance = new AutoArmor();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_armor";

	/** Worn-slot menu indexes in the player's own inventory menu (ARMOR_SLOT_START order). */
	private static final Map<EquipmentSlot, Integer> ARMOR_MENU_SLOTS = Map.of(
		EquipmentSlot.HEAD, InventoryMenu.ARMOR_SLOT_START,
		EquipmentSlot.CHEST, InventoryMenu.ARMOR_SLOT_START + 1,
		EquipmentSlot.LEGS, InventoryMenu.ARMOR_SLOT_START + 2,
		EquipmentSlot.FEET, InventoryMenu.ARMOR_SLOT_START + 3);

	/** QoL tier: on by default, only a server deny locks it. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", true);
	/** Re-check interval in ticks when the inventory is not otherwise changing. */
	public final IntegerOption checkInterval = new IntegerOption("check_interval", 20, 5, 200);

	private final OptionCategory category = OptionCategory.create("autoarmor");

	private int lastTimesChanged = -1;
	private int cooldown;

	public static AutoArmor getInstance() {
		return Instance;
	}

	private AutoArmor() {
	}

	@Override
	public void init() {
		category.add(enabled, checkInterval);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_ARMOR, enabled);
	}

	@Override
	public void tick() {
		ServerGating.enforceQoL(enabled, ServerGating.AUTO_ARMOR, LOCK_REASON_KEY);
		LocalPlayer player = client.player;
		if (!enabled.get() || player == null || client.gui.screen() != null) {
			cooldown = 0;
			return;
		}

		Inventory inventory = player.getInventory();
		int timesChanged = inventory.getTimesChanged();
		boolean inventoryChanged = timesChanged != lastTimesChanged;
		lastTimesChanged = timesChanged;
		if (!inventoryChanged && cooldown-- > 0) {
			return;
		}
		cooldown = checkInterval.get();

		equipBetterArmor(player);
	}

	/** One pass over the four worn slots, equipping strictly better pieces found in the inventory. */
	private void equipBetterArmor(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = player.getItemBySlot(slot);
			int candidate = bestInventoryArmor(inventory, slot, armorScore(worn));
			if (candidate < 0) {
				continue;
			}
			int menuSlot = menuSlotForInventoryIndex(candidate);
			int armorSlot = ARMOR_MENU_SLOTS.get(slot);
			// Pick the piece up, drop it into the worn slot (vanilla swaps if occupied), put
			// whatever is left on the cursor back where the piece came from.
			player.inventoryMenu.clicked(menuSlot, 0, ContainerInput.PICKUP, player);
			player.inventoryMenu.clicked(armorSlot, 0, ContainerInput.PICKUP, player);
			if (!player.inventoryMenu.getCarried().isEmpty()) {
				player.inventoryMenu.clicked(menuSlot, 0, ContainerInput.PICKUP, player);
			}
			return; // one slot per pass; the inventory-change signal re-triggers the rest
		}
	}

	/**
	 * Best strictly-better-than-{@code currentScore} armor piece for {@code slot} among the
	 * carried items (hotbar + main inventory, indexes 0-35). Earliest index wins ties.
	 */
	@VisibleForTesting
	public static int bestInventoryArmor(Inventory inventory, EquipmentSlot slot, double currentScore) {
		int best = -1;
		double bestScore = currentScore;
		int size = Math.min(Inventory.INVENTORY_SIZE, inventory.getNonEquipmentItems().size());
		for (int index = 0; index < size; index++) {
			ItemStack stack = inventory.getItem(index);
			if (stack.isEmpty()) {
				continue;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable == null || equippable.slot() != slot) {
				continue;
			}
			double score = armorScore(stack);
			if (score > bestScore) {
				best = index;
				bestScore = score;
			}
		}
		return best;
	}

	/** Armor points carried by the stack (armor attribute total, toughness as tie-break weight). */
	@VisibleForTesting
	public static double armorScore(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}
		var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return 0.0;
		}
		double armor = 0.0;
		double toughness = 0.0;
		for (var entry : modifiers.modifiers()) {
			if (entry.attribute() == Attributes.ARMOR) {
				armor += entry.modifier().amount();
			} else if (entry.attribute() == Attributes.ARMOR_TOUGHNESS) {
				toughness += entry.modifier().amount();
			}
		}
		return armor + toughness * 0.01;
	}

	/** Inventory index → slot index in the player's own inventory menu (hotbar sits after main). */
	private static int menuSlotForInventoryIndex(int index) {
		return Inventory.isHotbarSlot(index) ? index + InventoryMenu.USE_ROW_SLOT_START : index;
	}
}
