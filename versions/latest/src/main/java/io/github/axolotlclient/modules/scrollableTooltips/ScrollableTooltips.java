/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.scrollableTooltips;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.mixin.AbstractContainerScreenAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;

public class ScrollableTooltips extends AbstractModule {

	@Getter
	private static final ScrollableTooltips Instance = new ScrollableTooltips();
	public final BooleanOption enabled = new BooleanOption("enabled", false);
	public final BooleanOption enableShiftHorizontalScroll = new BooleanOption("shiftHorizontalScroll", true);
	protected final IntegerOption scrollAmount = new IntegerOption("scrollAmount", 5, 1, 20);
	protected final BooleanOption inverse = new BooleanOption("inverse", false);
	// Alpine parity: smooth_scroll glides the offset toward the target instead of snapping
	// by scrollAmount per notch; activation_key requires a modifier to be held before any
	// scrolling applies (NONE keeps it always active).
	public final BooleanOption smoothScroll = new BooleanOption("smooth_scroll", false);
	public final EnumOption<ActivationKey> activationKey = new EnumOption<>("activation_key", ActivationKey.class, ActivationKey.NONE);
	private final OptionCategory category = OptionCategory.create("scrollableTooltips");
	public int tooltipOffsetX;
	public int tooltipOffsetY;
	/** Smooth-scroll targets; the drawn offsets above ease toward these every tick. */
	private int targetOffsetX;
	private int targetOffsetY;

	/** Alpine activation_key: the modifier that must be held for scrolling to apply. */
	public enum ActivationKey {
		NONE,
		SHIFT,
		CONTROL,
		ALT;

		public boolean isDown(Minecraft client) {
			return switch (this) {
				case NONE -> true;
				case SHIFT -> client.hasShiftDown();
				case CONTROL -> client.hasControlDown();
				case ALT -> client.hasAltDown();
			};
		}

		@Override
		public String toString() {
			return "activation_key." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	@Override
	public void init() {
		category.add(enabled);
		category.add(enableShiftHorizontalScroll);
		category.add(scrollAmount);
		category.add(inverse);
		category.add(smoothScroll);
		category.add(activationKey);

		AxolotlClient.config().rendering.add(category);

		Events.TICK.register(() -> {
			if (!smoothScroll.get()) {
				tooltipOffsetX = targetOffsetX;
				tooltipOffsetY = targetOffsetY;
				return;
			}
			// Ease a third of the remaining distance per tick, always covering at least
			// one pixel so small nudges cannot stall short of the target.
			tooltipOffsetX += Math.copySign(Math.max(1, Math.abs(targetOffsetX - tooltipOffsetX) / 3), targetOffsetX - tooltipOffsetX);
			tooltipOffsetY += Math.copySign(Math.max(1, Math.abs(targetOffsetY - tooltipOffsetY) / 3), targetOffsetY - tooltipOffsetY);
			if (Math.abs(targetOffsetX - tooltipOffsetX) < 2) {
				tooltipOffsetX = targetOffsetX;
			}
			if (Math.abs(targetOffsetY - tooltipOffsetY) < 2) {
				tooltipOffsetY = targetOffsetY;
			}
		});
	}

	public boolean onScroll(boolean reverse) {
		if (client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
			if (!activationKey.get().isDown(Minecraft.getInstance())) {
				return false;
			}
			if ((Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen)
				&& ((CreativeModeInventoryScreen) Minecraft.getInstance().gui.screen())
				.getSelectedTab() != BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.INVENTORY)) {
				return false;
			}
			Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
			if (hovered == null || hovered.hasItem() && hovered.getItem().is(Items.BUNDLE) && !Minecraft.getInstance().hasControlDown()) {
				return false;
			}
			if (Minecraft.getInstance().hasShiftDown()) {
				if (applyInverse(reverse)) {
					targetOffsetX -= scrollAmount.get();
				} else {
					targetOffsetX += scrollAmount.get();
				}
			} else {
				if (applyInverse(reverse)) {
					targetOffsetY -= scrollAmount.get();
				} else {
					targetOffsetY += scrollAmount.get();
				}
			}
			if (!smoothScroll.get()) {
				tooltipOffsetX = targetOffsetX;
				tooltipOffsetY = targetOffsetY;
			}
			return true;
		}
		return false;
	}

	protected boolean applyInverse(boolean value) {
		return inverse.get() != value;
	}

	public void resetScroll() {
		tooltipOffsetY = tooltipOffsetX = 0;
		targetOffsetX = 0;
		targetOffsetY = 0;
	}
}
