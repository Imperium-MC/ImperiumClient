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

package io.github.axolotlclient.modules.hud.util

import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.DoubleOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.gui.layout.CardinalOrder
import io.github.axolotlclient.util.options.ForceableBooleanOption

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

object DefaultOptions {
    @JvmStatic
    fun getX(defaultX: Double): DoubleOption = DoubleOption("x", defaultX, 0.0, 1.0)

    @JvmStatic
    fun getY(defaultY: Double): DoubleOption = DoubleOption("y", defaultY, 0.0, 1.0)

    @JvmStatic
    fun getScale(entry: HudEntry): DoubleOption = DoubleOption("scale", 1.0, { entry.onBoundsUpdate() }, 0.0, 2.0)

    @JvmStatic
    fun getEnabled(entry: HudEntry): ForceableBooleanOption = ForceableBooleanOption("enabled", false) { v ->
        if (!v) {
            entry.clearBoundsDependencies()
            HudManagerCommon.getInstance()!!.getMoveableEntries().forEach { e ->
                e.removeBoundsDependencyX(entry)
                e.removeBoundsDependencyY(entry)
            }
        }
    }

    @JvmStatic
    fun getAnchorPoint(entry: HudEntry): EnumOption<AnchorPoint> = getAnchorPoint(AnchorPoint.TOP_LEFT, entry)

    @JvmStatic
    fun getAnchorPoint(defaultValue: AnchorPoint, entry: HudEntry): EnumOption<AnchorPoint> =
        EnumOption("anchorpoint", AnchorPoint::class.java, defaultValue) {
            val storedX = entry.getRawTrueX()
            val storedY = entry.getRawTrueY()
            val offsetX = entry.offsetTrueWidth()
            val offsetY = entry.offsetTrueHeight()
            entry.setX(storedX + offsetX)
            entry.setY(storedY + offsetY)
        }

    @JvmStatic
    fun getCardinalOrder(defaultValue: CardinalOrder): EnumOption<CardinalOrder> =
        EnumOption("cardinalorder", CardinalOrder::class.java, defaultValue)

    @JvmStatic
    fun getHideIfEmpty(): BooleanOption = BooleanOption("hud.hide_if_empty", true)
}
