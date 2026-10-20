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

package io.github.axolotlclient.imperium.emotes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.imperium.menu.CarvedTheme
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The radial emote wheel overlay (W5-A). Hold the wheel keybind in-game and the defined emotes
 * fan out around a carved hub; point (or click) at a slot and release the key to send it through
 * [EmoteDispatch]. ESC cancels without sending.
 *
 * Drawing follows the ImperiumMenuScreen conventions: [CarvedTheme] stone panels,
 * raised tiles, bronze hairlines and engraved capitals — the wheel reads as carved into the same
 * imperial identity, world visible behind it (transparent in-game background, no pause).
 *
 * Selection is the standard radial gesture: the mouse vector from the wheel centre picks the
 * nearest slot once it leaves the hub dead-zone, so nothing is selected while the pointer rests
 * in the middle. Defined emotes are distributed evenly starting at 12 o'clock; empty config
 * slots simply do not appear.
 */
class EmoteWheelScreen(private val keybind: KeyMapping, emotes: List<Emote>) :
	Screen(Component.translatable("imperiumclient.emotes.title")) {

	private val emotes: List<Emote> = emotes.toList()

	private var selected = -1

	// ── geometry (pure enough to assert from tests) ──

	private fun centerX(): Int = width / 2

	private fun centerY(): Int = height / 2

	private fun radius(): Int = max(46, min(84, min(width, height) / 4))

	/** Slot i's angle in radians, 12 o'clock, clockwise, defined slots spread evenly. */
	private fun slotAngle(index: Int): Double = -PI / 2 + 2 * PI * index / emotes.size

	@VisibleForTesting
	fun slotCenter(index: Int): IntArray {
		val r = radius()
		return intArrayOf(
			centerX() + (cos(slotAngle(index)) * r).roundToInt(),
			centerY() + (sin(slotAngle(index)) * r).roundToInt()
		)
	}

	/**
	 * The slot the given pointer position selects, or -1 in the dead zone / outside the wheel's
	 * reach. Slot boundaries sit halfway between neighbours.
	 */
	@VisibleForTesting
	fun slotAt(mouseX: Double, mouseY: Double): Int {
		if (emotes.isEmpty()) {
			return -1
		}
		val dx = mouseX - centerX()
		val dy = mouseY - centerY()
		if (hypot(dx, dy) < DEAD_ZONE) {
			return -1
		}
		val angle = atan2(dy, dx) // -PI..PI, 0 = 3 o'clock
		// map onto [0, size) clockwise from 12 o'clock
		val slice = 2 * PI / emotes.size
		val offset = (angle + PI / 2 + 2 * PI) % (2 * PI)
		val index = floor(offset / slice).toInt()
		return if (index >= 0 && index < emotes.size) index else -1
	}

	private fun inSlot(index: Int, mouseX: Double, mouseY: Double): Boolean {
		if (index < 0 || index >= emotes.size) {
			return false
		}
		val center = slotCenter(index)
		return mouseX >= center[0] - SLOT_SIZE / 2 && mouseX < center[0] + SLOT_SIZE / 2
			&& mouseY >= center[1] - SLOT_SIZE / 2 && mouseY < center[1] + SLOT_SIZE / 2
	}

	// ── rendering ──

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		// in-game UI: transparent background so the wheel floats over the world
		super.extractBackground(graphics, mouseX, mouseY, delta)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		selected = slotAt(mouseX.toDouble(), mouseY.toDouble())

		// bronze hairline from the hub edge to the selected slot
		if (selected >= 0) {
			val target = slotCenter(selected)
			val angle = atan2((target[1] - centerY()).toDouble(), (target[0] - centerX()).toDouble())
			val sx = centerX() + (cos(angle) * (HUB_SIZE / 2 + 2)).roundToInt()
			val sy = centerY() + (sin(angle) * (HUB_SIZE / 2 + 2)).roundToInt()
			val ex = target[0] - (cos(angle) * (SLOT_SIZE / 2 + 2)).roundToInt()
			val ey = target[1] - (sin(angle) * (SLOT_SIZE / 2 + 2)).roundToInt()
			fillLine(graphics, sx, sy, ex, ey, CarvedTheme.BRONZE_DARK)
		}

		// slots: raised relief tiles, gilded when selected
		for (i in emotes.indices) {
			val emote = emotes[i]
			val center = slotCenter(i)
			val x = center[0] - SLOT_SIZE / 2
			val y = center[1] - SLOT_SIZE / 2
			CarvedTheme.raisedTile(graphics, x, y, SLOT_SIZE, SLOT_SIZE, i == selected, true)
			// accent pip: the emote's own colour, set into the tile's corner like the panel studs
			graphics.fill(x + 3, y + 3, x + 5, y + 5, emote.color())
			graphics.fill(x + 3, y + 3, x + 5, y + 4, 0x55FFFFFF) // catch-light on the pip's top edge
			// engraved initial of the emote name
			val initial = emote.name().substring(0, 1).uppercase(Locale.ROOT)
			CarvedTheme.engravedText(
				graphics, font, initial,
				x + SLOT_SIZE / 2 - font.width(initial) / 2, y + SLOT_SIZE / 2 - 4,
				if (i == selected) CarvedTheme.MARBLE else CarvedTheme.BRONZE_LIGHT
			)
		}

		// hub: carved panel naming the pointed-at emote (or the wheel itself)
		val hx = centerX() - HUB_SIZE / 2
		val hy = centerY() - HUB_SIZE / 2
		CarvedTheme.carvedPanel(graphics, hx, hy, HUB_SIZE, HUB_SIZE)
		val label = if (selected >= 0) emotes[selected].name().uppercase(Locale.ROOT)
		else Component.translatable("imperiumclient.emotes.title").string.uppercase(Locale.ROOT)
		val maxW = HUB_SIZE - 12
		val fitted = fitCaps(label, maxW)
		val textX = centerX() - CarvedTheme.capsWidth(font, fitted) / 2
		val textY = centerY() - font.lineHeight / 2
		CarvedTheme.engravedCaps(graphics, font, fitted, textX, textY, if (selected >= 0) CarvedTheme.BRONZE_LIGHT else CarvedTheme.MUTED)
	}

	/** Truncates with an engraved ellipsis so long emote names cannot spill out of the hub. */
	private fun fitCaps(label: String, maxWidth: Int): String {
		if (CarvedTheme.capsWidth(font, label) <= maxWidth) {
			return label
		}
		var prefix = label
		while (prefix.length > 1 && CarvedTheme.capsWidth(font, prefix + "...") > maxWidth) {
			prefix = prefix.substring(0, prefix.length - 1)
		}
		return prefix + "..."
	}

	private fun fillLine(graphics: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
		// axis-aligned or shallow diagonals as a 2px stepped line — pixel-grid, no anti-aliasing
		val steps = max(abs(x2 - x1), abs(y2 - y1))
		if (steps == 0) {
			graphics.fill(x1, y1, x1 + 2, y1 + 2, color)
			return
		}
		for (i in 0..steps) {
			val x = x1 + (x2 - x1) * i / steps
			val y = y1 + (y2 - y1) * i / steps
			graphics.fill(x, y, x + 1, y + 2, color)
		}
	}

	// ── input ──

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (super.mouseClicked(event, doubleClick)) return true
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (i in emotes.indices) {
				if (inSlot(i, event.x(), event.y())) {
					send(i)
					return true
				}
			}
		}
		return false
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		if (keybind.matches(event)) {
			send(selected)
			return true
		}
		return super.keyReleased(event)
	}

	override fun onClose() {
		// ESC (and any other close path): cancel without sending
		closeWheel()
	}

	override fun isPauseScreen(): Boolean = false

	@VisibleForTesting
	fun selectedSlot(): Int = selected

	@VisibleForTesting
	fun wheelEmotes(): List<Emote> = emotes

	/** Dispatches the given slot (no-op for -1) through the dispatcher seam and closes. */
	private fun send(index: Int) {
		val emote = if (index >= 0 && index < emotes.size) emotes[index] else null
		closeWheel()
		if (emote != null) {
			EmoteDispatch.dispatch(emote)
		}
	}

	private fun closeWheel() {
		if (minecraft != null) {
			minecraft!!.gui.setScreen(null)
		}
	}

	companion object {
		private const val SLOT_SIZE = 26
		private const val HUB_SIZE = 68

		/** Pointer distance from centre below which nothing is selected. */
		private const val DEAD_ZONE = 34.0
	}
}
