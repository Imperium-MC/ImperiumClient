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

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.imperium.theme.ImperiumFonts
import io.github.axolotlclient.imperium.theme.ImperiumTheme as Theme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * A button in the theme's control materials (docs/design/README.md): a stone-toned fill under a
 * 1px component hairline, the spec's radius, hover brightening the fill and accenting the border,
 * and the spec's button motion - scale 1.033 on hover, 0.966 while pressed, eased over 200/100 ms
 * (200 ms colour), all disabled by the "Reduce motion" setting.
 *
 * It is a real widget (an [AbstractWidget]), so keyboard focus traversal, ENTER/SPACE activation
 * and narration keep working; it just draws itself instead of the vanilla grey sprites.
 */
class ImperiumButton(
	x: Int, y: Int, width: Int, height: Int,
	message: Component,
	private val onPress: (ImperiumButton) -> Unit
) : AbstractWidget(x, y, width, height, message) {

	// the spec's 12 px control radius halved (v3 sizes are the mockup's CSS values halved)
	private val radius: Float = 6f.coerceAtMost(height / 2f)

	private var hoverT = 0f
	private var pressT = 0f
	private var lastFrameMs = 0L
	private var pressed = false

	override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
		pressed = true
		onPress(this)
	}

	override fun onRelease(event: MouseButtonEvent) {
		pressed = false
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (isActive && event.isSelection) {
			pressed = true
			playDownSound(Minecraft.getInstance().soundManager)
			onPress(this)
			pressed = false
			return true
		}
		return false
	}

	override fun updateWidgetNarration(output: NarrationElementOutput) {
		defaultButtonNarrationText(output)
	}

	override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (!visible) return
		val reduceMotion = AxolotlClient.config().menuReduceMotion.get()
		val hovered = isActive && isMouseOver(mouseX.toDouble(), mouseY.toDouble())
		advanceFades(hovered, reduceMotion)
		val t = if (reduceMotion) (if (hovered) 1f else 0f) else hoverT
		val p = if (reduceMotion) (if (pressed) 1f else 0f) else pressT

		val x = getX()
		val y = getY()
		val w = width
		val h = height
		val scale = 1f + 0.033f * easeOut(t) - 0.067f * easeOut(p)
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate((x + w / 2).toFloat(), (y + h / 2).toFloat())
		pose.scale(scale, scale)
		pose.translate(-(x + w / 2).toFloat(), -(y + h / 2).toFloat())

		Theme.roundRect(graphics, x, y, w, h, lerpColor(Theme.LAYER, Theme.PRIMARY_HOVER, t), radius)
		Theme.roundOutline(
			graphics, x, y, w, h,
			if (isFocused) Theme.ACCENT_HALF else lerpColor(Theme.COMPONENT, Theme.ACCENT_SOFT, t), radius
		)

		val font = Minecraft.getInstance().font
		val text = ImperiumFonts.truncate(font, message.string, w - 8, ImperiumFonts.BARLOW_SEMIBOLD)
		graphics.text(
			font, ImperiumFonts.semibold(text),
			x + w / 2 - ImperiumFonts.width(font, text, ImperiumFonts.BARLOW_SEMIBOLD) / 2,
			y + (h - font.lineHeight + 1) / 2,
			if (t > 0.5f || isFocused) Theme.TEXT else Theme.TEXT_SECONDARY, false
		)
		pose.popMatrix()
	}

	private fun advanceFades(hovered: Boolean, reduceMotion: Boolean) {
		val now = System.currentTimeMillis()
		val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
		lastFrameMs = now
		if (reduceMotion) {
			hoverT = if (hovered) 1f else 0f
			pressT = if (pressed) 1f else 0f
			return
		}
		hoverT = step(hoverT, if (hovered) 1f else 0f, dt / HOVER_MS)
		pressT = step(pressT, if (pressed) 1f else 0f, dt / PRESS_MS)
	}

	private fun step(current: Float, target: Float, amount: Float): Float =
		if (current < target) minOf(target, current + amount) else maxOf(target, current - amount)

	private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

	private fun lerpColor(from: Int, to: Int, t: Float): Int {
		if (t <= 0f) return from
		if (t >= 1f) return to
		val fa = (from ushr 24) and 0xFF
		val fr = (from ushr 16) and 0xFF
		val fg = (from ushr 8) and 0xFF
		val fb = from and 0xFF
		val ta = (to ushr 24) and 0xFF
		val tr = (to ushr 16) and 0xFF
		val tg = (to ushr 8) and 0xFF
		val tb = to and 0xFF
		return ((fa + (ta - fa) * t).toInt() shl 24) or ((fr + (tr - fr) * t).toInt() shl 16) or
			((fg + (tg - fg) * t).toInt() shl 8) or (fb + (tb - fb) * t).toInt()
	}

	companion object {
		private const val HOVER_MS = 0.2f
		private const val PRESS_MS = 0.1f
	}
}
