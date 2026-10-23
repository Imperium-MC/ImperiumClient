/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.auth.skin

import io.github.axolotlclient.util.IdentifiablePiPRenderState
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.resources.Identifier

/**
 * Kotlin note: this was a Java record; the MC record-style accessor *functions* are spelled
 * out because a Kotlin @JvmRecord's accessors are not treated as implementations (the
 * HorizontalGradientRectangleRenderState / PlayerHudEntityRenderState precedent).
 */
class SkinRenderState(
    private val classicVariant: Boolean,
    private val skinTexture: Identifier,
    private val cape: Identifier?,
    private val rotationX: Float,
    private val rotationY: Float,
    private val pivotY: Float,
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int,
    private val scale: Float,
    scissorArea: ScreenRectangle?,
    bounds: ScreenRectangle?,
    private val renderer: SkinRenderer
) : PictureInPictureRenderState, IdentifiablePiPRenderState<SkinRenderer> {

    private val scissorArea: ScreenRectangle?
    private val bounds: ScreenRectangle?

    constructor(
        classicVariant: Boolean, skinTexture: Identifier, cape: Identifier?, rotationX: Float,
        rotationY: Float, pivotY: Float, x0: Int, y0: Int, x1: Int, y1: Int, scale: Float,
        scissorArea: ScreenRectangle?, renderer: SkinRenderer
    ) : this(
        classicVariant, skinTexture, cape, rotationX, rotationY, pivotY, x0, y0, x1, y1, scale, scissorArea,
        PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), renderer
    )

    init {
        this.scissorArea = scissorArea
        this.bounds = bounds
    }

    fun classicVariant(): Boolean = classicVariant

    fun skinTexture(): Identifier = skinTexture

    fun cape(): Identifier? = cape

    fun rotationX(): Float = rotationX

    fun rotationY(): Float = rotationY

    fun pivotY(): Float = pivotY

    override fun x0(): Int = x0

    override fun y0(): Int = y0

    override fun x1(): Int = x1

    override fun y1(): Int = y1

    override fun scale(): Float = scale

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle? = bounds

    override fun renderer(): SkinRenderer = renderer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkinRenderState) return false
        return classicVariant == other.classicVariant && skinTexture == other.skinTexture && cape == other.cape &&
            rotationX == other.rotationX && rotationY == other.rotationY && pivotY == other.pivotY && x0 == other.x0 &&
            y0 == other.y0 && x1 == other.x1 && y1 == other.y1 && scale.compareTo(other.scale) == 0 &&
            scissorArea == other.scissorArea && bounds == other.bounds && renderer == other.renderer
    }

    override fun hashCode(): Int {
        var result = classicVariant.hashCode()
        result = 31 * result + skinTexture.hashCode()
        result = 31 * result + (cape?.hashCode() ?: 0)
        result = 31 * result + rotationX.hashCode()
        result = 31 * result + rotationY.hashCode()
        result = 31 * result + pivotY.hashCode()
        result = 31 * result + x0
        result = 31 * result + y0
        result = 31 * result + x1
        result = 31 * result + y1
        result = 31 * result + scale.hashCode()
        result = 31 * result + (scissorArea?.hashCode() ?: 0)
        result = 31 * result + (bounds?.hashCode() ?: 0)
        result = 31 * result + renderer.hashCode()
        return result
    }

    override fun toString(): String =
        "SkinRenderState[classicVariant=$classicVariant, skinTexture=$skinTexture, cape=$cape, rotationX=$rotationX, " +
            "rotationY=$rotationY, pivotY=$pivotY, x0=$x0, y0=$y0, x1=$x1, y1=$y1, scale=$scale, " +
            "scissorArea=$scissorArea, bounds=$bounds, renderer=$renderer]"
}
