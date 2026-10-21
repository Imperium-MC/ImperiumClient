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

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.AxolotlClientConfig
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.imperium.theme.ImperiumTheme as Theme
import io.github.axolotlclient.modules.hud.HudEditScreen
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
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

	/** The element the editor has selected (persists between drags; drives styling + keyboard). */
	private var selected: HudEntry? = null
	private val history = HudEditorHistory()

	// Drag bookkeeping: the snapshot taken when a drag starts, committed to history on release.
	private var dragEntry: HudEntry? = null
	private var dragBefore: HudEditorHistory.Snapshot? = null

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
		drawSelection(graphics, mouseX, mouseY)
		drawSnapHint(graphics)
	}

	/** 2px bronze outline plus 10px square handles on the corners and edge midpoints. */
	private fun drawSelection(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val entry = selected ?: return
		if (!entry.isEnabled) return
		val b = entry.trueBounds
		val x0 = b.x() - 2
		val y0 = b.y() - 2
		val x1 = b.xEnd() + 2
		val y1 = b.yEnd() + 2
		graphics.fill(x0, y0, x1, y0 + 2, Theme.ACCENT)
		graphics.fill(x0, y1 - 2, x1, y1, Theme.ACCENT)
		graphics.fill(x0, y0, x0 + 2, y1, Theme.ACCENT)
		graphics.fill(x1 - 2, y0, x1, y1, Theme.ACCENT)
		val midX = b.x() + b.width() / 2
		val midY = b.y() + b.height() / 2
		drawHandle(graphics, b.x(), b.y(), mouseX, mouseY)
		drawHandle(graphics, midX, b.y(), mouseX, mouseY)
		drawHandle(graphics, b.xEnd(), b.y(), mouseX, mouseY)
		drawHandle(graphics, b.x(), midY, mouseX, mouseY)
		drawHandle(graphics, b.xEnd(), midY, mouseX, mouseY)
		drawHandle(graphics, b.x(), b.yEnd(), mouseX, mouseY)
		drawHandle(graphics, midX, b.yEnd(), mouseX, mouseY)
		drawHandle(graphics, b.xEnd(), b.yEnd(), mouseX, mouseY)
	}

	/** A 10px square handle: accent-hot core with a dark 1px rim so it reads on any element. */
	private fun drawHandle(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, mouseX: Int, mouseY: Int) {
		val x = cx - HANDLE / 2
		val y = cy - HANDLE / 2
		graphics.fill(x, y, x + HANDLE, y + HANDLE, Theme.FIELD)
		val hovered = mouseX >= x - 1 && mouseX < x + HANDLE + 1 && mouseY >= y - 1 && mouseY < y + HANDLE + 1
		graphics.fill(x + 1, y + 1, x + HANDLE - 1, y + HANDLE - 1, if (hovered) Theme.TEXT else Theme.ACCENT_HOT)
	}

	// ── input ─────────────────────────────────────────────────────────────────────────────────

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (!v2()) return super.mouseClicked(event, doubleClick)
		// Clicking an element selects it; clicking empty space clears the selection. Dragging,
		// resizing and the fallback right-click behaviour stay in the base editor.
		val under = HudManager.getInstance().getEntryXY(event.x().toInt(), event.y().toInt()).orElse(null)
		selected = under
		dragEntry = under
		dragBefore = under?.let { HudEditorHistory.Snapshot.capture(it) }
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val entry = dragEntry
		val before = dragBefore
		val handled = super.mouseReleased(event)
		if (entry != null && before != null) {
			val after = HudEditorHistory.Snapshot.capture(entry)
			if (after != before) {
				history.push(entry, before, after, HudEditorHistory.Kind.DRAG)
			}
		}
		dragEntry = null
		dragBefore = null
		return handled
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (!v2()) return super.keyPressed(event)
		if (event.isEscape && selected != null && !isModificationActive()) {
			selected = null
			return true
		}
		if (ctrlDown(event)) {
			if (!shiftDown(event) && event.key() == InputConstants.KEY_Z) {
				undo()
				return true
			}
			if (event.key() == InputConstants.KEY_Y || (shiftDown(event) && event.key() == InputConstants.KEY_Z)) {
				redo()
				return true
			}
		}
		if (selected != null && !isModificationActive()) {
			val entry = selected!!
			val step = if (shiftDown(event)) NUDGE_BIG else NUDGE
			when (event.key()) {
				InputConstants.KEY_DELETE -> {
					hideEntry(entry)
					return true
				}
				InputConstants.KEY_UP -> {
					nudge(entry, 0, -step)
					return true
				}
				InputConstants.KEY_DOWN -> {
					nudge(entry, 0, step)
					return true
				}
				InputConstants.KEY_LEFT -> {
					nudge(entry, -step, 0)
					return true
				}
				InputConstants.KEY_RIGHT -> {
					nudge(entry, step, 0)
					return true
				}
			}
		}
		return super.keyPressed(event)
	}

	// ── editor actions ────────────────────────────────────────────────────────────────────────

	/**
	 * Modifier checks accept both sources: real key events carry modifier bits, while injected
	 * test input (and some synthetic paths) leaves them at 0 with only the global key state set.
	 */
	private fun ctrlDown(event: KeyEvent): Boolean =
		event.hasControlDown() || minecraft?.hasControlDown() == true

	private fun shiftDown(event: KeyEvent): Boolean =
		event.hasShiftDown() || minecraft?.hasShiftDown() == true

	/** Moves the element by whole pixels; consecutive nudges coalesce into one undo step. */
	private fun nudge(entry: HudEntry, dx: Int, dy: Int) {
		val before = HudEditorHistory.Snapshot.capture(entry)
		val bounds = entry.trueBounds
		entry.setPos(bounds.x() + dx + entry.offsetTrueWidth(), bounds.y() + dy + entry.offsetTrueHeight())
		history.push(entry, before, HudEditorHistory.Snapshot.capture(entry), HudEditorHistory.Kind.NUDGE)
		if (entry.tickable()) {
			entry.tick()
		}
		saveEntry(entry)
	}

	private fun undo() {
		val edit = history.popForUndo() ?: return
		applySnapshot(edit.entry, edit.before)
	}

	private fun redo() {
		val edit = history.popForRedo() ?: return
		applySnapshot(edit.entry, edit.after)
	}

	/** Restores a recorded snapshot: anchor first (its listener shifts the raw position), then placement. */
	private fun applySnapshot(entry: HudEntry, snapshot: HudEditorHistory.Snapshot) {
		val anchor = HudEditorAnchors.current(entry)
		if (anchor != null && snapshot.anchor != null && anchor != snapshot.anchor) {
			HudEditorAnchors.apply(entry, snapshot.anchor)
		}
		entry.setPos(snapshot.x + entry.offsetTrueWidth(), snapshot.y + entry.offsetTrueHeight())
		entry.setScale(snapshot.scale)
		if (entry.isEnabled != snapshot.enabled) {
			entry.setEnabled(snapshot.enabled)
		}
		entry.onBoundsUpdate()
		if (!entry.isEnabled && selected === entry) {
			selected = null
		}
		saveEntry(entry)
	}

	/** Disables the element (undoable; the delete key lands here). */
	private fun hideEntry(entry: HudEntry) {
		val before = HudEditorHistory.Snapshot.capture(entry)
		entry.setEnabled(false)
		history.push(entry, before, HudEditorHistory.Snapshot.capture(entry), HudEditorHistory.Kind.HIDE)
		if (selected === entry) {
			selected = null
		}
		saveEntry(entry)
	}

	private fun saveEntry(entry: HudEntry) {
		AxolotlClientConfig.getInstance().getConfigManager(entry.category).save()
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
		private const val HANDLE = 10
		private const val PILL_H = 16
		private const val PILL_Y = 8
		private const val PILL_PAD = 9
		private const val MIDDLE_DOT = "·"

		private const val NUDGE = 1
		private const val NUDGE_BIG = 10
	}
}
