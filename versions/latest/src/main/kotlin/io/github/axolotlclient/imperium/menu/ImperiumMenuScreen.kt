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

package io.github.axolotlclient.imperium.menu

import kotlin.math.max
import kotlin.math.min
import java.util.Locale

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.config.screen.ProfilesScreen
import io.github.axolotlclient.modules.hud.HudEditScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.jetbrains.annotations.VisibleForTesting

/**
 * Alpine-style module menu: category sidebar, search, and a grid of module cards. Clicking a card
 * toggles the module; its gear opens the module's settings INLINE — an overlay panel drawn over the
 * card grid, built from the same themed widgets Axolotl's config screens use.
 *
 * Two themes: CARVED (default) paints the carved imperial identity from [CarvedTheme] —
 * chiseled stone panels, bronze hairlines, engraved capitals and item sprites in the cards; CLASSIC
 * keeps the flat dark-and-gold look the menu originally shipped with.
 */
class ImperiumMenuScreen(private val parent: Screen?) :
	Screen(Component.translatable("imperium.menu.title")) {
	// CLASSIC palette
	private val bg = 0xE6100D10.toInt()
	private val sidebarColor = 0xF0181316.toInt()
	private val card = 0xFF211A1F.toInt()
	private val cardHover = 0xFF2B2228.toInt()
	private val gold = 0xFFD4AF37.toInt()
	private val crimson = 0xFF8E1F2F.toInt()
	private val textColor = 0xFFEDE6DA.toInt()
	private val muted = 0xFF9A8F86.toInt()
	private val toggleOff = 0xFF4A4045.toInt()
	private val settingsBg = 0xF0141013.toInt()

	// CARVED palette (see CarvedTheme for the full ramp)
	private val sidebarCarved = 0xF013100D.toInt()
	private val cardNameOn = 0xFFE0D1B8.toInt()
	private val cardNameOff = 0xFFC7BEB2.toInt()
	private val settingsTextCarved = 0xFFCFC5B4.toInt()

	private var allEntries: List<MenuEntry> = listOf()
	private var visible: List<MenuEntry> = listOf()
	private var category: MenuCategory = lastCategory
	private var query = ""
	private var scroll = 0.0
	private lateinit var search: EditBox
	private lateinit var editHudButton: Button
	private lateinit var profilesButton: Button
	private lateinit var allSettingsButton: Button

	// The inline settings panel. While open, the card grid is not drawn and the search box /
	// footer buttons are parked (removed from the widget tree) so nothing steals input or peeks
	// out from under the panel; closing restores them and saves the config.
	private var settingsEntry: MenuEntry? = null
	private val settingsRows = ArrayList<SettingsRow>()
	private var settingsScroll = 0.0

	private var panelX = 0
	private var panelY = 0
	private var panelW = 0
	private var panelH = 0

	override fun init() {
		allEntries = MenuEntries.build()

		panelW = min(width - 24, 560)
		panelH = min(height - 24, 340)
		panelX = (width - panelW) / 2
		panelY = (height - panelH) / 2

		search = EditBox(font, panelX + panelW - 138, panelY + 7, 130, 16, Component.translatable("imperium.menu.search"))
		search.setHint(Component.translatable("imperium.menu.search"))
		search.setValue(query)
		search.setResponder { value: String ->
			query = value
			scroll = 0.0
			refilter()
		}
		val buttonY = panelY + panelH - FOOTER_H + 4
		val buttonX = panelX + SIDEBAR_W + GAP
		editHudButton = Button.builder(
			Component.translatable("imperium.menu.edit_hud"),
			{ _: net.minecraft.client.gui.components.Button -> minecraft!!.gui.setScreen(HudEditScreen(this)) }
		).bounds(buttonX, buttonY, 80, 20).build()
		profilesButton = Button.builder(
			Component.translatable("imperium.menu.profiles"),
			{ _: net.minecraft.client.gui.components.Button -> minecraft!!.gui.setScreen(ProfilesScreen(this)) }
		).bounds(buttonX + 84, buttonY, 80, 20).build()
		allSettingsButton = Button.builder(
			Component.translatable("imperium.menu.all_settings"),
			{ _: net.minecraft.client.gui.components.Button ->
				minecraft!!.gui.setScreen(
					ConfigStyles.createScreen(this, AxolotlClientCommon.getInstance().configManager.root)
				)
			}
		).bounds(buttonX + 168, buttonY, 90, 20).build()

		if (settingsEntry == null) {
			addRenderableWidget(search)
			// Typing should search straight away.
			setInitialFocus(search)
			addRenderableWidget(editHudButton)
			addRenderableWidget(profilesButton)
			addRenderableWidget(allSettingsButton)
		} else {
			// Window resized while the settings panel is open: rebuild its widgets at the new
			// layout but keep the panel (and the scroll position) open.
			buildSettingsRows(settingsEntry!!.settings())
			addSettingsWidgets()
			clampSettingsScroll()
			layoutSettingsWidgets()
		}

		refilter()
	}

	private fun refilter() {
		visible = allEntries.stream()
			.filter { e: MenuEntry -> category == MenuCategory.ALL || e.category() == category }
			.filter { e: MenuEntry -> e.matches(query) }
			.toList()
		scroll = Mth.clamp(scroll, 0.0, maxScroll())
	}

	// layout

	private fun contentX(): Int = panelX + SIDEBAR_W + GAP

	private fun contentY(): Int = panelY + HEADER_H + GAP

	private fun contentW(): Int = panelW - SIDEBAR_W - GAP * 2

	private fun contentH(): Int = panelH - HEADER_H - FOOTER_H - GAP

	private fun columns(): Int = max(2, (contentW() + GAP) / (120 + GAP))

	private fun cardW(): Int = (contentW() - GAP * (columns() - 1)) / columns()

	private fun cardX(index: Int): Int = contentX() + (index % columns()) * (cardW() + GAP)

	private fun cardY(index: Int): Int = contentY() + (index / columns()) * (CARD_H + GAP) - scroll.toInt()

	private fun maxScroll(): Double {
		val rows = (visible.size + columns() - 1) / columns()
		return max(0.0, rows * (CARD_H + GAP) - GAP - contentH().toDouble())
	}

	private fun inContent(x: Double, y: Double): Boolean =
		x >= contentX() && x < contentX() + contentW() && y >= contentY() && y < contentY() + contentH()

	@VisibleForTesting
	fun visibleEntries(): List<MenuEntry> = visible

	/** Centre of the card body (away from the gear), in GUI coordinates. */
	@VisibleForTesting
	fun cardBodyCenter(index: Int): IntArray = intArrayOf(cardX(index) + cardW() / 3, cardY(index) + CARD_H / 2)

	/** Centre of the card's settings gear, in GUI coordinates. */
	@VisibleForTesting
	fun cardGearCenter(index: Int): IntArray =
		intArrayOf(cardX(index) + cardW() - GEAR_SIZE / 2 - 5, cardY(index) + CARD_H - GEAR_SIZE / 2 - 4)

	/** Centre of a sidebar category row, in GUI coordinates. */
	@VisibleForTesting
	fun categoryCenter(target: MenuCategory): IntArray {
		var index = 0
		for (c in MenuCategory.entries) {
			if (c == target) break
			index++
		}
		val y = panelY + HEADER_H + 6 + index * ROW_H
		return intArrayOf(panelX + SIDEBAR_W / 2, y + ROW_H / 2)
	}

	// inline settings panel

	/** Top of the settings panel's scrollable row band. */
	private fun settingsScrollTop(): Int = contentY() + SETTINGS_TITLE_H

	private fun settingsScrollBottom(): Int = contentY() + contentH()

	private fun settingsWidgetX(): Int = contentX() + contentW() - SETTINGS_WIDGET_W - 10

	private fun settingsContentHeight(): Int {
		var y = 0
		for (row in settingsRows) {
			y += if (row.header != null) SETTINGS_HEADER_H else SETTINGS_ROW_H
		}
		return y
	}

	private fun maxSettingsScroll(): Double =
		max(0.0, (settingsContentHeight() - (settingsScrollBottom() - settingsScrollTop())).toDouble())

	private fun clampSettingsScroll() {
		settingsScroll = Mth.clamp(settingsScroll, 0.0, maxSettingsScroll())
	}

	private fun settingsBackX(): Int = contentX() + contentW() - SETTINGS_BACK_W - 6

	private fun settingsBackY(): Int = contentY() + 3

	private fun overSettingsBack(x: Double, y: Double): Boolean =
		x >= settingsBackX() && x < settingsBackX() + SETTINGS_BACK_W && y >= settingsBackY() && y < settingsBackY() + SETTINGS_BACK_H

	/**
	 * Opens the given visible card's settings inline, over the card grid. The library widgets set
	 * the options as soon as they are used; the config is saved when the panel closes.
	 */
	@VisibleForTesting
	fun openSettings(visibleIndex: Int) {
		if (visibleIndex < 0 || visibleIndex >= visible.size) return
		val entry = visible[visibleIndex]
		if (settingsEntry != null) {
			stripSettingsWidgets()
		} else {
			// Park the search box and footer buttons: while the panel is open nothing else may
			// take input or render outside the scroll band's scissor.
			removeWidget(search)
			removeWidget(editHudButton)
			removeWidget(profilesButton)
			removeWidget(allSettingsButton)
		}
		// A previously focused option widget is about to be discarded.
		clearFocus()
		settingsEntry = entry
		settingsScroll = 0.0
		buildSettingsRows(entry.settings())
		addSettingsWidgets()
		layoutSettingsWidgets()
	}

	/** Closes the inline settings panel, restores the grid widgets and saves the config. */
	@VisibleForTesting
	fun closeSettingsPanel() {
		if (settingsEntry == null) return
		stripSettingsWidgets()
		settingsEntry = null
		settingsScroll = 0.0
		addRenderableWidget(search)
		addRenderableWidget(editHudButton)
		addRenderableWidget(profilesButton)
		addRenderableWidget(allSettingsButton)
		setInitialFocus(search)
		AxolotlClientCommon.getInstance().saveConfig()
	}

	@VisibleForTesting
	fun isSettingsPanelOpen(): Boolean = settingsEntry != null

	/** Number of option rows (one per option, sub-category headers excluded). */
	@VisibleForTesting
	fun settingsRowCount(): Int = settingsRows.count { row: SettingsRow -> row.option != null }

	/** Number of option widgets that are live children of this screen. */
	@VisibleForTesting
	fun settingsWidgetCount(): Int =
		settingsRows.count { row: SettingsRow -> row.widget != null && children().contains(row.widget) }

	/** Centre of the panel's back button, in GUI coordinates. */
	@VisibleForTesting
	fun settingsBackCenter(): IntArray = intArrayOf(settingsBackX() + SETTINGS_BACK_W / 2, settingsBackY() + SETTINGS_BACK_H / 2)

	/** How far the settings rows are currently scrolled down, in pixels. */
	@VisibleForTesting
	fun settingsScrollAmount(): Double = settingsScroll

	/** A point over the settings row band (safe scroll target), in GUI coordinates. */
	@VisibleForTesting
	fun settingsScrollCenter(): IntArray = intArrayOf(contentX() + contentW() / 2, settingsScrollTop() + 24)

	/** Sets the search box contents as if typed (test hook; refilters the grid). */
	@VisibleForTesting
	fun setSearchQuery(value: String) {
		search.setValue(value)
	}

	private fun buildSettingsRows(category: OptionCategory) {
		settingsRows.clear()
		appendSettingsRows(category, 0)
	}

	private fun appendSettingsRows(category: OptionCategory, indent: Int) {
		for (option in category.options) {
			settingsRows.add(
				SettingsRow(
					null, option,
					ConfigStyles.createWidget(0, 0, SETTINGS_WIDGET_W, SETTINGS_WIDGET_H, option), indent
				)
			)
		}
		for (sub in category.subCategories) {
			settingsRows.add(SettingsRow(AxoI18n.translate(sub.name), null, null, indent))
			appendSettingsRows(sub, indent + 1)
		}
	}

	private fun addSettingsWidgets() {
		for (row in settingsRows) {
			if (row.widget != null) addRenderableWidget(row.widget)
		}
	}

	private fun stripSettingsWidgets() {
		for (row in settingsRows) {
			if (row.widget != null) removeWidget(row.widget)
		}
		settingsRows.clear()
	}

	/** Places every option widget for the current scroll; rows outside the band are hidden. */
	private fun layoutSettingsWidgets() {
		val top = settingsScrollTop()
		val bottom = settingsScrollBottom()
		var y = 0
		for (row in settingsRows) {
			val rowH = if (row.header != null) SETTINGS_HEADER_H else SETTINGS_ROW_H
			val widget = row.widget
			if (widget != null) {
				val wy = top + y - settingsScroll.toInt()
				widget.setX(settingsWidgetX())
				widget.setY(wy + (SETTINGS_ROW_H - SETTINGS_WIDGET_H) / 2)
				widget.visible = wy + rowH > top && wy < bottom
			}
			y += rowH
		}
	}

	// rendering

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractBackground(graphics, mouseX, mouseY, delta)
		if (carved()) {
			CarvedTheme.carvedPanel(graphics, panelX, panelY, panelW, panelH)
			// sidebar rail
			graphics.fill(panelX + 1, panelY + HEADER_H, panelX + SIDEBAR_W - 1, panelY + panelH - 1, sidebarCarved)
			graphics.fill(panelX + SIDEBAR_W - 1, panelY + HEADER_H, panelX + SIDEBAR_W, panelY + panelH - 1, CarvedTheme.BRONZE_DARK)
			CarvedTheme.carvedRule(graphics, panelX, panelY + HEADER_H - 2, panelW)
			CarvedTheme.carvedRule(graphics, panelX, panelY + panelH - FOOTER_H, panelW)
			if (settingsEntry != null) {
				CarvedTheme.carvedPanel(graphics, contentX(), contentY(), contentW(), contentH())
			}
			return
		}
		val ctx = graphics as AxoRenderContext
		ctx.`br$fillRectRound`(panelX, panelY, panelW, panelH, bg, 6f)
		ctx.`br$fillRectRoundVarying`(panelX, panelY + HEADER_H, SIDEBAR_W, panelH - HEADER_H, sidebarColor, 0f, 6f, 0f, 0f)
		graphics.fill(panelX, panelY + HEADER_H - 1, panelX + panelW, panelY + HEADER_H, crimson)
		if (settingsEntry != null) {
			ctx.`br$fillRectRound`(contentX(), contentY(), contentW(), contentH(), settingsBg, 4f)
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (settingsEntry != null) {
			// The option widgets are real children, so they render (and hover) inside this pass:
			// scissor them to the row band so no row bleeds over the panel's title or footer.
			graphics.enableScissor(contentX(), settingsScrollTop(), contentX() + contentW(), settingsScrollBottom())
			super.extractRenderState(graphics, mouseX, mouseY, delta)
			graphics.disableScissor()
			if (carved()) {
				CarvedTheme.engravedCaps(
					graphics, font, Component.translatable("imperium.menu.title").string,
					panelX + 10, panelY + 11, CarvedTheme.BRONZE_LIGHT
				)
			} else {
				graphics.text(font, Component.translatable("imperium.menu.title"), panelX + 10, panelY + 11, gold, false)
			}
			extractSidebar(graphics, mouseX, mouseY)
			extractSettingsPanel(graphics, mouseX, mouseY)
			return
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		if (carved()) {
			CarvedTheme.engravedCaps(
				graphics, font, Component.translatable("imperium.menu.title").string,
				panelX + 10, panelY + 11, CarvedTheme.BRONZE_LIGHT
			)
		} else {
			graphics.text(font, Component.translatable("imperium.menu.title"), panelX + 10, panelY + 11, gold, false)
		}
		extractSidebar(graphics, mouseX, mouseY)
		extractCards(graphics, mouseX, mouseY)
	}

	private fun extractSidebar(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		var y = panelY + HEADER_H + 6
		for (c in MenuCategory.entries) {
			val selected = c == category
			val hovered = mouseX >= panelX && mouseX < panelX + SIDEBAR_W && mouseY >= y && mouseY < y + ROW_H
			if (carved()) {
				if (selected) {
					// the row is pressed into the rail: shadow top/left, catch-light bottom/right
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + ROW_H, 0x33A8823F)
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + 1, 0x660B0908)
					graphics.fill(panelX + 1, y, panelX + 2, y + ROW_H, 0x660B0908)
					graphics.fill(panelX + 1, y + ROW_H - 1, panelX + SIDEBAR_W - 1, y + ROW_H, CarvedTheme.BRONZE_DARK)
					// carved marker: a bronze wedge anchored to the rail's edge
					graphics.fill(panelX + 2, y + 2, panelX + 5, y + ROW_H - 2, CarvedTheme.BRONZE)
					graphics.fill(panelX + 2, y + 2, panelX + 5, y + 3, CarvedTheme.BRONZE_LIGHT)
				} else if (hovered) {
					graphics.fill(panelX + 1, y, panelX + SIDEBAR_W - 1, y + ROW_H, 0x1ACEA464)
				}
				CarvedTheme.engravedText(
					graphics, font, Component.translatable(c.translationKey()).string.uppercase(Locale.ROOT),
					panelX + 9, y + 5, if (selected) CarvedTheme.BRONZE_LIGHT else settingsTextCarved
				)
			} else {
				if (selected) {
					graphics.fill(panelX, y, panelX + 2, y + ROW_H, gold)
					graphics.fill(panelX + 2, y, panelX + SIDEBAR_W, y + ROW_H, 0x30D4AF37)
				} else if (hovered) {
					graphics.fill(panelX, y, panelX + SIDEBAR_W, y + ROW_H, 0x18FFFFFF)
				}
				graphics.text(font, Component.translatable(c.translationKey()), panelX + 10, y + 5, if (selected) gold else textColor, false)
			}
			y += ROW_H
		}
	}

	private fun extractCards(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val ctx = graphics as AxoRenderContext
		val carved = carved()
		if (visible.isEmpty()) {
			if (carved) {
				CarvedTheme.engravedText(
					graphics, font, Component.translatable("imperium.menu.no_results").string,
					contentX() + contentW() / 2 - font.width(Component.translatable("imperium.menu.no_results").string) / 2,
					contentY() + contentH() / 2 - 4, CarvedTheme.MUTED
				)
			} else {
				graphics.centeredText(
					font, Component.translatable("imperium.menu.no_results"),
					contentX() + contentW() / 2, contentY() + contentH() / 2 - 4, muted
				)
			}
			return
		}
		graphics.enableScissor(contentX(), contentY(), contentX() + contentW(), contentY() + contentH())
		val w = cardW()
		for (i in visible.indices) {
			val x = cardX(i)
			val y = cardY(i)
			if (y + CARD_H < contentY() || y > contentY() + contentH()) continue
			val entry = visible[i]
			val hovered = inContent(mouseX.toDouble(), mouseY.toDouble()) &&
				mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H
			val enabled = entry.enabled()
			if (carved) {
				CarvedTheme.raisedTile(graphics, x, y, w, CARD_H, hovered, enabled)
				drawCarvedCardContent(graphics, entry, x, y, w, hovered, enabled)
			} else {
				ctx.`br$fillRectRound`(x, y, w, CARD_H, if (hovered) cardHover else card, 4f)
				if (enabled) {
					ctx.`br$outlineRectRound`(x, y, w, CARD_H, gold, 4f)
				}
				graphics.text(font, font.plainSubstrByWidth(entry.name(), w - 12), x + 6, y + 7, textColor, false)
				if (entry.hasToggle()) {
					val tx = x + 6
					val ty = y + CARD_H - 15
					ctx.`br$fillRectRound`(tx, ty, 22, 10, if (enabled) gold else toggleOff, 5f)
					ctx.`br$fillRectRound`(if (enabled) tx + 13 else tx + 1, ty + 1, 8, 8, 0xFFFFFFFF.toInt(), 4f)
					graphics.text(
						font, Component.translatable(if (enabled) "options.on" else "options.off"),
						tx + 27, ty + 1, if (enabled) gold else muted, false
					)
				}
			}

			val gx = x + w - GEAR_SIZE - 5
			val gy = y + CARD_H - GEAR_SIZE - 4
			val gearHovered = hovered && mouseX >= gx && mouseX < gx + GEAR_SIZE && mouseY >= gy && mouseY < gy + GEAR_SIZE
			graphics.text(
				font, "⚙", gx + 2, gy + 2,
				if (gearHovered) (if (carved) CarvedTheme.BRONZE else gold) else (if (carved) CarvedTheme.MUTED else muted), false
			)
		}
		graphics.disableScissor()

		val max = maxScroll()
		if (max > 0) {
			val trackX = contentX() + contentW() + 2
			val thumbH = max(16, (contentH() * (contentH() / (contentH() + max))).toInt())
			val thumbY = contentY() + ((contentH() - thumbH) * (scroll / max)).toInt()
			if (carved) {
				graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, CarvedTheme.BRONZE_DARK)
				graphics.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, CarvedTheme.BRONZE)
			} else {
				graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80D4AF37.toInt())
			}
		}
	}

	/**
	 * Carved card interior: item sprite (or an empty carved slot) in a fixed left column, the
	 * module name beside it, and the bronze/marble toggle under the name.
	 */
	private fun drawCarvedCardContent(graphics: GuiGraphicsExtractor, entry: MenuEntry, x: Int, y: Int, w: Int, hovered: Boolean, enabled: Boolean) {
		val iconX = x + 5
		val iconY = y + 5
		CarvedTheme.carvedSlot(graphics, iconX, iconY, ICON_SIZE)
		val icon = CarvedTheme.iconFor(entry)
		if (icon != null) {
			graphics.fakeItem(icon, iconX, iconY)
		}

		val textX = iconX + ICON_SIZE + 4
		val nameColor = if (enabled) cardNameOn else cardNameOff
		graphics.text(
			font, font.plainSubstrByWidth(entry.name(), w - (textX - x) - GEAR_SIZE - 4),
			textX, y + 7, if (hovered) CarvedTheme.MARBLE else nameColor, false
		)

		if (entry.hasToggle()) {
			val tx = textX - 1
			val ty = y + CARD_H - 15
			CarvedTheme.carvedToggle(graphics, tx, ty, enabled)
			graphics.text(
				font, Component.translatable(if (enabled) "options.on" else "options.off"), tx + 27, ty + 1,
				if (enabled) CarvedTheme.BRONZE else CarvedTheme.MUTED, false
			)
		}
	}

	/**
	 * The inline settings panel: module name and a back button in a title band, then one row per
	 * option (translated label left, themed widget right) with sub-categories as indented groups
	 * under a small engraved header. The widgets themselves render as the screen's children, already
	 * scissored to the row band; this draws everything around and between them.
	 */
	private fun extractSettingsPanel(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val ctx = graphics as AxoRenderContext
		val carved = carved()
		val top = settingsScrollTop()
		val bottom = settingsScrollBottom()

		// title band
		if (carved) {
			CarvedTheme.engravedCaps(graphics, font, settingsEntry!!.name(), contentX() + 8, contentY() + 6, CarvedTheme.BRONZE_LIGHT)
			CarvedTheme.carvedRule(graphics, contentX() + 2, contentY() + SETTINGS_TITLE_H - 2, contentW() - 4)
		} else {
			graphics.text(font, settingsEntry!!.name(), contentX() + 8, contentY() + 6, gold, false)
			graphics.fill(contentX() + 4, contentY() + SETTINGS_TITLE_H - 1, contentX() + contentW() - 4, contentY() + SETTINGS_TITLE_H, crimson)
		}

		// back button, drawn by hand so it sits above every scrolled row
		val backHovered = overSettingsBack(mouseX.toDouble(), mouseY.toDouble())
		val bx = settingsBackX()
		val by = settingsBackY()
		val backLabel = "< " + Component.translatable("imperium.menu.settings_back").string
		if (carved) {
			CarvedTheme.raisedTile(graphics, bx, by, SETTINGS_BACK_W, SETTINGS_BACK_H, backHovered, false)
			CarvedTheme.engravedText(graphics, font, backLabel, bx + 6, by + 3, if (backHovered) CarvedTheme.BRONZE_LIGHT else CarvedTheme.MUTED)
		} else {
			ctx.`br$fillRectRound`(bx, by, SETTINGS_BACK_W, SETTINGS_BACK_H, if (backHovered) cardHover else card, 3f)
			graphics.text(font, backLabel, bx + 6, by + 3, if (backHovered) gold else textColor, false)
		}

		// rows (labels and headers; the widgets are children rendering between these draws)
		graphics.enableScissor(contentX(), top, contentX() + contentW(), bottom)
		var y = top - settingsScroll.toInt()
		val widgetX = settingsWidgetX()
		for (row in settingsRows) {
			if (row.header != null) {
				val hx = contentX() + 6 + row.indent * SETTINGS_INDENT
				if (carved) {
					CarvedTheme.engravedText(graphics, font, row.header.uppercase(Locale.ROOT), hx, y + 4, CarvedTheme.BRONZE)
					graphics.fill(hx, y + SETTINGS_HEADER_H - 3, contentX() + contentW() - 10, y + SETTINGS_HEADER_H - 2, 0x40A8823F)
				} else {
					graphics.text(font, row.header.uppercase(Locale.ROOT), hx, y + 4, gold, false)
					graphics.fill(hx, y + SETTINGS_HEADER_H - 3, contentX() + contentW() - 10, y + SETTINGS_HEADER_H - 2, 0x30D4AF37)
				}
				y += SETTINGS_HEADER_H
				continue
			}
			val hovered = mouseX >= contentX() && mouseX < contentX() + contentW() && mouseY >= y && mouseY < y + SETTINGS_ROW_H
			val labelX = contentX() + 8 + row.indent * SETTINGS_INDENT
			val label = font.plainSubstrByWidth(AxoI18n.translate(row.option!!.name), widgetX - labelX - 6)
			if (hovered) {
				graphics.fill(contentX() + 2, y, contentX() + contentW() - 2, y + SETTINGS_ROW_H, if (carved) 0x12CEA464 else 0x10FFFFFF)
			}
			graphics.text(
				font, label, labelX, y + (SETTINGS_ROW_H - 8) / 2,
				if (carved) (if (hovered) CarvedTheme.MARBLE else settingsTextCarved) else textColor, false
			)
			y += SETTINGS_ROW_H
		}
		graphics.disableScissor()

		// scrollbar, in the same voice as the grid's
		val max = maxSettingsScroll()
		if (max > 0) {
			val trackX = contentX() + contentW() + 2
			val bandH = bottom - top
			val thumbH = max(16, (bandH * (bandH / (bandH + max))).toInt())
			val thumbY = top + ((bandH - thumbH) * (settingsScroll / max)).toInt()
			if (carved) {
				graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, CarvedTheme.BRONZE_DARK)
				graphics.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, CarvedTheme.BRONZE)
			} else {
				graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80D4AF37.toInt())
			}
		}
	}

	// input

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val mx = event.x()
		val my = event.y()
		if (settingsEntry != null) {
			// the back button wins over any row scrolled under it
			if (overSettingsBack(mx, my)) {
				closeSettingsPanel()
				return true
			}
			// widgets only answer inside the row band; the title band never leaks clicks to them
			if (my >= settingsScrollTop() && my < settingsScrollBottom() && super.mouseClicked(event, doubleClick)) return true
		} else if (super.mouseClicked(event, doubleClick)) return true

		var y = panelY + HEADER_H + 6
		for (c in MenuCategory.entries) {
			if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= y && my < y + ROW_H) {
				if (settingsEntry != null) closeSettingsPanel()
				category = c
				lastCategory = c
				scroll = 0.0
				refilter()
				return true
			}
			y += ROW_H
		}

		if (settingsEntry != null || !inContent(mx, my)) return false
		val w = cardW()
		for (i in visible.indices) {
			val x = cardX(i)
			val cy = cardY(i)
			if (mx < x || mx >= x + w || my < cy || my >= cy + CARD_H) continue
			val entry = visible[i]
			val gx = x + w - GEAR_SIZE - 5
			val gy = cy + CARD_H - GEAR_SIZE - 4
			val onGear = mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT || onGear || !entry.hasToggle()) {
				openSettings(i)
			} else {
				entry.toggle()
			}
			return true
		}
		return false
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (settingsEntry != null) {
			if (mouseX >= contentX() && mouseX < contentX() + contentW() && mouseY >= settingsScrollTop() && mouseY < settingsScrollBottom()) {
				settingsScroll = Mth.clamp(settingsScroll - scrollY * SETTINGS_ROW_H / 2, 0.0, maxSettingsScroll())
				layoutSettingsWidgets()
				return true
			}
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		}
		if (inContent(mouseX, mouseY)) {
			scroll = Mth.clamp(scroll - scrollY * (CARD_H + GAP) / 2, 0.0, maxScroll())
			return true
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		// The first Escape closes the settings panel; only the second closes the menu.
		if (settingsEntry != null && event.isEscape) {
			closeSettingsPanel()
			return true
		}
		return super.keyPressed(event)
	}

	override fun onClose() {
		minecraft!!.gui.setScreen(parent)
	}

	override fun isPauseScreen(): Boolean = false

	/** One row of the settings panel: either a sub-category header or an option with its widget. */
	private class SettingsRow(
		@JvmField val header: String?,
		@JvmField val option: Option<*>?,
		@JvmField val widget: AbstractWidget?,
		@JvmField val indent: Int
	)

	companion object {
		private const val HEADER_H = 30
		private const val FOOTER_H = 28
		private const val SIDEBAR_W = 92
		private const val ROW_H = 18
		private const val CARD_H = 44
		private const val GAP = 6
		private const val GEAR_SIZE = 12
		private const val ICON_SIZE = 16

		// Inline settings panel metrics (all inside the content area, right of the sidebar).
		private const val SETTINGS_TITLE_H = 20
		private const val SETTINGS_ROW_H = 22
		private const val SETTINGS_HEADER_H = 16
		private const val SETTINGS_WIDGET_W = 110
		private const val SETTINGS_WIDGET_H = 20
		private const val SETTINGS_INDENT = 8
		private const val SETTINGS_BACK_W = 46
		private const val SETTINGS_BACK_H = 14

		private var lastCategory = MenuCategory.ALL

		private fun carved(): Boolean =
			AxolotlClient.config().menuTheme.get() == AxolotlClientConfigCommon.MenuTheme.CARVED
	}
}
