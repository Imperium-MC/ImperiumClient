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

package io.github.axolotlclient.modules.inventoryTweaks;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.modules.AbstractModule;
import lombok.Getter;

/**
 * inventory_tweaks completion (wave 5): the two toggles Alpine groups under inventory_tweaks,
 * plus compact stack counts. Every option is standalone — none needs the custom PotionsHud.
 *
 * <ul>
 * <li>{@code hide_vanilla_potion_hud} — removes the vanilla potion-effect HUD everywhere: the
 * top-right icon stack {@code Hud#extractEffects} draws, and the icon panel
 * {@code EffectsInInventory} draws next to an open container. The custom PotionsHud keeps
 * working independently (it already cancels the vanilla HUD when it is enabled).</li>
 * <li>{@code keep_centered} — keeps container screens horizontally centered: the vanilla effect
 * panel that occupies the space right of the inventory is suppressed, so the container does not
 * sit off-center inside its composition, and {@code leftPos}/{@code topPos} are recomputed after
 * {@code AbstractContainerScreen#init} for any screen that shifted itself to make room. With the
 * panel gone, {@code canSeeEffects()} reports false, so the effect icons fall back to the
 * screen's top-right corner while a container is open — the inventory stays centered and the
 * effects stay visible unless the hide option removes them too.</li>
 * <li>{@code compact_item_counts} — stack counts at or above 10 000 switch to the compact
 * "1.2k"-style format (see {@link CompactCounts}).</li>
 * </ul>
 */
public class InventoryTweaks extends AbstractModule {

	@Getter
	private static final InventoryTweaks Instance = new InventoryTweaks();

	public final BooleanOption hideVanillaPotionHud = new BooleanOption("hide_vanilla_potion_hud", false);
	public final BooleanOption keepCentered = new BooleanOption("keep_centered", false);
	public final BooleanOption compactItemCounts = new BooleanOption("compact_item_counts", false);

	private final OptionCategory category = OptionCategory.create("inventoryTweaks");

	@Override
	public void init() {
		category.add(hideVanillaPotionHud, keepCentered, compactItemCounts);
		AxolotlClient.config().general.add(category);
	}

	/** Whether the vanilla effect icons must not be drawn at all (HUD corner or container panel). */
	public boolean hidesVanillaEffects() {
		return hideVanillaPotionHud.get();
	}

	/** Whether the in-inventory effect panel is suppressed so the container stays centered. */
	public boolean suppressesEffectPanel() {
		return keepCentered.get() || hideVanillaPotionHud.get();
	}

	/** Whether container screens must be (re-)centered by the mixin. */
	public boolean forcesCenteredContainers() {
		return keepCentered.get();
	}

	/** Formats a stack count, honouring the compact_item_counts toggle. */
	public String formatCount(int count) {
		return compactItemCounts.get() ? CompactCounts.format(count) : Integer.toString(count);
	}
}
