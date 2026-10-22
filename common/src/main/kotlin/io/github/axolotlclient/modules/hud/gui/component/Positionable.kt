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

import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hud.util.Rectangle

interface Positionable {

    fun setPos(x: Int, y: Int) {
        setX(x)
        setY(y)
    }

    /**
     * Gets the x value of this object in pixels (so it's scaled)
     *
     * @return True X position
     */
    fun getTrueX(): Int = getRawTrueX()

    /**
     * Set the true x value, this is equivalent to pixels
     *
     * @param trueX Pixel value of x
     */
    fun setTrueX(trueX: Int) {
        setX((trueX / getScale()).toInt())
    }

    /**
     * Gets the x value of this object in pixels (so it's scaled). This may not be accurate so use [getTrueX]
     *
     * @return True X position
     */
    fun getRawTrueX(): Int = (getX() * getScale()).toInt()

    /**
     * Gets the x value of this object. This is unscaled so this may not be the exact pixel value
     *
     * @return Unscaled X position
     */
    fun getX(): Int = getRawX()

    /**
     * Set the raw x value (this does not mean pixels!)
     *
     * @param x Raw x value
     */
    fun setX(x: Int)

    /**
     * The scale to render this object in. By default, it is one
     *
     * @return The scale
     */
    fun getScale(): Float = 1f

    fun setScale(max: Float) {
    }

    /**
     * Gets the x value that is stored locally. This may not be correct so use [getX]
     *
     * @return Unscaled X position
     */
    fun getRawX(): Int

    /**
     * Gets the y value of this object in pixels (so it's scaled)
     *
     * @return True Y position
     */
    fun getTrueY(): Int = getRawTrueY()

    /**
     * Set the true y value, this is equivalent to pixels
     *
     * @param trueY Pixel value of y
     */
    fun setTrueY(trueY: Int) {
        setY((trueY / getScale()).toInt())
    }

    /**
     * Gets the y value of this object in pixels (so it's scaled). This may not be accurate so use [getTrueY]
     *
     * @return True Y position
     */
    fun getRawTrueY(): Int = (getY() * getScale()).toInt()

    /**
     * Gets the y value of this object. This is unscaled so this may not be the exact pixel value
     *
     * @return Y Unscaled Y position
     */
    fun getY(): Int = getRawY()

    /**
     * Set the raw y value (this does not mean pixels!)
     *
     * @param y Raw y value
     */
    fun setY(y: Int)

    /**
     * Gets the y value that is stored locally. This may not be correct so use [getY]
     *
     * @return Y Unscaled Y position
     */
    fun getRawY(): Int

    /**
     * Returns the rectangle that represents the boundaries of where this object will be rendered when scaled
     *
     * @return The scaled rectangle
     */
    fun getBounds(): Rectangle {
        val pos = getPos()
        return Rectangle(pos.x, pos.y, getWidth(), getHeight())
    }

    /**
     * Returns the draw position when render is unscaled. This does not reflect pixel values
     *
     * @return Position
     */
    fun getPos(): DrawPosition = DrawPosition(getRawX(), getRawY())

    /**
     * The width of the object, unscaled
     *
     * @return The width of this object
     */
    fun getWidth(): Int

    /**
     * Sets the raw width (this is unscaled)
     */
    fun setWidth(width: Int)

    /**
     * The height of the object, unscaled
     *
     * @return The height of this object
     */
    fun getHeight(): Int

    /**
     * Sets the raw height (this is unscaled)
     */
    fun setHeight(height: Int)

    /**
     * Returns the rectangle that represents the boundaries of where this object will be rendered on the screen. This is unscaled
     *
     * @return The unscaled rectangle
     */
    fun getTrueBounds(): Rectangle {
        val pos = getTruePos()
        return Rectangle(pos.x, pos.y, getTrueWidth(), getTrueHeight())
    }

    /**
     * Returns the draw position that matches the screen positioned. This reflects pixel values
     *
     * @return Position
     */
    fun getTruePos(): DrawPosition = DrawPosition(getRawTrueX(), getRawTrueY())

    /**
     * The width of the object, scaled. If scale > 1 this is greater than #getWidth. This is the width in pixels basically.
     *
     * @return Scaled width of the object
     */
    fun getTrueWidth(): Int = (getWidth() * getScale()).toInt()

    /**
     * The height of the object, scaled. If scale > 1 this is greater than #getHeight. This is the height in pixels basically.
     *
     * @return Scaled height of the object
     */
    fun getTrueHeight(): Int = (getHeight() * getScale()).toInt()

    /**
     * Updated when location/size changes
     */
    fun onBoundsUpdate() {
    }

    /**
     * If this component should be able to move
     *
     * @return Movable option
     */
    fun movable(): Boolean = true

    fun offsetTrueWidth(): Int = (offsetWidth() * getScale()).toInt()

    /**
     * The height modifier after taking raw position. This is really only used for [DynamicallyPositionable]
     *
     * @return The width to shift anchor
     */
    fun offsetWidth(): Int = 0

    fun offsetTrueHeight(): Int = (offsetHeight() * getScale()).toInt()

    fun offsetHeight(): Int = 0
}
