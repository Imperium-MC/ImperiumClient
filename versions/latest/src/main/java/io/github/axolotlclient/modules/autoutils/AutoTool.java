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
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Tool (wave 4 native utility) — QoL tier per docs/server-gated-features.md: ships
 * enabled, only a server deny (features or block_mods channel) force-offs it via
 * {@link ServerGating#enforceQoL}.
 *
 * <p>Before a block break starts (or continues on a fresh block), the hotbar selection is
 * swapped to the tool with the highest vanilla {@link ItemStack#getDestroySpeed(BlockState)}
 * for that block state — the same figure {@code Player#getDigSpeed} builds the break
 * progress on, so "best tool" is the vanilla definition, enchantments included. The swap
 * happens at the HEAD of {@code MultiPlayerGameMode#startDestroyBlock}/{@code continueDestroyBlock}
 * (see {@code MultiPlayerGameModeMixin}), so the selection is already correct when vanilla
 * sends the carried-item packet for the break. It only ever swaps while mining is being
 * requested; when mining stops the previously held slot is restored (optional).</p>
 */
public class AutoTool extends AbstractModule {

	private static final AutoTool Instance = new AutoTool();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_tool";

	/** QoL tier: on by default, only a server deny locks it. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", true);
	/** Put the previously held item back in hand once mining stops. */
	public final BooleanOption swapBack = new BooleanOption("swap_back", true);

	private final OptionCategory category = OptionCategory.create("autotool");

	/** True while our swap is active (guards the swap-back). */
	private boolean swapped;
	/** Slot held before the swap; -1 while inactive. */
	private int previousSlot = -1;

	public static AutoTool getInstance() {
		return Instance;
	}

	private AutoTool() {
	}

	@Override
	public void init() {
		category.add(enabled, swapBack);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_TOOL, enabled);
		Events.DISCONNECT.register(this::restore);
	}

	/**
	 * Called from {@code MultiPlayerGameModeMixin} at the head of both destroy-block paths:
	 * swap to the best tool for {@code state} if the current selection is not already it.
	 * Strictly-better comparison — the current slot wins ties, so no hotbar churn on
	 * equal-speed blocks.
	 */
	public void beforeDestroy(BlockPos pos) {
		if (!enabled.get() || client.player == null) {
			return;
		}
		LocalPlayer player = client.player;
		BlockState state = player.level().getBlockState(pos);
		Inventory inventory = player.getInventory();
		int best = bestToolSlot(inventory, state);
		if (best < 0 || best == inventory.getSelectedSlot()) {
			return;
		}
		if (!swapped) {
			previousSlot = inventory.getSelectedSlot();
			swapped = true;
		}
		inventory.setSelectedSlot(best);
	}

	@Override
	public void tick() {
		ServerGating.enforceQoL(enabled, ServerGating.AUTO_TOOL, LOCK_REASON_KEY);
		if (!swapped) {
			return;
		}
		if (!enabled.get() || client.player == null) {
			restore();
			return;
		}
		// Mining session over: attack key released, crosshair left the block, or a screen opened.
		if (client.gui.screen() == null
			&& client.options.keyAttack.isDown()
			&& client.hitResult instanceof BlockHitResult) {
			return;
		}
		restore();
	}

	private void restore() {
		if (swapped && swapBack.get() && client.player != null) {
			Inventory inventory = client.player.getInventory();
			if (previousSlot >= 0 && previousSlot != inventory.getSelectedSlot()) {
				inventory.setSelectedSlot(previousSlot);
			}
		}
		swapped = false;
		previousSlot = -1;
	}

	/**
	 * Hotbar slot whose stack breaks {@code state} fastest, or -1 when nothing beats the
	 * current selection. Pure over the hotbar so it is unit-testable without a world.
	 */
	@VisibleForTesting
	public static int bestToolSlot(Inventory inventory, BlockState state) {
		int selected = inventory.getSelectedSlot();
		float bestSpeed = destroySpeed(inventory.getItem(selected), state);
		int bestSlot = -1;
		for (int slot = 0; slot < Math.min(Inventory.getSelectionSize(), inventory.getNonEquipmentItems().size()); slot++) {
			if (slot == selected) {
				continue;
			}
			float speed = destroySpeed(inventory.getItem(slot), state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}
		return bestSlot;
	}

	private static float destroySpeed(ItemStack stack, BlockState state) {
		return stack.isEmpty() ? 0.0F : stack.getDestroySpeed(state);
	}
}
