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

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.mixin.MinecraftClientAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Eat (wave 4 native utility) — QoL tier per docs/server-gated-features.md: ships
 * enabled (singleplayer-style default), and a server can only force it off through a
 * {@code deny} (features channel) or the {@code block_mods} channel, both via
 * {@link ServerGating#enforceQoL}.
 *
 * <p>When the hunger bar drops below the threshold (default 14) the module selects the
 * best food stack in the hotbar and eats it through the vanilla right-click path:
 * {@code Minecraft#startUseItem} (the AutoFish invoker) starts the eat, and the use key is
 * held ({@code Options.keyUse.setDown}) for as long as the eat runs because vanilla's
 * {@code handleKeybinds} releases item use the moment the key is up. No packets are
 * injected beyond what a held right-click produces; the selected-slot swap rides the
 * vanilla carried-item sync. The previously held slot is restored afterwards when
 * {@code swap_back} is on.</p>
 */
public class AutoEat extends AbstractModule {

	private static final AutoEat Instance = new AutoEat();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_eat";

	/** QoL tier: on by default, only a server deny locks it. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", true);
	/** Start eating when the hunger bar drops below this level (vanilla scale 0-20). */
	public final IntegerOption hungerThreshold = new IntegerOption("hunger_threshold", 14, 1, 19);
	/** Put the previously held item back in hand once the eat finishes. */
	public final BooleanOption swapBack = new BooleanOption("swap_back", true);

	private final OptionCategory category = OptionCategory.create("autoeat");

	/** True while WE hold the use key for an eat in progress (a physical press is never touched). */
	private boolean eatingByUs;
	/** Hotbar slot held before we swapped to food; -1 while we hold the player's own selection. */
	private int previousSlot = -1;

	public static AutoEat getInstance() {
		return Instance;
	}

	private AutoEat() {
	}

	@Override
	public void init() {
		category.add(enabled, hungerThreshold, swapBack);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		// Server-denyable through the block_mods channel too (FeatureDisabler pattern).
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_EAT, enabled);
		Events.DISCONNECT.register(this::stop);
	}

	@Override
	public void tick() {
		// Re-evaluated every tick: a mid-session deny stops an in-flight eat on the next tick.
		ServerGating.enforceQoL(enabled, ServerGating.AUTO_EAT, LOCK_REASON_KEY);
		if (!enabled.get()) {
			stop();
			return;
		}

		LocalPlayer player = client.player;
		if (player == null) {
			stop();
			return;
		}
		if (client.gui.screen() != null) {
			return; // menu/chat open: hold state (handleKeybinds is skipped anyway), never click through screens
		}

		if (player.isUsingItem()) {
			if (!eatingByUs) {
				return; // the player is using an item themselves — do not interfere
			}
			if (!isEdible(player.getMainHandItem())) {
				// the stack ran out (or the hand changed underneath us): stop holding use
				stop();
				return;
			}
			// keep the simulated key held for the whole eat duration
			client.options.keyUse.setDown(true);
			return;
		}

		if (eatingByUs) {
			// eat finished: release and restore the hand
			stop();
		}

		if (player.getFoodData().getFoodLevel() >= hungerThreshold.get() || !player.canEat(false)) {
			return;
		}

		int foodSlot = chooseFoodSlot(player.getInventory());
		if (foodSlot < 0) {
			return; // nothing edible in the hotbar
		}

		Inventory inventory = player.getInventory();
		if (inventory.getSelectedSlot() != foodSlot) {
			previousSlot = inventory.getSelectedSlot();
			inventory.setSelectedSlot(foodSlot);
		}
		eatingByUs = true;
		client.options.keyUse.setDown(true);
		((MinecraftClientAccessor) client).axolotlclient$startUseItem();
	}

	/** Releases the simulated use key and restores the pre-eat selection (if {@code swap_back}). */
	private void stop() {
		if (eatingByUs) {
			client.options.keyUse.setDown(false);
		}
		eatingByUs = false;
		if (previousSlot >= 0 && swapBack.get() && client.player != null) {
			Inventory inventory = client.player.getInventory();
			if (inventory.getSelectedSlot() != previousSlot && !client.player.isUsingItem()) {
				inventory.setSelectedSlot(previousSlot);
			}
		}
		previousSlot = -1;
	}

	static boolean isEdible(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD);
	}

	/**
	 * Best hotbar slot to eat: highest nutrition first, saturation as tie-break, earliest
	 * slot on full ties. Pure over {@link Inventory#getNonEquipmentItems()} (hotbar is
	 * indexes 0-8) so the choice is unit-testable without a world.
	 */
	@VisibleForTesting
	public static int chooseFoodSlot(Inventory inventory) {
		int bestSlot = -1;
		int bestNutrition = -1;
		float bestSaturation = -1.0F;
		for (int slot = 0; slot < Math.min(Inventory.getSelectionSize(), inventory.getNonEquipmentItems().size()); slot++) {
			FoodProperties food = inventory.getItem(slot).get(DataComponents.FOOD);
			if (food == null) {
				continue;
			}
			if (food.nutrition() > bestNutrition
				|| (food.nutrition() == bestNutrition && food.saturation() > bestSaturation)) {
				bestSlot = slot;
				bestNutrition = food.nutrition();
				bestSaturation = food.saturation();
			}
		}
		return bestSlot;
	}
}
