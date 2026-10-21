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

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.imperium.theme.ImperiumTheme as Theme
import io.github.axolotlclient.modules.hud.HudEditScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The HUD editor v2 layer (docs/design/README.md, "HUD editor"): the Imperium-theme skin over
 * the existing editor, not a fork of the HUD framework. Dragging, corner-resizing, snapping and
 * magnet links still run entirely in [HudEditScreen]; this class layers the spec's editor
 * chrome on top.
 *
 * CARVED and CLASSIC keep the original editor (vanilla buttons, blue snap lines) as the
 * documented fallback; the v2 layer deactivates with the theme.
 */
class ImperiumHudEditScreen : HudEditScreen {

	constructor() : super()

	constructor(parent: Screen?) : super(parent)

	/** The v2 chrome rides on the Imperium theme; the fallback themes keep the base editor. */
	private fun v2(): Boolean =
		AxolotlClient.config().menuTheme.get() == AxolotlClientConfigCommon.MenuTheme.IMPERIUM

	override fun init() {
		super.init()
	}

	override fun addControlButtons() {
		// The v2 toolbar replaces the vanilla buttons; fallback themes keep them.
		if (!v2()) {
			super.addControlButtons()
		}
	}

	override fun renderWidgetFocusOutline(): Boolean = !v2()

	/** The spec's guide lines: 1px rules in accent-hot for the frames a snap is holding. */
	override fun renderSnapGuides(graphics: GuiGraphicsExtractor) {
		if (!v2()) {
			super.renderSnapGuides(graphics)
			return
		}
		val helper = snap ?: return
		val entry = current ?: return
		helper.setCurrent(entry.trueBounds)
		helper.rawXSnap.ifPresent { x -> graphics.fill(x, 0, x + 1, height, Theme.ACCENT_HOT) }
		helper.rawYSnap.ifPresent { y -> graphics.fill(0, y, width, y + 1, Theme.ACCENT_HOT) }
	}

	override fun onClose() {
		if (!v2()) {
			super.onClose()
			return
		}
		minecraft!!.gui.setScreen(parent)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		if (!v2()) return
		drawSnapHint(graphics)
	}

	/** The snap hint pill at the top centre while dragging. */
	private fun drawSnapHint(graphics: GuiGraphicsExtractor) {
		val entry = current ?: return
		if (!mouseDown) return
		val bounds = entry.trueBounds
		val helper = snap
		val xName: String
		val yName: String
		if (helper != null) {
			helper.setCurrent(bounds)
			xName = helper.rawXSnap.map { value -> snapName(value, true) }.orElse(bounds.x().toString())
			yName = helper.rawYSnap.map { value -> snapName(value, false) }.orElse(bounds.y().toString())
		} else {
			xName = bounds.x().toString()
			yName = bounds.y().toString()
		}
		val text = "x $xName $MIDDLE_DOT y $yName"
		val w = font.width(text) + PILL_PAD * 2
		val x = width / 2 - w / 2
		Theme.roundRect(graphics, x, PILL_Y, w, PILL_H, Theme.OVERLAY_CONTROL, PILL_H / 2f)
		Theme.roundOutline(graphics, x, PILL_Y, w, PILL_H, Theme.OVERLAY_BORDER, PILL_H / 2f)
		graphics.text(
			font, text, x + PILL_PAD, PILL_Y + (PILL_H - font.lineHeight + 1) / 2, Theme.OVERLAY_TEXT, false
		)
	}

	/** "centre" when the snap line is the screen centre, "edge" for element edges. */
	private fun snapName(value: Int, horizontal: Boolean): String {
		val centre = if (horizontal) width / 2 else height / 2
		return if (value == centre) {
			Component.translatable("imperium.hud_editor.snap.centre").string
		} else {
			Component.translatable("imperium.hud_editor.snap.edge").string
		}
	}

	companion object {
		private const val PILL_H = 16
		private const val PILL_Y = 8
		private const val PILL_PAD = 9
		private const val MIDDLE_DOT = "·"
	}
}
