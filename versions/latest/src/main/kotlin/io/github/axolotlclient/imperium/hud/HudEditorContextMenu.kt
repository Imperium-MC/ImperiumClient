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

package io.github.axolotlclient.imperium.hud

import io.github.axolotlclient.imperium.theme.ImperiumTheme as Theme
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * The HUD editor v2's context menu (right-click an element): Settings, Reset, Hide, and the
 * 3x3 anchor picker from the design spec. Pure layout + rendering + hit-testing; the screen
 * owns the actions, this class only reports which entry was picked.
 */
internal class HudEditorContextMenu(
	/** The element the menu was opened for. */
	val entry: HudEntry,
	clickX: Int,
	clickY: Int,
	screenWidth: Int,
	screenHeight: Int,
	font: Font
) {
	/** What the user picked; the screen performs the action. */
	sealed interface Action {
		data object Settings : Action
		data object Reset : Action
		data object Hide : Action
		data class Anchor(val anchor: AnchorPoint) : Action
	}

	private val anchorable: Boolean = HudEditorAnchors.option(entry) != null

	/** Panel width in GUI px. */
	private val menuW: Int

	/** Panel height in GUI px. */
	private val menuH: Int
	val x: Int
	val y: Int

	private val itemLabels: Array<String>
	private val itemGlyphs = arrayOf(Theme.GLYPH_GEAR, Theme.GLYPH_RESET, Theme.GLYPH_BLOCK)
	private val anchorLabel: String = Component.translatable("imperium.hud_editor.ctx.anchor").string

	/** Top of the three action rows, relative to the panel's top edge. */
	private val itemsTop = PAD

	/** Top of the anchor grid, relative to the panel's top edge (0 when not anchorable). */
	private val gridTop = itemsTop + ROW_H * ITEMS + DIVIDER + LABEL_H

	private val gridW: Int get() = GRID * PITCH - CELL_GAP
	private val gridH: Int get() = GRID * PITCH - CELL_GAP

	init {
		itemLabels = arrayOf(
			Component.translatable("imperium.hud_editor.ctx.settings").string,
			Component.translatable("imperium.hud_editor.ctx.reset").string,
			Component.translatable("imperium.hud_editor.ctx.hide").string
		)
		val textW = itemLabels.maxOf { font.width(it) }
		menuW = (PAD * 2 + GLYPH_W + GAP + textW).coerceAtLeast(MIN_W).coerceAtLeast(
			if (anchorable) PAD * 2 + gridW else 0
		)
		menuH = (if (anchorable) gridTop + gridH else itemsTop + ROW_H * ITEMS) + PAD
		x = (clickX + 2).coerceIn(4, (screenWidth - menuW - 4).coerceAtLeast(4))
		y = (clickY + 2).coerceIn(4, (screenHeight - menuH - 4).coerceAtLeast(4))
	}

	private fun gridX(): Int = x + menuW / 2 - gridW / 2

	/** The action at the given point, or null when the click misses every row and cell. */
	fun hit(mx: Double, my: Double): Action? {
		if (mx < x || mx >= x + menuW || my < y || my >= y + menuH) return null
		val ly = my - y
		val row = ((ly - itemsTop) / ROW_H).toInt()
		if (ly >= itemsTop && row < ITEMS) {
			return when (row) {
				0 -> Action.Settings
				1 -> Action.Reset
				else -> Action.Hide
			}
		}
		if (anchorable && ly >= gridTop) {
			val gridRow = ((ly - gridTop) / PITCH).toInt()
			val gx = mx - gridX()
			val gridCol = (gx / PITCH).toInt()
			if (gridRow in 0 until GRID && gx >= 0 && gridCol < GRID) {
				return Action.Anchor(ANCHORS[gridRow * GRID + gridCol])
			}
		}
		return null
	}

	/** Centre of an action row (or anchor cell), in GUI coordinates (test hook). */
	fun actionCenter(action: Action): IntArray? {
		val row = when (action) {
			Action.Settings -> 0
			Action.Reset -> 1
			Action.Hide -> 2
			is Action.Anchor -> return anchorCenter(action.anchor)
		}
		return intArrayOf(x + menuW / 2, y + itemsTop + row * ROW_H + ROW_H / 2)
	}

	/** Centre of an anchor picker cell, in GUI coordinates (test hook). */
	fun anchorCenter(anchor: AnchorPoint): IntArray? {
		if (!anchorable) return null
		val index = ANCHORS.indexOf(anchor)
		if (index < 0) return null
		return intArrayOf(
			gridX() + (index % GRID) * PITCH + CELL / 2,
			y + gridTop + (index / GRID) * PITCH + CELL / 2
		)
	}

	/** Draws the panel: action rows with glyph + label, then the 3x3 anchor picker. */
	fun render(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		Theme.roundRect(graphics, x, y, menuW, menuH, Theme.PANEL_BASE, R)
		Theme.roundOutline(graphics, x, y, menuW, menuH, Theme.OVERLAY_BORDER, R)

		for (i in 0 until ITEMS) {
			val rowY = y + itemsTop + i * ROW_H
			val hovered = mouseX >= x && mouseX < x + menuW && mouseY >= rowY && mouseY < rowY + ROW_H
			if (hovered) {
				Theme.roundRect(graphics, x + 2, rowY, menuW - 4, ROW_H, Theme.PRIMARY_HOVER, 4f)
			}
			Theme.drawGlyph(
				graphics, itemGlyphs[i], x + PAD, rowY + (ROW_H - itemGlyphs[i].size) / 2,
				if (hovered) Theme.TEXT_ACCENT else Theme.TEXT_SECONDARY
			)
			graphics.text(
				font, itemLabels[i], x + PAD + GLYPH_W + GAP, rowY + (ROW_H - font.lineHeight + 1) / 2,
				if (hovered) Theme.TEXT else Theme.TEXT_SECONDARY, false
			)
		}

		if (anchorable) {
			val labelTop = y + gridTop - LABEL_H
			graphics.fill(x + PAD, labelTop - 4, x + menuW - PAD, labelTop - 3, Theme.COMPONENT)
			graphics.text(font, anchorLabel, x + PAD, labelTop + (LABEL_H - font.lineHeight) / 2, Theme.TEXT_TERTIARY, false)
			val current = HudEditorAnchors.current(entry)
			val gx = gridX()
			for (row in 0 until GRID) {
				for (col in 0 until GRID) {
					val cx = gx + col * PITCH
					val cy = y + gridTop + row * PITCH
					val anchor = ANCHORS[row * GRID + col]
					val selected = anchor == current
					val hovered = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL
					Theme.roundRect(
						graphics, cx, cy, CELL, CELL,
						when {
							selected -> Theme.PRIMARY
							hovered -> Theme.SECONDARY
							else -> Theme.LAYER
						}, 3f
					)
					Theme.roundOutline(
						graphics, cx, cy, CELL, CELL,
						if (selected) Theme.ACCENT_SOFT else Theme.COMPONENT, 3f
					)
					// The cell's dot marks which point of the element that cell stands for.
					val dotX = cx + CELL / 2 + (col - 1) * (CELL / 2 - 2)
					val dotY = cy + CELL / 2 + (row - 1) * (CELL / 2 - 2)
					graphics.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, if (selected) Theme.ACCENT_HOT else Theme.TEXT_TERTIARY)
				}
			}
		}
	}

	companion object {
		private const val MIN_W = 78
		private const val PAD = 5
		private const val ROW_H = 16
		private const val ITEMS = 3
		private const val GLYPH_W = 11
		private const val GAP = 4
		private const val DIVIDER = 8
		private const val LABEL_H = 11
		private const val GRID = 3
		private const val CELL = 12
		private const val CELL_GAP = 2
		private const val PITCH = CELL + CELL_GAP
		private const val R = 8f

		/** The 3x3 picker grid, row-major from top-left. */
		val ANCHORS = arrayOf(
			AnchorPoint.TOP_LEFT, AnchorPoint.TOP_MIDDLE, AnchorPoint.TOP_RIGHT,
			AnchorPoint.MIDDLE_LEFT, AnchorPoint.MIDDLE_MIDDLE, AnchorPoint.MIDDLE_RIGHT,
			AnchorPoint.BOTTOM_LEFT, AnchorPoint.BOTTOM_MIDDLE, AnchorPoint.BOTTOM_RIGHT
		)
	}
}
