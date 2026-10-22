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

package io.github.axolotlclient.modules.hud.gui.layout

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

enum class AnchorPoint(val xComponent: Int, val yComponent: Int) {
    TOP_LEFT(-1, 1), TOP_MIDDLE(0, 1), TOP_RIGHT(1, 1),
    MIDDLE_LEFT(-1, 0), MIDDLE_MIDDLE(0, 0), MIDDLE_RIGHT(1, 0),
    BOTTOM_LEFT(-1, -1), BOTTOM_MIDDLE(0, -1), BOTTOM_RIGHT(1, -1),
    ;

    fun getX(anchorX: Int, width: Int): Int = when (xComponent) {
        0 -> anchorX - (width / 2)
        1 -> anchorX - width
        else -> anchorX
    }

    fun getY(anchorY: Int, height: Int): Int = when (yComponent) {
        0 -> anchorY - (height / 2)
        1 -> anchorY - height
        else -> anchorY
    }

    fun offsetWidth(width: Int): Int = when (xComponent) {
        0 -> width / 2
        1 -> width
        else -> 0
    }

    fun offsetHeight(height: Int): Int = when (yComponent) {
        0 -> height / 2
        1 -> 0
        else -> height
    }
}
