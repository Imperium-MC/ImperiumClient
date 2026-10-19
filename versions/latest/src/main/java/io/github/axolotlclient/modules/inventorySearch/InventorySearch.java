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

package io.github.axolotlclient.modules.inventorySearch;

import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.AbstractModule;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Alpine parity: a search bar overlaid on any open container screen. Typing highlights the slots
 * whose item name matches (case-insensitive "contains") and dims the rest. The overlay is purely
 * visual — the container keeps its layout and stays usable; only key events are captured while
 * the search field is up.
 */
public class InventorySearch extends AbstractModule {

	@Getter
	private static final InventorySearch Instance = new InventorySearch();

	public final BooleanOption enabled = new BooleanOption("enabled", true);
	public final EnumOption<BarPosition> searchBarPosition = new EnumOption<>("search_bar_position", BarPosition.class, BarPosition.TOP);
	public final IntegerOption searchBarWidth = new IntegerOption("search_bar_width", 140, 60, 300);
	public final IntegerOption searchBarHeight = new IntegerOption("search_bar_height", 16, 10, 24);
	public final BooleanOption highlightSlot = new BooleanOption("highlight_slot", true);
	public final ColorOption highlightColor = new ColorOption("highlight_color", new Color(0xFFD4AF37));
	public final ColorOption dimColor = new ColorOption("dim_color", new Color(0x96000000));

	public final AxoKeybinding searchKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.inventory_search.open");

	private final OptionCategory category = OptionCategory.create("inventorySearch");

	private boolean active;
	@Nullable
	private EditBox searchField;

	@Override
	public void init() {
		category.add(enabled, searchBarPosition, searchBarWidth, searchBarHeight, highlightSlot, highlightColor, dimColor);

		AxolotlClient.config().general.add(category);
	}

	public boolean isActive() {
		return active;
	}

	/**
	 * Key routing for container screens. The overlay opens on the bound search key; while it is up,
	 * every key event is swallowed by the search field so nothing leaks into the container (no
	 * hotbar swaps, no E-to-close). Escape or the search key again dismisses it.
	 */
	public boolean onKeyPressed(KeyEvent event) {
		if (!enabled.get()) {
			return false;
		}
		KeyMapping key = (KeyMapping) (Object) searchKey;
		if (active) {
			if (event.key() == InputConstants.KEY_ESCAPE || key.matches(event)) {
				close();
			} else if (searchField != null) {
				searchField.keyPressed(event);
			}
			return true;
		}
		if (key.matches(event)) {
			open();
			return true;
		}
		return false;
	}

	public boolean onCharTyped(CharacterEvent event) {
		if (active && searchField != null) {
			searchField.charTyped(event);
		}
		return active;
	}

	/** Clicks on the search bar focus it; everything else falls through to the container. */
	public boolean onMouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!active || searchField == null) {
			return false;
		}
		double x = event.x(), y = event.y();
		if (x >= searchField.getX() && x < searchField.getX() + searchField.getWidth()
			&& y >= searchField.getY() && y < searchField.getY() + searchField.getHeight()) {
			searchField.setFocused(true);
			searchField.onClick(event, doubleClick);
			return true;
		}
		return false;
	}

	public void renderOverlay(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
		int mouseX, int mouseY, float partialTick, int leftPos, int topPos, int imageWidth, int imageHeight) {
		if (!active || !enabled.get() || searchField == null) {
			return;
		}
		positionBar(screen, leftPos, topPos, imageWidth, imageHeight);

		String query = searchField.getValue().trim().toLowerCase(Locale.ROOT);
		if (!query.isEmpty()) {
			AxoRenderContext ctx = (AxoRenderContext) graphics;
			int dim = dimColor.get().toInt();
			int fill = highlightColor.get().withAlpha(0x40).toInt();
			int outline = highlightColor.get().toInt();
			int size = AbstractContainerMenu.SLOT_SIZE;
			for (Slot slot : screen.getMenu().slots) {
				int x = leftPos + slot.x;
				int y = topPos + slot.y;
				if (!slot.hasItem() || !matches(slot, query)) {
					graphics.fill(x, y, x + size, y + size, dim);
				} else if (highlightSlot.get()) {
					ctx.br$fillRectRound(x, y, size, size, fill, 2);
					ctx.br$outlineRectRound(x, y, size, size, outline, 2);
				}
			}
		}
		// Drawn after the slot tints, still below the carried item and the tooltip.
		searchField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
	}

	public void onScreenRemoved() {
		close();
	}

	private void open() {
		active = true;
		searchField = new EditBox(client.font, 0, 0, searchBarWidth.get(), searchBarHeight.get(),
			Component.translatable("inventory_search.hint"));
		searchField.setMaxLength(64);
		searchField.setHint(Component.translatable("inventory_search.hint"));
		searchField.setCanLoseFocus(false);
		searchField.setFocused(true);
	}

	private void close() {
		active = false;
		searchField = null;
	}

	private void positionBar(AbstractContainerScreen<?> screen, int leftPos, int topPos, int imageWidth, int imageHeight) {
		int width = searchBarWidth.get();
		int height = searchBarHeight.get();
		int x = leftPos + (imageWidth - width) / 2;
		int y = searchBarPosition.get() == BarPosition.TOP ? topPos - height - 4 : topPos + imageHeight + 4;
		searchField.setX(x);
		searchField.setY(Mth.clamp(y, 2, Math.max(2, screen.height - height - 2)));
	}

	private boolean matches(Slot slot, String query) {
		String name = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString());
		return name != null && name.toLowerCase(Locale.ROOT).contains(query);
	}

	/** Current query text (empty while the overlay is closed). */
	@VisibleForTesting
	public String query() {
		return searchField == null ? "" : searchField.getValue();
	}

	/** Number of slots the current query matches; -1 for a non-container screen or empty query. */
	@VisibleForTesting
	public int matchingSlots(net.minecraft.client.gui.screens.Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return -1;
		}
		String query = query().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			return -1;
		}
		int count = 0;
		for (Slot slot : container.getMenu().slots) {
			if (slot.hasItem() && matches(slot, query)) {
				count++;
			}
		}
		return count;
	}

	public enum BarPosition {
		TOP, BOTTOM;

		@Override
		public String toString() {
			return "search_bar_position." + name().toLowerCase(Locale.ROOT);
		}
	}
}
