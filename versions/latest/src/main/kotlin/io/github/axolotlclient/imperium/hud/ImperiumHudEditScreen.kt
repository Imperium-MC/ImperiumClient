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
import io.github.axolotlclient.imperium.menu.ImperiumMenuScreen
import io.github.axolotlclient.imperium.theme.ImperiumTheme as Theme
import io.github.axolotlclient.modules.hud.HudEditScreen
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.jetbrains.annotations.VisibleForTesting

/**
 * The HUD editor v2 layer (docs/design/README.md, "HUD editor"): the Imperium-theme skin over
 * the existing editor, not a fork of the HUD framework. Dragging, corner-resizing, snapping and
 * magnet links still run entirely in [HudEditScreen]; this class adds the spec's editor chrome:
 *
 *  - a snap hint pill at the top centre while dragging ("x 128 · y 64", or the snapped edge
 *    name once a guide takes over),
 *  - snap guide lines in `--imp-accent-hot` for the frames a snap is active,
 *  - selection styling: a 2px bronze outline with 10px square handles on the corners/edges,
 *  - a right-click context menu (Settings / Reset / Hide / 3x3 anchor picker),
 *  - keyboard nudging (arrows, Shift x10), bounded undo/redo (Ctrl+Z / Ctrl+Y), Delete hides,
 *  - the bottom-left toolbar (Snapping, Undo, Redo, Modules, Done) and the circular help
 *    button with a key/description panel, all in theme materials rather than vanilla buttons.
 *
 * CARVED and CLASSIC keep the original editor (vanilla buttons, blue snap lines) as the
 * documented fallback; the v2 layer deactivates with the theme.
 */
class ImperiumHudEditScreen : HudEditScreen {

	constructor() : super()

	constructor(parent: Screen?) : super(parent)

	private var selected: HudEntry? = null
	private var helpOpen = false
	private var contextMenu: HudEditorContextMenu? = null
	private val history = HudEditorHistory()

	// Drag bookkeeping: the snapshot taken when a drag starts, committed to history on release.
	private var dragEntry: HudEntry? = null
	private var dragBefore: HudEditorHistory.Snapshot? = null

	override fun init() {
		super.init()
		if (v2()) {
			// A window resize invalidates the menu's cached coordinates.
			contextMenu = null
		}
	}

	/** The v2 chrome rides on the Imperium theme; the fallback themes keep the base editor. */
	private fun v2(): Boolean =
		AxolotlClient.config().menuTheme.get() == AxolotlClientConfigCommon.MenuTheme.IMPERIUM

	// ── base editor hooks ─────────────────────────────────────────────────────────────────────

	override fun addControlButtons() {
		// The toolbar replaces the vanilla buttons; fallback themes keep them.
		if (!v2()) {
			super.addControlButtons()
		}
	}

	override fun renderWidgetFocusOutline(): Boolean = !v2()

	override fun renderSnapGuides(graphics: GuiGraphicsExtractor) {
		if (!v2()) {
			super.renderSnapGuides(graphics)
			return
		}
		val helper = snap ?: return
		val entry = current ?: return
		helper.setCurrent(entry.trueBounds)
		// The spec's guide lines: full-height/width 1px rules in accent-hot, only for the
		// frames a snap is actually holding the element.
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

	// ── rendering ─────────────────────────────────────────────────────────────────────────────

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		if (!v2()) return
		drawSelection(graphics, mouseX, mouseY)
		drawSnapHint(graphics)
		drawToolbar(graphics, mouseX, mouseY)
		drawHelp(graphics, mouseX, mouseY)
		contextMenu?.render(graphics, font, mouseX, mouseY)
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

	// ── toolbar ───────────────────────────────────────────────────────────────────────────────

	private enum class ChipId { SNAPPING, UNDO, REDO, MODULES, DONE }

	private class Chip(val id: ChipId, val label: String, val x: Int, val y: Int, val w: Int, val enabled: Boolean)

	private val chipY: Int get() = height - TOOLBAR_MARGIN - CHIP_H

	private fun chips(): List<Chip> {
		var x = TOOLBAR_MARGIN
		fun next(id: ChipId, key: String, enabled: Boolean = true, dot: Boolean = false): Chip {
			val label = Component.translatable(key).string
			val w = CHIP_PAD * 2 + (if (dot) STATE_DOT + STATE_DOT_GAP else 0) + font.width(label)
			val chip = Chip(id, label, x, chipY, w, enabled)
			x += w + CHIP_GAP
			return chip
		}
		return listOf(
			next(ChipId.SNAPPING, "hud.snapping", dot = true),
			next(ChipId.UNDO, "imperium.hud_editor.toolbar.undo", enabled = history.canUndo),
			next(ChipId.REDO, "imperium.hud_editor.toolbar.redo", enabled = history.canRedo),
			next(ChipId.MODULES, "imperium.hud_editor.toolbar.modules"),
			next(ChipId.DONE, "imperium.hud_editor.toolbar.done")
		)
	}

	private fun drawToolbar(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val snappingOn = HudManager.getInstance().isSnappingEnabled
		for (chip in chips()) {
			val hovered = chip.enabled && mouseX >= chip.x && mouseX < chip.x + chip.w &&
				mouseY >= chip.y && mouseY < chip.y + CHIP_H
			Theme.roundRect(
				graphics, chip.x, chip.y, chip.w, CHIP_H,
				if (hovered) Theme.OVERLAY_CONTROL_HOVER else Theme.OVERLAY_CONTROL, CHIP_H / 2f
			)
			Theme.roundOutline(
				graphics, chip.x, chip.y, chip.w, CHIP_H,
				if (hovered) Theme.OVERLAY_BORDER_HOVER else Theme.OVERLAY_BORDER, CHIP_H / 2f
			)
			var tx = chip.x + CHIP_PAD
			if (chip.id == ChipId.SNAPPING) {
				// State is never colour-alone: the dot sits inside a pill whose label stays.
				graphics.fill(
					tx, chip.y + (CHIP_H - STATE_DOT) / 2, tx + STATE_DOT, chip.y + (CHIP_H + STATE_DOT) / 2,
					if (snappingOn) Theme.ACCENT_HOT else Theme.TEXT_DISABLED
				)
				tx += STATE_DOT + STATE_DOT_GAP
			}
			graphics.text(
				font, chip.label, tx, chip.y + (CHIP_H - font.lineHeight + 1) / 2,
				if (chip.enabled) Theme.OVERLAY_TEXT else Theme.TEXT_DISABLED, false
			)
		}
	}

	private fun toolbarHit(mx: Double, my: Double): ChipId? {
		for (chip in chips()) {
			if (mx >= chip.x && mx < chip.x + chip.w && my >= chip.y && my < chip.y + CHIP_H) {
				return chip.id
			}
		}
		return null
	}

	private fun handleChip(id: ChipId) {
		when (id) {
			ChipId.SNAPPING -> HudManager.getInstance().toggleSnapping()
			ChipId.UNDO -> undo()
			ChipId.REDO -> redo()
			ChipId.MODULES -> minecraft!!.gui.setScreen(ImperiumMenuScreen(this))
			ChipId.DONE -> onClose()
		}
	}

	// ── help ──────────────────────────────────────────────────────────────────────────────────

	private fun helpX(): Int = width - HELP_MARGIN - HELP_SIZE
	private fun helpY(): Int = height - HELP_MARGIN - HELP_SIZE

	private fun overHelpButton(mx: Double, my: Double): Boolean =
		mx >= helpX() && mx < helpX() + HELP_SIZE && my >= helpY() && my < helpY() + HELP_SIZE

	private data class HelpRow(val key: String, val label: String)

	private fun helpRows(): List<HelpRow> = listOf(
		HelpRow(tr("imperium.hud_editor.help.nudge.key"), tr("imperium.hud_editor.help.nudge")),
		HelpRow(tr("imperium.hud_editor.help.nudge_big.key"), tr("imperium.hud_editor.help.nudge_big")),
		HelpRow(tr("imperium.hud_editor.help.undo.key"), tr("imperium.hud_editor.help.undo")),
		HelpRow(tr("imperium.hud_editor.help.redo.key"), tr("imperium.hud_editor.help.redo")),
		HelpRow(tr("imperium.hud_editor.help.hide.key"), tr("imperium.hud_editor.help.hide")),
		HelpRow(tr("imperium.hud_editor.help.menu.key"), tr("imperium.hud_editor.help.menu")),
		HelpRow(tr("imperium.hud_editor.help.help.key"), tr("imperium.hud_editor.help.help"))
	)

	private fun tr(key: String): String = Component.translatable(key).string

	private fun drawHelp(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val hovered = overHelpButton(mouseX.toDouble(), mouseY.toDouble())
		Theme.roundRect(
			graphics, helpX(), helpY(), HELP_SIZE, HELP_SIZE,
			if (hovered || helpOpen) Theme.OVERLAY_CONTROL_HOVER else Theme.OVERLAY_CONTROL, HELP_SIZE / 2f
		)
		Theme.roundOutline(
			graphics, helpX(), helpY(), HELP_SIZE, HELP_SIZE,
			if (hovered || helpOpen) Theme.OVERLAY_BORDER_HOVER else Theme.OVERLAY_BORDER, HELP_SIZE / 2f
		)
		graphics.text(
			font, "?", helpX() + (HELP_SIZE - font.width("?")) / 2,
			helpY() + (HELP_SIZE - font.lineHeight) / 2, Theme.TEXT_ACCENT, false
		)
		if (!helpOpen) return

		val rows = helpRows()
		val keyW = rows.maxOf { font.width(it.key) + KEY_CHIP_PAD * 2 }.coerceAtLeast(MIN_KEY_CHIP_W)
		val panelW = (rows.maxOf { keyW + HELP_GAP + font.width(it.label) } + HELP_PAD * 2).coerceAtLeast(HELP_MIN_W)
		val rowH = KEY_CHIP_H + 4
		val panelH = HELP_PAD * 2 + HELP_TITLE_H + rows.size * rowH
		val px = width - HELP_MARGIN - panelW
		val py = helpY() - HELP_PANEL_GAP - panelH
		Theme.roundRect(graphics, px, py, panelW, panelH, Theme.PANEL_BASE, HELP_R)
		Theme.roundOutline(graphics, px, py, panelW, panelH, Theme.OVERLAY_BORDER, HELP_R)
		graphics.text(
			font, tr("imperium.hud_editor.help.title"), px + HELP_PAD, py + HELP_PAD, Theme.TEXT_ACCENT, false
		)
		var y = py + HELP_PAD + HELP_TITLE_H
		for (row in rows) {
			// The key chip: Minecraft font in a small rounded chip.
			Theme.roundRect(graphics, px + HELP_PAD, y, keyW, KEY_CHIP_H, Theme.CHIP, KEY_CHIP_H / 2f)
			Theme.roundOutline(graphics, px + HELP_PAD, y, keyW, KEY_CHIP_H, Theme.COMPONENT, KEY_CHIP_H / 2f)
			graphics.text(
				font, row.key, px + HELP_PAD + (keyW - font.width(row.key)) / 2,
				y + (KEY_CHIP_H - font.lineHeight + 1) / 2, Theme.TEXT, false
			)
			graphics.text(
				font, row.label, px + HELP_PAD + keyW + HELP_GAP,
				y + (KEY_CHIP_H - font.lineHeight + 1) / 2, Theme.TEXT_SECONDARY, false
			)
			y += rowH
		}
	}

	// ── input ─────────────────────────────────────────────────────────────────────────────────

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (!v2()) return super.mouseClicked(event, doubleClick)
		val mx = event.x()
		val my = event.y()
		contextMenu?.let { menu ->
			when (val action = menu.hit(mx, my)) {
				HudEditorContextMenu.Action.Settings -> openSettingsFor(menu.entry)
				HudEditorContextMenu.Action.Reset -> resetEntry(menu.entry)
				HudEditorContextMenu.Action.Hide -> hideEntry(menu.entry)
				is HudEditorContextMenu.Action.Anchor -> applyAnchor(menu.entry, action.anchor)
				null -> closeContextMenu()
			}
			return true
		}
		if (overHelpButton(mx, my)) {
			helpOpen = !helpOpen
			return true
		}
		// A left click dismisses the help panel first; right-clicks pass through so the
		// context menu stays reachable while the shortcuts are showing.
		if (helpOpen && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			helpOpen = false
			return true
		}
		toolbarHit(mx, my)?.let { id ->
			handleChip(id)
			return true
		}
		val under = HudManager.getInstance().getEntryXY(mx.toInt(), my.toInt()).orElse(null)
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (under != null) {
				selected = under
				contextMenu = HudEditorContextMenu(under, mx.toInt(), my.toInt(), width, height, font)
			} else {
				selected = null
			}
			return true
		}
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
		if (contextMenu != null) {
			if (event.isEscape) closeContextMenu()
			return true
		}
		if (event.isEscape) {
			if (helpOpen) {
				helpOpen = false
				return true
			}
			if (selected != null && !isModificationActive()) {
				selected = null
				return true
			}
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
		// H toggles the help panel even mid-drag, so the shortcuts stay reachable while the
		// mouse is busy moving an element.
		if (event.key() == InputConstants.KEY_H && !ctrlDown(event) && !event.hasAltDown()) {
			helpOpen = !helpOpen
			return true
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

	/** Disables the element (undoable; the delete key and the Hide action both land here). */
	private fun hideEntry(entry: HudEntry) {
		closeContextMenu()
		val before = HudEditorHistory.Snapshot.capture(entry)
		entry.setEnabled(false)
		history.push(entry, before, HudEditorHistory.Snapshot.capture(entry), HudEditorHistory.Kind.HIDE)
		if (selected === entry) {
			selected = null
		}
		saveEntry(entry)
	}

	/** Resets bounds to the element's configured defaults: position fraction, scale 1, no links. */
	private fun resetEntry(entry: HudEntry) {
		closeContextMenu()
		val before = HudEditorHistory.Snapshot.capture(entry)
		entry.clearBoundsDependencies()
		HudManagerCommon.getInstance().saveHudDependencyLinks()
		entry.setScale(1f)
		// Default positions are fractions of the window; storing f * size puts the option back
		// to its factory value (setX/setY convert pixels to fractions).
		entry.setPos((entry.defaultX * width).toInt(), (entry.defaultY * height).toInt())
		history.push(entry, before, HudEditorHistory.Snapshot.capture(entry), HudEditorHistory.Kind.RESET)
		saveEntry(entry)
	}

	/** Applies a new anchor point; the option's listener keeps the element visually in place. */
	private fun applyAnchor(entry: HudEntry, anchor: AnchorPoint) {
		val before = HudEditorHistory.Snapshot.capture(entry)
		if (!HudEditorAnchors.apply(entry, anchor)) {
			return
		}
		entry.onBoundsUpdate()
		history.push(entry, before, HudEditorHistory.Snapshot.capture(entry), HudEditorHistory.Kind.ANCHOR)
		saveEntry(entry)
		// The menu stays open so the new anchor can be compared with its neighbours.
	}

	/** Settings opens the menu's inline settings page for the element (callback hook). */
	private fun openSettingsFor(entry: HudEntry) {
		closeContextMenu()
		minecraft!!.gui.setScreen(ImperiumMenuScreen.openWithHudSettings(this, entry))
	}

	private fun closeContextMenu() {
		contextMenu = null
	}

	private fun saveEntry(entry: HudEntry) {
		AxolotlClientConfig.getInstance().getConfigManager(entry.category).save()
	}

	// ── test hooks ────────────────────────────────────────────────────────────────────────────

	@VisibleForTesting
	fun selectedEntryId(): String? = selected?.id?.toString()

	@VisibleForTesting
	fun isHelpOpen(): Boolean = helpOpen

	@VisibleForTesting
	fun isContextMenuOpen(): Boolean = contextMenu != null

	@VisibleForTesting
	fun contextMenuEntryId(): String? = contextMenu?.entry?.id?.toString()

	@VisibleForTesting
	fun undoDepth(): Int = history.undoDepth()

	@VisibleForTesting
	fun redoDepth(): Int = history.redoDepth()

	@VisibleForTesting
	fun helpButtonCenter(): IntArray = intArrayOf(helpX() + HELP_SIZE / 2, helpY() + HELP_SIZE / 2)

	@VisibleForTesting
	fun chipCenter(id: String): IntArray? {
		val chipId = when (id) {
			"snapping" -> ChipId.SNAPPING
			"undo" -> ChipId.UNDO
			"redo" -> ChipId.REDO
			"modules" -> ChipId.MODULES
			"done" -> ChipId.DONE
			else -> return null
		}
		val chip = chips().firstOrNull { it.id == chipId } ?: return null
		return intArrayOf(chip.x + chip.w / 2, chip.y + CHIP_H / 2)
	}

	@VisibleForTesting
	fun contextMenuActionCenter(action: String): IntArray? {
		val menu = contextMenu ?: return null
		return when (action) {
			"settings" -> menu.actionCenter(HudEditorContextMenu.Action.Settings)
			"reset" -> menu.actionCenter(HudEditorContextMenu.Action.Reset)
			"hide" -> menu.actionCenter(HudEditorContextMenu.Action.Hide)
			else -> null
		}
	}

	@VisibleForTesting
	fun contextMenuAnchorCenter(anchor: AnchorPoint): IntArray? = contextMenu?.anchorCenter(anchor)

	companion object {
		private const val HANDLE = 10
		private const val PILL_H = 16
		private const val PILL_Y = 8
		private const val PILL_PAD = 9
		private const val MIDDLE_DOT = "·"

		private const val TOOLBAR_MARGIN = 8
		private const val CHIP_H = 16
		private const val CHIP_GAP = 4
		private const val CHIP_PAD = 8
		private const val STATE_DOT = 5
		private const val STATE_DOT_GAP = 4

		private const val HELP_SIZE = 48
		private const val HELP_MARGIN = 8
		private const val HELP_PANEL_GAP = 6
		private const val HELP_PAD = 8
		private const val HELP_TITLE_H = 12
		private const val HELP_GAP = 6
		private const val HELP_MIN_W = 130
		private const val HELP_R = 10f
		private const val KEY_CHIP_H = 12
		private const val KEY_CHIP_PAD = 5
		private const val MIN_KEY_CHIP_W = 26

		private const val NUDGE = 1
		private const val NUDGE_BIG = 10
	}
}
