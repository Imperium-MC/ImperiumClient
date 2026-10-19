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

package io.github.axolotlclient.imperium.menu;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.config.screen.ProfilesScreen;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.annotations.Nullable;

/**
 * Alpine-style module menu: category sidebar, search, and a grid of module cards. Clicking a card
 * toggles the module; its gear opens the module's settings.
 *
 * <p>Two themes: CARVED (default) paints the carved imperial identity from {@link CarvedTheme} —
 * chiseled stone panels, bronze hairlines, engraved capitals and item sprites in the cards; CLASSIC
 * keeps the flat dark-and-gold look the menu originally shipped with.
 */
public class ImperiumMenuScreen extends Screen {
	// CLASSIC palette
	private static final int BG = 0xE6100D10;
	private static final int SIDEBAR = 0xF0181316;
	private static final int CARD = 0xFF211A1F;
	private static final int CARD_HOVER = 0xFF2B2228;
	private static final int GOLD = 0xFFD4AF37;
	private static final int CRIMSON = 0xFF8E1F2F;
	private static final int TEXT = 0xFFEDE6DA;
	private static final int MUTED = 0xFF9A8F86;
	private static final int TOGGLE_OFF = 0xFF4A4045;

	// CARVED palette (see CarvedTheme for the full ramp)
	private static final int SIDEBAR_CARVED = 0xF013100D;
	private static final int CARD_NAME_ON = 0xFFE0D1B8;
	private static final int CARD_NAME_OFF = 0xFFC7BEB2;

	private static final int HEADER_H = 30;
	private static final int FOOTER_H = 28;
	private static final int SIDEBAR_W = 92;
	private static final int ROW_H = 18;
	private static final int CARD_H = 44;
	private static final int GAP = 6;
	private static final int GEAR_SIZE = 12;
	private static final int ICON_SIZE = 16;

	private static MenuCategory lastCategory = MenuCategory.ALL;

	@Nullable
	private final Screen parent;
	private List<MenuEntry> allEntries = List.of();
	private List<MenuEntry> visible = List.of();
	private MenuCategory category = lastCategory;
	private String query = "";
	private double scroll;
	private EditBox search;

	private int panelX, panelY, panelW, panelH;

	public ImperiumMenuScreen(@Nullable Screen parent) {
		super(Component.translatable("imperium.menu.title"));
		this.parent = parent;
	}

	private static boolean carved() {
		return AxolotlClient.config().menuTheme.get() == AxolotlClientConfigCommon.MenuTheme.CARVED;
	}

	@Override
	protected void init() {
		allEntries = MenuEntries.build();

		panelW = Math.min(width - 24, 560);
		panelH = Math.min(height - 24, 340);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;

		search = new EditBox(font, panelX + panelW - 138, panelY + 7, 130, 16, Component.translatable("imperium.menu.search"));
		search.setHint(Component.translatable("imperium.menu.search"));
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			scroll = 0;
			refilter();
		});
		addRenderableWidget(search);
		// Typing should search straight away.
		setInitialFocus(search);

		int buttonY = panelY + panelH - FOOTER_H + 4;
		int buttonX = panelX + SIDEBAR_W + GAP;
		addRenderableWidget(Button.builder(Component.translatable("imperium.menu.edit_hud"),
			_ -> minecraft.gui.setScreen(new HudEditScreen(this))).bounds(buttonX, buttonY, 80, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("imperium.menu.profiles"),
			_ -> minecraft.gui.setScreen(new ProfilesScreen(this))).bounds(buttonX + 84, buttonY, 80, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("imperium.menu.all_settings"),
			_ -> minecraft.gui.setScreen(ConfigStyles.createScreen(this, AxolotlClientCommon.getInstance().getConfigManager().getRoot())))
			.bounds(buttonX + 168, buttonY, 90, 20).build());

		refilter();
	}

	private void refilter() {
		visible = allEntries.stream()
			.filter(e -> category == MenuCategory.ALL || e.category() == category)
			.filter(e -> e.matches(query))
			.toList();
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	// layout

	private int contentX() {
		return panelX + SIDEBAR_W + GAP;
	}

	private int contentY() {
		return panelY + HEADER_H + GAP;
	}

	private int contentW() {
		return panelW - SIDEBAR_W - GAP * 2;
	}

	private int contentH() {
		return panelH - HEADER_H - FOOTER_H - GAP;
	}

	private int columns() {
		return Math.max(2, (contentW() + GAP) / (120 + GAP));
	}

	private int cardW() {
		return (contentW() - GAP * (columns() - 1)) / columns();
	}

	private int cardX(int index) {
		return contentX() + (index % columns()) * (cardW() + GAP);
	}

	private int cardY(int index) {
		return contentY() + (index / columns()) * (CARD_H + GAP) - (int) scroll;
	}

	private double maxScroll() {
		int rows = (visible.size() + columns() - 1) / columns();
		return Math.max(0, rows * (CARD_H + GAP) - GAP - contentH());
	}

	private boolean inContent(double x, double y) {
		return x >= contentX() && x < contentX() + contentW() && y >= contentY() && y < contentY() + contentH();
	}

	@VisibleForTesting
	public List<MenuEntry> visibleEntries() {
		return visible;
	}

	/** Centre of the card body (away from the gear), in GUI coordinates. */
	@VisibleForTesting
	public int[] cardBodyCenter(int index) {
		return new int[]{cardX(index) + cardW() / 3, cardY(index) + CARD_H / 2};
	}

	/** Centre of the card's settings gear, in GUI coordinates. */
	@VisibleForTesting
	public int[] cardGearCenter(int index) {
		return new int[]{cardX(index) + cardW() - GEAR_SIZE / 2 - 5, cardY(index) + CARD_H - GEAR_SIZE / 2 - 4};
	}

	/** Centre of a sidebar category row, in GUI coordinates. */
	@VisibleForTesting
	public int[] categoryCenter(MenuCategory target) {
		int index = 0;
		for (MenuCategory c : MenuCategory.values()) {
			if (c == target) break;
			index++;
		}
		int y = panelY + HEADER_H + 6 + index * ROW_H;
		return new int[]{panelX + SIDEBAR_W / 2, y + ROW_H / 2};
	}

	// rendering

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		if (carved()) {
			CarvedTheme.carvedPanel(graphics, panelX, panelY, panelW, panelH);
			// sidebar rail
			graphics.fill(panelX + 1, panelY + HEADER_H, panelX + SIDEBAR_W - 1, panelY + panelH - 1, SIDEBAR_CARVED);
			graphics.fill(panelX + SIDEBAR_W - 1, panelY + HEADER_H, panelX + SIDEBAR_W, panelY + panelH - 1, CarvedTheme.BRONZE_DARK);
			CarvedTheme.carvedRule(graphics, panelX, panelY + HEADER_H - 2, panelW);
			CarvedTheme.carvedRule(graphics, panelX, panelY + panelH - FOOTER_H, panelW);
			return;
		}
		var ctx = (AxoRenderContext) graphics;
		ctx.br$fillRectRound(panelX, panelY, panelW, panelH, BG, 6);
		ctx.br$fillRectRoundVarying(panelX, panelY + HEADER_H, SIDEBAR_W, panelH - HEADER_H, SIDEBAR, 0, 6, 0, 0);
		graphics.fill(panelX, panelY + HEADER_H - 1, panelX + panelW, panelY + HEADER_H, CRIMSON);
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (carved()) {
			CarvedTheme.engravedCaps(graphics, font, Component.translatable("imperium.menu.title").getString(),
				panelX + 10, panelY + 11, CarvedTheme.BRONZE_LIGHT);
		} else {
			graphics.text(font, Component.translatable("imperium.menu.title"), panelX + 10, panelY + 11, GOLD, false);
		}
		extractSidebar(graphics, mouseX, mouseY);
		extractCards(graphics, mouseX, mouseY);
	}

	private void extractSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = panelY + HEADER_H + 6;
		for (MenuCategory c : MenuCategory.values()) {
			boolean selected = c == category;
			boolean hovered = mouseX >= panelX && mouseX < panelX + SIDEBAR_W && mouseY >= y && mouseY < y + ROW_H;
			if (carved()) {
				if (selected) {
					// the row is pressed into the rail: shadow top/left, catch-light bottom/right
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + ROW_H, 0x33A8823F);
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + 1, 0x660B0908);
					graphics.fill(panelX + 1, y, panelX + 2, y + ROW_H, 0x660B0908);
					graphics.fill(panelX + 1, y + ROW_H - 1, panelX + SIDEBAR_W - 1, y + ROW_H, CarvedTheme.BRONZE_DARK);
					// carved marker: a bronze wedge anchored to the rail's edge
					graphics.fill(panelX + 2, y + 2, panelX + 5, y + ROW_H - 2, CarvedTheme.BRONZE);
					graphics.fill(panelX + 2, y + 2, panelX + 5, y + 3, CarvedTheme.BRONZE_LIGHT);
				} else if (hovered) {
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + ROW_H, 0x1ACEA464);
				}
				CarvedTheme.engravedText(graphics, font, Component.translatable(c.translationKey()).getString().toUpperCase(Locale.ROOT),
					panelX + 9, y + 5, selected ? CarvedTheme.BRONZE_LIGHT : 0xFFCFC5B4);
			} else {
				if (selected) {
					graphics.fill(panelX, y, panelX + 2, y + ROW_H, GOLD);
					graphics.fill(panelX + 2, y, panelX + SIDEBAR_W, y + ROW_H, 0x30D4AF37);
				} else if (hovered) {
					graphics.fill(panelX, y, panelX + SIDEBAR_W, y + ROW_H, 0x18FFFFFF);
				}
				graphics.text(font, Component.translatable(c.translationKey()), panelX + 10, y + 5, selected ? GOLD : TEXT, false);
			}
			y += ROW_H;
		}
	}

	private void extractCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var ctx = (AxoRenderContext) graphics;
		boolean carved = carved();
		if (visible.isEmpty()) {
			if (carved) {
				CarvedTheme.engravedText(graphics, font, Component.translatable("imperium.menu.no_results").getString(),
					contentX() + contentW() / 2 - font.width(Component.translatable("imperium.menu.no_results").getString()) / 2,
					contentY() + contentH() / 2 - 4, CarvedTheme.MUTED);
			} else {
				graphics.centeredText(font, Component.translatable("imperium.menu.no_results"),
					contentX() + contentW() / 2, contentY() + contentH() / 2 - 4, MUTED);
			}
			return;
		}
		graphics.enableScissor(contentX(), contentY(), contentX() + contentW(), contentY() + contentH());
		int w = cardW();
		for (int i = 0; i < visible.size(); i++) {
			int x = cardX(i);
			int y = cardY(i);
			if (y + CARD_H < contentY() || y > contentY() + contentH()) continue;
			MenuEntry entry = visible.get(i);
			boolean hovered = inContent(mouseX, mouseY) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H;
			boolean enabled = entry.enabled();
			if (carved) {
				CarvedTheme.raisedTile(graphics, x, y, w, CARD_H, hovered, enabled);
				drawCarvedCardContent(graphics, entry, x, y, w, hovered, enabled);
			} else {
				ctx.br$fillRectRound(x, y, w, CARD_H, hovered ? CARD_HOVER : CARD, 4);
				if (enabled) {
					ctx.br$outlineRectRound(x, y, w, CARD_H, GOLD, 4);
				}
				graphics.text(font, font.plainSubstrByWidth(entry.name(), w - 12), x + 6, y + 7, TEXT, false);
				if (entry.hasToggle()) {
					int tx = x + 6, ty = y + CARD_H - 15;
					ctx.br$fillRectRound(tx, ty, 22, 10, enabled ? GOLD : TOGGLE_OFF, 5);
					ctx.br$fillRectRound(enabled ? tx + 13 : tx + 1, ty + 1, 8, 8, 0xFFFFFFFF, 4);
					graphics.text(font, Component.translatable(enabled ? "options.on" : "options.off"), tx + 27, ty + 1, enabled ? GOLD : MUTED, false);
				}
			}

			int gx = x + w - GEAR_SIZE - 5, gy = y + CARD_H - GEAR_SIZE - 4;
			boolean gearHovered = hovered && mouseX >= gx && mouseX < gx + GEAR_SIZE && mouseY >= gy && mouseY < gy + GEAR_SIZE;
			graphics.text(font, "⚙", gx + 2, gy + 2, gearHovered ? (carved ? CarvedTheme.BRONZE : GOLD) : (carved ? CarvedTheme.MUTED : MUTED), false);
		}
		graphics.disableScissor();

		double max = maxScroll();
		if (max > 0) {
			int trackX = contentX() + contentW() + 2;
			int thumbH = Math.max(16, (int) (contentH() * (contentH() / (contentH() + max))));
			int thumbY = contentY() + (int) ((contentH() - thumbH) * (scroll / max));
			if (carved) {
				graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, CarvedTheme.BRONZE_DARK);
				graphics.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, CarvedTheme.BRONZE);
			} else {
				graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80D4AF37);
			}
		}
	}

	/**
	 * Carved card interior: item sprite (or an empty carved slot) in a fixed left column, the
	 * module name beside it, and the bronze/marble toggle under the name.
	 */
	private void drawCarvedCardContent(GuiGraphicsExtractor graphics, MenuEntry entry, int x, int y, int w, boolean hovered, boolean enabled) {
		int iconX = x + 5, iconY = y + 5;
		CarvedTheme.carvedSlot(graphics, iconX, iconY, ICON_SIZE);
		var icon = CarvedTheme.iconFor(entry);
		if (icon != null) {
			graphics.fakeItem(icon, iconX, iconY);
		}

		int textX = iconX + ICON_SIZE + 4;
		int nameColor = enabled ? CARD_NAME_ON : CARD_NAME_OFF;
		graphics.text(font, font.plainSubstrByWidth(entry.name(), w - (textX - x) - GEAR_SIZE - 4), textX, y + 7, hovered ? CarvedTheme.MARBLE : nameColor, false);

		if (entry.hasToggle()) {
			int tx = textX - 1, ty = y + CARD_H - 15;
			CarvedTheme.carvedToggle(graphics, tx, ty, enabled);
			graphics.text(font, Component.translatable(enabled ? "options.on" : "options.off"), tx + 27, ty + 1,
				enabled ? CarvedTheme.BRONZE : CarvedTheme.MUTED, false);
		}
	}

	// input

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();

		int y = panelY + HEADER_H + 6;
		for (MenuCategory c : MenuCategory.values()) {
			if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= y && my < y + ROW_H) {
				category = c;
				lastCategory = c;
				scroll = 0;
				refilter();
				return true;
			}
			y += ROW_H;
		}

		if (!inContent(mx, my)) return false;
		int w = cardW();
		for (int i = 0; i < visible.size(); i++) {
			int x = cardX(i), cy = cardY(i);
			if (mx < x || mx >= x + w || my < cy || my >= cy + CARD_H) continue;
			MenuEntry entry = visible.get(i);
			int gx = x + w - GEAR_SIZE - 5, gy = cy + CARD_H - GEAR_SIZE - 4;
			boolean onGear = mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE;
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT || onGear || !entry.hasToggle()) {
				minecraft.gui.setScreen(ConfigStyles.createScreen(this, entry.settings()));
			} else {
				entry.toggle();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (inContent(mouseX, mouseY)) {
			scroll = Mth.clamp(scroll - scrollY * (CARD_H + GAP) / 2, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
