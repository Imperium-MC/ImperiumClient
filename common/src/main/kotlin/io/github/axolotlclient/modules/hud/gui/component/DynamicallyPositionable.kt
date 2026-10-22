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

import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 * <p>
 * Represents an object that width/height can change and it can react accordingly
 */
interface DynamicallyPositionable : Positionable {

    override fun getTrueX(): Int = getAnchor().getX(getRawTrueX(), getTrueWidth())

    override fun getX(): Int = getAnchor().getX(getRawX(), getWidth())

    /**
     * Get the direction that this object is anchored in
     *
     * @return [AnchorPoint] that represents where the object is anchored in
     */
    fun getAnchor(): AnchorPoint

    override fun getTrueY(): Int = getAnchor().getY(getRawTrueY(), getTrueHeight())

    override fun getY(): Int = getAnchor().getY(getRawY(), getHeight())

    override fun offsetWidth(): Int = getAnchor().offsetWidth(getWidth())

    override fun offsetHeight(): Int = getAnchor().offsetHeight(getHeight())
}
