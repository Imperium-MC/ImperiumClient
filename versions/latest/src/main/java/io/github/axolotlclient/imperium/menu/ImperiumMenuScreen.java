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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoI18n;
import io.github.axolotlclient.config.screen.ProfilesScreen;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.annotations.Nullable;

/**
 * Alpine-style module menu: category sidebar, search, and a grid of module cards. Clicking a card
 * toggles the module; its gear opens the module's settings INLINE — an overlay panel drawn over the
 * card grid, built from the same themed widgets Axolotl's config screens use.
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
	private static final int SETTINGS_BG = 0xF0141013;

	// CARVED palette (see CarvedTheme for the full ramp)
	private static final int SIDEBAR_CARVED = 0xF013100D;
	private static final int CARD_NAME_ON = 0xFFE0D1B8;
	private static final int CARD_NAME_OFF = 0xFFC7BEB2;
	private static final int SETTINGS_TEXT_CARVED = 0xFFCFC5B4;

	private static final int HEADER_H = 30;
	private static final int FOOTER_H = 28;
	private static final int SIDEBAR_W = 92;
	private static final int ROW_H = 18;
	private static final int CARD_H = 44;
	private static final int GAP = 6;
	private static final int GEAR_SIZE = 12;
	private static final int ICON_SIZE = 16;

	// Inline settings panel metrics (all inside the content area, right of the sidebar).
	private static final int SETTINGS_TITLE_H = 20;
	private static final int SETTINGS_ROW_H = 22;
	private static final int SETTINGS_HEADER_H = 16;
	private static final int SETTINGS_WIDGET_W = 110;
	private static final int SETTINGS_WIDGET_H = 20;
	private static final int SETTINGS_INDENT = 8;
	private static final int SETTINGS_BACK_W = 46;
	private static final int SETTINGS_BACK_H = 14;

	private static MenuCategory lastCategory = MenuCategory.ALL;

	@Nullable
	private final Screen parent;
	private List<MenuEntry> allEntries = List.of();
	private List<MenuEntry> visible = List.of();
	private MenuCategory category = lastCategory;
	private String query = "";
	private double scroll;
	private EditBox search;
	private Button editHudButton;
	private Button profilesButton;
	private Button allSettingsButton;

	// The inline settings panel. While open, the card grid is not drawn and the search box /
	// footer buttons are parked (removed from the widget tree) so nothing steals input or peeks
	// out from under the panel; closing restores them and saves the config.
	@Nullable
	private MenuEntry settingsEntry;
	private final List<SettingsRow> settingsRows = new ArrayList<>();
	private double settingsScroll;

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
		int buttonY = panelY + panelH - FOOTER_H + 4;
		int buttonX = panelX + SIDEBAR_W + GAP;
		editHudButton = Button.builder(Component.translatable("imperium.menu.edit_hud"),
			_ -> minecraft.gui.setScreen(new HudEditScreen(this))).bounds(buttonX, buttonY, 80, 20).build();
		profilesButton = Button.builder(Component.translatable("imperium.menu.profiles"),
			_ -> minecraft.gui.setScreen(new ProfilesScreen(this))).bounds(buttonX + 84, buttonY, 80, 20).build();
		allSettingsButton = Button.builder(Component.translatable("imperium.menu.all_settings"),
			_ -> minecraft.gui.setScreen(ConfigStyles.createScreen(this, AxolotlClientCommon.getInstance().getConfigManager().getRoot())))
			.bounds(buttonX + 168, buttonY, 90, 20).build();

		if (settingsEntry == null) {
			addRenderableWidget(search);
			// Typing should search straight away.
			setInitialFocus(search);
			addRenderableWidget(editHudButton);
			addRenderableWidget(profilesButton);
			addRenderableWidget(allSettingsButton);
		} else {
			// Window resized while the settings panel is open: rebuild its widgets at the new
			// layout but keep the panel (and the scroll position) open.
			buildSettingsRows(settingsEntry.settings());
			addSettingsWidgets();
			clampSettingsScroll();
			layoutSettingsWidgets();
		}

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

	// inline settings panel

	/** Top of the settings panel's scrollable row band. */
	private int settingsScrollTop() {
		return contentY() + SETTINGS_TITLE_H;
	}

	private int settingsScrollBottom() {
		return contentY() + contentH();
	}

	private int settingsWidgetX() {
		return contentX() + contentW() - SETTINGS_WIDGET_W - 10;
	}

	private int settingsContentHeight() {
		int y = 0;
		for (SettingsRow row : settingsRows) {
			y += row.header() != null ? SETTINGS_HEADER_H : SETTINGS_ROW_H;
		}
		return y;
	}

	private double maxSettingsScroll() {
		return Math.max(0, settingsContentHeight() - (settingsScrollBottom() - settingsScrollTop()));
	}

	private void clampSettingsScroll() {
		settingsScroll = Mth.clamp(settingsScroll, 0, maxSettingsScroll());
	}

	private int settingsBackX() {
		return contentX() + contentW() - SETTINGS_BACK_W - 6;
	}

	private int settingsBackY() {
		return contentY() + 3;
	}

	private boolean overSettingsBack(double x, double y) {
		return x >= settingsBackX() && x < settingsBackX() + SETTINGS_BACK_W && y >= settingsBackY() && y < settingsBackY() + SETTINGS_BACK_H;
	}

	/**
	 * Opens the given visible card's settings inline, over the card grid. The library widgets set
	 * the options as soon as they are used; the config is saved when the panel closes.
	 */
	@VisibleForTesting
	public void openSettings(int visibleIndex) {
		if (visibleIndex < 0 || visibleIndex >= visible.size()) return;
		MenuEntry entry = visible.get(visibleIndex);
		if (settingsEntry != null) {
			stripSettingsWidgets();
		} else {
			// Park the search box and footer buttons: while the panel is open nothing else may
			// take input or render outside the scroll band's scissor.
			removeWidget(search);
			removeWidget(editHudButton);
			removeWidget(profilesButton);
			removeWidget(allSettingsButton);
		}
		// A previously focused option widget is about to be discarded.
		clearFocus();
		settingsEntry = entry;
		settingsScroll = 0;
		buildSettingsRows(entry.settings());
		addSettingsWidgets();
		layoutSettingsWidgets();
	}

	/** Closes the inline settings panel, restores the grid widgets and saves the config. */
	@VisibleForTesting
	public void closeSettingsPanel() {
		if (settingsEntry == null) return;
		stripSettingsWidgets();
		settingsEntry = null;
		settingsScroll = 0;
		addRenderableWidget(search);
		addRenderableWidget(editHudButton);
		addRenderableWidget(profilesButton);
		addRenderableWidget(allSettingsButton);
		setInitialFocus(search);
		AxolotlClientCommon.getInstance().saveConfig();
	}

	@VisibleForTesting
	public boolean isSettingsPanelOpen() {
		return settingsEntry != null;
	}

	/** Number of option rows (one per option, sub-category headers excluded). */
	@VisibleForTesting
	public int settingsRowCount() {
		return (int) settingsRows.stream().filter(row -> row.option() != null).count();
	}

	/** Number of option widgets that are live children of this screen. */
	@VisibleForTesting
	public int settingsWidgetCount() {
		return (int) settingsRows.stream()
			.filter(row -> row.widget() != null && children().contains(row.widget())).count();
	}

	/** Centre of the panel's back button, in GUI coordinates. */
	@VisibleForTesting
	public int[] settingsBackCenter() {
		return new int[]{settingsBackX() + SETTINGS_BACK_W / 2, settingsBackY() + SETTINGS_BACK_H / 2};
	}

	/** How far the settings rows are currently scrolled down, in pixels. */
	@VisibleForTesting
	public double settingsScrollAmount() {
		return settingsScroll;
	}

	/** A point over the settings row band (safe scroll target), in GUI coordinates. */
	@VisibleForTesting
	public int[] settingsScrollCenter() {
		return new int[]{contentX() + contentW() / 2, settingsScrollTop() + 24};
	}

	/** Sets the search box contents as if typed (test hook; refilters the grid). */
	@VisibleForTesting
	public void setSearchQuery(String value) {
		search.setValue(value);
	}

	private void buildSettingsRows(OptionCategory category) {
		settingsRows.clear();
		appendSettingsRows(category, 0);
	}

	private void appendSettingsRows(OptionCategory category, int indent) {
		for (Option<?> option : category.getOptions()) {
			settingsRows.add(new SettingsRow(null, option,
				ConfigStyles.createWidget(0, 0, SETTINGS_WIDGET_W, SETTINGS_WIDGET_H, option), indent));
		}
		for (OptionCategory sub : category.getSubCategories()) {
			settingsRows.add(new SettingsRow(AxoI18n.translate(sub.getName()), null, null, indent));
			appendSettingsRows(sub, indent + 1);
		}
	}

	private void addSettingsWidgets() {
		for (SettingsRow row : settingsRows) {
			if (row.widget() != null) addRenderableWidget(row.widget());
		}
	}

	private void stripSettingsWidgets() {
		for (SettingsRow row : settingsRows) {
			if (row.widget() != null) removeWidget(row.widget());
		}
		settingsRows.clear();
	}

	/** Places every option widget for the current scroll; rows outside the band are hidden. */
	private void layoutSettingsWidgets() {
		int top = settingsScrollTop();
		int bottom = settingsScrollBottom();
		int y = 0;
		for (SettingsRow row : settingsRows) {
			int rowH = row.header() != null ? SETTINGS_HEADER_H : SETTINGS_ROW_H;
			AbstractWidget widget = row.widget();
			if (widget != null) {
				int wy = top + y - (int) settingsScroll;
				widget.setX(settingsWidgetX());
				widget.setY(wy + (SETTINGS_ROW_H - SETTINGS_WIDGET_H) / 2);
				widget.visible = wy + rowH > top && wy < bottom;
			}
			y += rowH;
		}
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
			if (settingsEntry != null) {
				CarvedTheme.carvedPanel(graphics, contentX(), contentY(), contentW(), contentH());
			}
			return;
		}
		var ctx = (AxoRenderContext) graphics;
		ctx.br$fillRectRound(panelX, panelY, panelW, panelH, BG, 6);
		ctx.br$fillRectRoundVarying(panelX, panelY + HEADER_H, SIDEBAR_W, panelH - HEADER_H, SIDEBAR, 0, 6, 0, 0);
		graphics.fill(panelX, panelY + HEADER_H - 1, panelX + panelW, panelY + HEADER_H, CRIMSON);
		if (settingsEntry != null) {
			ctx.br$fillRectRound(contentX(), contentY(), contentW(), contentH(), SETTINGS_BG, 4);
		}
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (settingsEntry != null) {
			// The option widgets are real children, so they render (and hover) inside this pass:
			// scissor them to the row band so no row bleeds over the panel's title or footer.
			graphics.enableScissor(contentX(), settingsScrollTop(), contentX() + contentW(), settingsScrollBottom());
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			graphics.disableScissor();
			if (carved()) {
				CarvedTheme.engravedCaps(graphics, font, Component.translatable("imperium.menu.title").getString(),
					panelX + 10, panelY + 11, CarvedTheme.BRONZE_LIGHT);
			} else {
				graphics.text(font, Component.translatable("imperium.menu.title"), panelX + 10, panelY + 11, GOLD, false);
			}
			extractSidebar(graphics, mouseX, mouseY);
			extractSettingsPanel(graphics, mouseX, mouseY);
			return;
		}
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

	/**
	 * The inline settings panel: module name and a back button in a title band, then one row per
	 * option (translated label left, themed widget right) with sub-categories as indented groups
	 * under a small engraved header. The widgets themselves render as the screen's children, already
	 * scissored to the row band; this draws everything around and between them.
	 */
	private void extractSettingsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var ctx = (AxoRenderContext) graphics;
		boolean carved = carved();
		int top = settingsScrollTop();
		int bottom = settingsScrollBottom();

		// title band
		if (carved) {
			CarvedTheme.engravedCaps(graphics, font, settingsEntry.name(), contentX() + 8, contentY() + 6, CarvedTheme.BRONZE_LIGHT);
			CarvedTheme.carvedRule(graphics, contentX() + 2, contentY() + SETTINGS_TITLE_H - 2, contentW() - 4);
		} else {
			graphics.text(font, settingsEntry.name(), contentX() + 8, contentY() + 6, GOLD, false);
			graphics.fill(contentX() + 4, contentY() + SETTINGS_TITLE_H - 1, contentX() + contentW() - 4, contentY() + SETTINGS_TITLE_H, CRIMSON);
		}

		// back button, drawn by hand so it sits above every scrolled row
		boolean backHovered = overSettingsBack(mouseX, mouseY);
		int bx = settingsBackX(), by = settingsBackY();
		String backLabel = "< " + Component.translatable("imperium.menu.settings_back").getString();
		if (carved) {
			CarvedTheme.raisedTile(graphics, bx, by, SETTINGS_BACK_W, SETTINGS_BACK_H, backHovered, false);
			CarvedTheme.engravedText(graphics, font, backLabel, bx + 6, by + 3, backHovered ? CarvedTheme.BRONZE_LIGHT : CarvedTheme.MUTED);
		} else {
			ctx.br$fillRectRound(bx, by, SETTINGS_BACK_W, SETTINGS_BACK_H, backHovered ? CARD_HOVER : CARD, 3);
			graphics.text(font, backLabel, bx + 6, by + 3, backHovered ? GOLD : TEXT, false);
		}

		// rows (labels and headers; the widgets are children rendering between these draws)
		graphics.enableScissor(contentX(), top, contentX() + contentW(), bottom);
		int y = top - (int) settingsScroll;
		int widgetX = settingsWidgetX();
		for (SettingsRow row : settingsRows) {
			if (row.header() != null) {
				int hx = contentX() + 6 + row.indent() * SETTINGS_INDENT;
				if (carved) {
					CarvedTheme.engravedText(graphics, font, row.header().toUpperCase(Locale.ROOT), hx, y + 4, CarvedTheme.BRONZE);
					graphics.fill(hx, y + SETTINGS_HEADER_H - 3, contentX() + contentW() - 10, y + SETTINGS_HEADER_H - 2, 0x40A8823F);
				} else {
					graphics.text(font, row.header().toUpperCase(Locale.ROOT), hx, y + 4, GOLD, false);
					graphics.fill(hx, y + SETTINGS_HEADER_H - 3, contentX() + contentW() - 10, y + SETTINGS_HEADER_H - 2, 0x30D4AF37);
				}
				y += SETTINGS_HEADER_H;
				continue;
			}
			boolean hovered = mouseX >= contentX() && mouseX < contentX() + contentW() && mouseY >= y && mouseY < y + SETTINGS_ROW_H;
			int labelX = contentX() + 8 + row.indent() * SETTINGS_INDENT;
			String label = font.plainSubstrByWidth(AxoI18n.translate(row.option().getName()), widgetX - labelX - 6);
			if (hovered) {
				graphics.fill(contentX() + 2, y, contentX() + contentW() - 2, y + SETTINGS_ROW_H, carved ? 0x12CEA464 : 0x10FFFFFF);
			}
			graphics.text(font, label, labelX, y + (SETTINGS_ROW_H - 8) / 2, carved ? (hovered ? CarvedTheme.MARBLE : SETTINGS_TEXT_CARVED) : TEXT, false);
			y += SETTINGS_ROW_H;
		}
		graphics.disableScissor();

		// scrollbar, in the same voice as the grid's
		double max = maxSettingsScroll();
		if (max > 0) {
			int trackX = contentX() + contentW() + 2;
			int bandH = bottom - top;
			int thumbH = Math.max(16, (int) (bandH * (bandH / (bandH + max))));
			int thumbY = top + (int) ((bandH - thumbH) * (settingsScroll / max));
			if (carved) {
				graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, CarvedTheme.BRONZE_DARK);
				graphics.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, CarvedTheme.BRONZE);
			} else {
				graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80D4AF37);
			}
		}
	}

	// input

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (settingsEntry != null) {
			// the back button wins over any row scrolled under it
			if (overSettingsBack(mx, my)) {
				closeSettingsPanel();
				return true;
			}
			// widgets only answer inside the row band; the title band never leaks clicks to them
			if (my >= settingsScrollTop() && my < settingsScrollBottom() && super.mouseClicked(event, doubleClick)) return true;
		} else if (super.mouseClicked(event, doubleClick)) return true;

		int y = panelY + HEADER_H + 6;
		for (MenuCategory c : MenuCategory.values()) {
			if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= y && my < y + ROW_H) {
				if (settingsEntry != null) closeSettingsPanel();
				category = c;
				lastCategory = c;
				scroll = 0;
				refilter();
				return true;
			}
			y += ROW_H;
		}

		if (settingsEntry != null || !inContent(mx, my)) return false;
		int w = cardW();
		for (int i = 0; i < visible.size(); i++) {
			int x = cardX(i), cy = cardY(i);
			if (mx < x || mx >= x + w || my < cy || my >= cy + CARD_H) continue;
			MenuEntry entry = visible.get(i);
			int gx = x + w - GEAR_SIZE - 5, gy = cy + CARD_H - GEAR_SIZE - 4;
			boolean onGear = mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE;
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT || onGear || !entry.hasToggle()) {
				openSettings(i);
			} else {
				entry.toggle();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (settingsEntry != null) {
			if (mouseX >= contentX() && mouseX < contentX() + contentW() && mouseY >= settingsScrollTop() && mouseY < settingsScrollBottom()) {
				settingsScroll = Mth.clamp(settingsScroll - scrollY * SETTINGS_ROW_H / 2, 0, maxSettingsScroll());
				layoutSettingsWidgets();
				return true;
			}
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (inContent(mouseX, mouseY)) {
			scroll = Mth.clamp(scroll - scrollY * (CARD_H + GAP) / 2, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(@NotNull KeyEvent event) {
		// The first Escape closes the settings panel; only the second closes the menu.
		if (settingsEntry != null && event.isEscape()) {
			closeSettingsPanel();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One row of the settings panel: either a sub-category header or an option with its widget. */
	private record SettingsRow(@Nullable String header, @Nullable Option<?> option, @Nullable AbstractWidget widget, int indent) {
	}
}
