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

package io.github.axolotlclient.modules.hud.util

import io.github.axolotlclient.util.IdentifiablePiPRenderState
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Kotlin note: this was a Java record. [PictureInPictureRenderState] declares record-style
 * accessor *functions* (x0(), scale(), ...) that a Java record satisfies implicitly through
 * its component accessors; a Kotlin @JvmRecord's accessors are not treated as
 * implementations (the HorizontalGradientRectangleRenderState precedent), so the accessors
 * and the record's equals/hashCode/toString semantics are spelled out here instead.
 */
class PlayerHudEntityRenderState(
    private val renderState: EntityRenderState,
    private val translation: Vector3f,
    private val rotation: Quaternionf,
    private val overrideCameraAngle: Quaternionf?,
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int,
    private val scale: Float,
    scissorArea: ScreenRectangle?,
    bounds: ScreenRectangle?,
    private val renderer: PlayerHudEntityRenderer
) : PictureInPictureRenderState, IdentifiablePiPRenderState<PlayerHudEntityRenderer> {

    private val scissorArea: ScreenRectangle?
    private val bounds: ScreenRectangle?

    constructor(
        entityRenderState: EntityRenderState,
        vector3f: Vector3f,
        quaternionf: Quaternionf,
        quaternionf2: Quaternionf?,
        i: Int,
        j: Int,
        k: Int,
        l: Int,
        f: Float,
        screenRectangle: ScreenRectangle?,
        renderer: PlayerHudEntityRenderer
    ) : this(
        entityRenderState, vector3f, quaternionf, quaternionf2, i, j, k, l, f, screenRectangle,
        PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle), renderer
    )

    init {
        this.scissorArea = scissorArea
        this.bounds = bounds
    }

    fun renderState(): EntityRenderState = renderState

    fun translation(): Vector3f = translation

    fun rotation(): Quaternionf = rotation

    fun overrideCameraAngle(): Quaternionf? = overrideCameraAngle

    override fun x0(): Int = x0

    override fun y0(): Int = y0

    override fun x1(): Int = x1

    override fun y1(): Int = y1

    override fun scale(): Float = scale

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle? = bounds

    override fun renderer(): PlayerHudEntityRenderer = renderer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerHudEntityRenderState) return false
        return renderState == other.renderState && translation == other.translation && rotation == other.rotation &&
            overrideCameraAngle == other.overrideCameraAngle && x0 == other.x0 && y0 == other.y0 && x1 == other.x1 &&
            y1 == other.y1 && scale.compareTo(other.scale) == 0 && scissorArea == other.scissorArea &&
            bounds == other.bounds && renderer == other.renderer
    }

    override fun hashCode(): Int {
        var result = renderState.hashCode()
        result = 31 * result + translation.hashCode()
        result = 31 * result + rotation.hashCode()
        result = 31 * result + (overrideCameraAngle?.hashCode() ?: 0)
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
        "PlayerHudEntityRenderState[renderState=$renderState, translation=$translation, rotation=$rotation, " +
            "overrideCameraAngle=$overrideCameraAngle, x0=$x0, y0=$y0, x1=$x1, y1=$y1, scale=$scale, " +
            "scissorArea=$scissorArea, bounds=$bounds, renderer=$renderer]"
}
