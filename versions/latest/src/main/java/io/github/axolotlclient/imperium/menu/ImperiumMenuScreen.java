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

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
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
 */
public class ImperiumMenuScreen extends Screen {
	private static final int BG = 0xE6100D10;
	private static final int SIDEBAR = 0xF0181316;
	private static final int CARD = 0xFF211A1F;
	private static final int CARD_HOVER = 0xFF2B2228;
	private static final int GOLD = 0xFFD4AF37;
	private static final int CRIMSON = 0xFF8E1F2F;
	private static final int TEXT = 0xFFEDE6DA;
	private static final int MUTED = 0xFF9A8F86;
	private static final int TOGGLE_OFF = 0xFF4A4045;

	private static final int HEADER_H = 30;
	private static final int FOOTER_H = 28;
	private static final int SIDEBAR_W = 92;
	private static final int ROW_H = 18;
	private static final int CARD_H = 44;
	private static final int GAP = 6;
	private static final int GEAR_SIZE = 12;

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

	// rendering

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		var ctx = (AxoRenderContext) graphics;
		ctx.br$fillRectRound(panelX, panelY, panelW, panelH, BG, 6);
		ctx.br$fillRectRoundVarying(panelX, panelY + HEADER_H, SIDEBAR_W, panelH - HEADER_H, SIDEBAR, 0, 6, 0, 0);
		graphics.fill(panelX, panelY + HEADER_H - 1, panelX + panelW, panelY + HEADER_H, CRIMSON);
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, Component.translatable("imperium.menu.title"), panelX + 10, panelY + 11, GOLD, false);
		extractSidebar(graphics, mouseX, mouseY);
		extractCards(graphics, mouseX, mouseY);
	}

	private void extractSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = panelY + HEADER_H + 6;
		for (MenuCategory c : MenuCategory.values()) {
			boolean selected = c == category;
			boolean hovered = mouseX >= panelX && mouseX < panelX + SIDEBAR_W && mouseY >= y && mouseY < y + ROW_H;
			if (selected) {
				graphics.fill(panelX, y, panelX + 2, y + ROW_H, GOLD);
				graphics.fill(panelX + 2, y, panelX + SIDEBAR_W, y + ROW_H, 0x30D4AF37);
			} else if (hovered) {
				graphics.fill(panelX, y, panelX + SIDEBAR_W, y + ROW_H, 0x18FFFFFF);
			}
			graphics.text(font, Component.translatable(c.translationKey()), panelX + 10, y + 5, selected ? GOLD : TEXT, false);
			y += ROW_H;
		}
	}

	private void extractCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var ctx = (AxoRenderContext) graphics;
		if (visible.isEmpty()) {
			graphics.centeredText(font, Component.translatable("imperium.menu.no_results"),
				contentX() + contentW() / 2, contentY() + contentH() / 2 - 4, MUTED);
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
			ctx.br$fillRectRound(x, y, w, CARD_H, hovered ? CARD_HOVER : CARD, 4);
			if (entry.enabled()) {
				ctx.br$outlineRectRound(x, y, w, CARD_H, GOLD, 4);
			}
			graphics.text(font, font.plainSubstrByWidth(entry.name(), w - 12), x + 6, y + 7, TEXT, false);

			if (entry.hasToggle()) {
				int tx = x + 6, ty = y + CARD_H - 15;
				boolean on = entry.enabled();
				ctx.br$fillRectRound(tx, ty, 22, 10, on ? GOLD : TOGGLE_OFF, 5);
				ctx.br$fillRectRound(on ? tx + 13 : tx + 1, ty + 1, 8, 8, 0xFFFFFFFF, 4);
				graphics.text(font, Component.translatable(on ? "options.on" : "options.off"), tx + 27, ty + 1, on ? GOLD : MUTED, false);
			}

			int gx = x + w - GEAR_SIZE - 5, gy = y + CARD_H - GEAR_SIZE - 4;
			boolean gearHovered = hovered && mouseX >= gx && mouseX < gx + GEAR_SIZE && mouseY >= gy && mouseY < gy + GEAR_SIZE;
			graphics.text(font, "⚙", gx + 2, gy + 2, gearHovered ? GOLD : MUTED, false);
		}
		graphics.disableScissor();

		double max = maxScroll();
		if (max > 0) {
			int trackX = contentX() + contentW() + 2;
			int thumbH = Math.max(16, (int) (contentH() * (contentH() / (contentH() + max))));
			int thumbY = contentY() + (int) ((contentH() - thumbH) * (scroll / max));
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80D4AF37);
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
