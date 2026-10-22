/*
 * Copyright © 2023 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hud.gui.component

import java.util.Optional

import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.layout.SnapAnchorType

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

interface HudEntry : Identifiable, Configurable, Positionable {

    fun isEnabled(): Boolean

    fun getZLevel(): Int = 0

    fun setEnabled(value: Boolean)

    fun tickable(): Boolean = false

    /**
     * Whether this entry's rendered content changes on its own while the game runs (timers,
     * counters, live positions, animations) rather than only through discrete events. Entries
     * that answer `true` are never served from the `hud_frame_limit` recording —
     * they re-render every frame so FPS/CPS/coordinates-style readouts never visibly freeze.
     */
    fun dynamicContent(): Boolean = false

    fun overridesF3(): Boolean = false

    fun tick() {
    }

    fun init() {
    }

    fun postConfigLoad() {
    }

    fun getDefaultX(): Double = 0.0

    fun getDefaultY(): Double = 0.0

    fun render(ctx: AxoRenderContext, delta: Float)

    fun renderPlaceholder(ctx: AxoRenderContext, delta: Float)

    fun setHovered(hovered: Boolean)

    fun isHovered(): Boolean

    fun isHidden(): Boolean

    fun supportsScaling(): Boolean

    fun dependsOnX(entry: HudEntry): Optional<SnapAnchorType>

    fun dependsOnY(entry: HudEntry): Optional<SnapAnchorType>

    fun addBoundsDependency(dependency: HudEntry, type: SnapAnchorType)

    fun removeBoundsDependencyX(entry: HudEntry)

    fun removeBoundsDependencyY(entry: HudEntry)

    fun clearBoundsDependencies()

    fun getDependenciesX(): Map<HudEntry, SnapAnchorType>

    fun getDependenciesY(): Map<HudEntry, SnapAnchorType>
}
