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

import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint

/**
 * The HUD editor v2's bounded undo/redo stack (docs/design/README.md, "HUD editor").
 *
 * Every editor action records one [Edit]: the element plus its bounds before and after the
 * change. The stack is capped (oldest edits fall off) so a long editing session cannot grow it
 * without bound. Consecutive keyboard nudges of the same element coalesce into a single edit,
 * so undo steps back a whole nudge burst, not one pixel at a time.
 */
internal class HudEditorHistory(private val capacity: Int = MAX_HISTORY) {

	/** What kind of change an [Edit] records; consecutive [Kind.NUDGE] edits coalesce. */
	enum class Kind { DRAG, NUDGE, RESET, HIDE, ANCHOR }

	/**
	 * One element's placement state: top-left pixel position, scale, enabled flag and (for
	 * anchored elements) the anchor point. Positions are the true (scaled) top-left, the same
	 * value `getTrueBounds().x()/y()` reports, so snapshots survive scale and window changes
	 * within an editing session.
	 */
	data class Snapshot(val x: Int, val y: Int, val scale: Float, val enabled: Boolean, val anchor: AnchorPoint?) {
		companion object {
			fun capture(entry: HudEntry): Snapshot {
				val bounds = entry.trueBounds
				return Snapshot(bounds.x(), bounds.y(), entry.scale, entry.isEnabled, HudEditorAnchors.current(entry))
			}
		}
	}

	/** One recorded change: restoring [before] undoes it, restoring [after] redoes it. */
	class Edit(
		val entryId: String,
		val entry: HudEntry,
		val kind: Kind,
		val before: Snapshot,
		var after: Snapshot
	)

	private val undoStack = ArrayDeque<Edit>()
	private val redoStack = ArrayDeque<Edit>()

	val canUndo: Boolean get() = undoStack.isNotEmpty()
	val canRedo: Boolean get() = redoStack.isNotEmpty()

	fun undoDepth(): Int = undoStack.size

	fun redoDepth(): Int = redoStack.size

	/**
	 * Records a change. A no-op (identical snapshots) is dropped; a nudge extends the newest
	 * nudge edit of the same element instead of pushing, and any new edit clears the redo stack.
	 */
	fun push(entry: HudEntry, before: Snapshot, after: Snapshot, kind: Kind) {
		if (before == after) return
		redoStack.clear()
		val top = undoStack.lastOrNull()
		if (kind == Kind.NUDGE && top != null && top.kind == Kind.NUDGE && top.entryId == entry.id.toString()) {
			top.after = after
			return
		}
		if (undoStack.size >= capacity) {
			undoStack.removeFirst()
		}
		undoStack.addLast(Edit(entry.id.toString(), entry, kind, before, after))
	}

	/** Pops the newest edit for undo (moving it to the redo stack), or null when there is none. */
	fun popForUndo(): Edit? {
		val edit = undoStack.removeLastOrNull() ?: return null
		redoStack.addLast(edit)
		return edit
	}

	/** Pops the newest undone edit for redo (moving it back to the undo stack), or null. */
	fun popForRedo(): Edit? {
		val edit = redoStack.removeLastOrNull() ?: return null
		undoStack.addLast(edit)
		return edit
	}

	fun clear() {
		undoStack.clear()
		redoStack.clear()
	}

	companion object {
		/** The spec's bounded history: 64 recorded edits, then the oldest falls off. */
		const val MAX_HISTORY = 64
	}
}

/**
 * Anchor-point access for HUD elements that implement `DynamicallyPositionable`: their anchor
 * lives in the entry's options as an `EnumOption<AnchorPoint>` ("anchorpoint"). Entries without
 * one (fixed top-left elements) answer null everywhere and the editor hides its anchor picker.
 */
object HudEditorAnchors {

	/** The entry's anchor option, or null when the element cannot be anchored. */
	@JvmStatic
	fun option(entry: HudEntry): EnumOption<*>? {
		return entry.saveOptions.firstOrNull { opt -> opt is EnumOption<*> && (opt as EnumOption<*>).get() is AnchorPoint }
			as? EnumOption<*>
	}

	/** The entry's current anchor point, or null when it has none. */
	@JvmStatic
	fun current(entry: HudEntry): AnchorPoint? = option(entry)?.get() as? AnchorPoint

	/**
	 * Applies a new anchor point. The option's own listener shifts the stored position so the
	 * element stays visually put; returns false when the element has no anchor or already uses it.
	 */
	@JvmStatic
	fun apply(entry: HudEntry, anchor: AnchorPoint): Boolean {
		val option = option(entry) ?: return false
		if (option.get() == anchor) return false
		@Suppress("UNCHECKED_CAST")
		(option as EnumOption<Any>).set(anchor)
		return true
	}
}
