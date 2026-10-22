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

/*
 * Stores a basic rectangle.
 */
class Rectangle(
    @JvmField var x: Int,
    @JvmField var y: Int,
    @JvmField var width: Int,
    @JvmField var height: Int
) {

    fun x(): Int = x

    fun x(x: Int) {
        this.x = x
    }

    fun y(): Int = y

    fun y(y: Int) {
        this.y = y
    }

    fun width(): Int = width

    fun width(width: Int) {
        this.width = width
    }

    fun height(): Int = height

    fun height(height: Int) {
        this.height = height
    }

    fun withX(x: Int): Rectangle = Rectangle(x, y, width, height)

    fun withY(y: Int): Rectangle = Rectangle(x, y, width, height)

    fun withWidth(width: Int): Rectangle = Rectangle(x, y, width, height)

    fun withHeight(height: Int): Rectangle = Rectangle(x, y, width, height)

    fun offset(offset: DrawPosition): Rectangle = Rectangle(x + offset.x, y + offset.y, width, height)

    fun offset(x: Int, y: Int): Rectangle = Rectangle(this.x + x, this.y + y, width, height)

    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height

    fun contains(pX: Int, pY: Int): Boolean = contains(x, y, width, height, pX, pY)

    fun copy(): Rectangle = Rectangle(x, y, width, height)

    fun scale(scale: Double): Rectangle =
        Rectangle((x() * scale).toInt(), (y() * scale).toInt(), (width() * scale).toInt(), (height() * scale).toInt())

    fun xEnd(): Int = x() + width()

    fun yEnd(): Int = y() + height()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rectangle) return false
        return x == other.x && y == other.y && width == other.width && height == other.height
    }

    override fun hashCode(): Int = Objects.hash(x, y, width, height)

    override fun toString(): String = "Rectangle(x=$x, y=$y, width=$width, height=$height)"

    companion object {
        @JvmStatic
        fun contains(x: Int, y: Int, width: Int, height: Int, pX: Int, pY: Int): Boolean =
            pX >= x && pX <= x + width && pY >= y && pY <= y + height
    }
}
