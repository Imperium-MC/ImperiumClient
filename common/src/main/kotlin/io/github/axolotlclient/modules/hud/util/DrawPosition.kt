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

import java.util.Objects

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class DrawPosition(@JvmField var x: Int, @JvmField var y: Int) {

    fun x(): Int = x

    fun x(x: Int) {
        this.x = x
    }

    fun y(): Int = y

    fun y(y: Int) {
        this.y = y
    }

    fun subtract(x: Int, y: Int): DrawPosition = DrawPosition(this.x - x, this.y - y)

    fun subtract(position: DrawPosition): DrawPosition = DrawPosition(position.x, position.y)

    fun divide(scale: Float): DrawPosition = DrawPosition(Math.round(x / scale), Math.round(y / scale))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawPosition) return false
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int = Objects.hash(x, y)

    override fun toString(): String = "DrawPosition(x=$x, y=$y)"
}
