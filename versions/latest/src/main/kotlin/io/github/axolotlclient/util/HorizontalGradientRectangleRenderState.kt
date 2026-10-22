/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f

/**
 * Kotlin note: this was a Java record. [GuiElementRenderState] declares record-style accessor
 * *functions* (`pipeline()`, `bounds()`, ...) that a Java record satisfies implicitly through its
 * component accessors; a Kotlin @JvmRecord's accessors are not treated as implementations, so the
 * accessors (and the record's equals/hashCode/toString semantics) are spelled out here instead.
 */
class HorizontalGradientRectangleRenderState(
    private val pipeline: RenderPipeline,
    private val textureSetup: TextureSetup,
    private val pose: Matrix3x2f,
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int,
    private val col1: Int,
    private val col2: Int,
    private val scissorArea: ScreenRectangle?,
    private val bounds: ScreenRectangle?
) : GuiElementRenderState {

    fun submit() {
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.addGuiElement(this)
    }

    override fun buildVertices(vertexConsumer: VertexConsumer) {
        vertexConsumer.addVertexWith2DPose(this.pose, this.x0.toFloat(), this.y0.toFloat()).setColor(this.col1)
        vertexConsumer.addVertexWith2DPose(this.pose, this.x0.toFloat(), this.y1.toFloat()).setColor(this.col1)
        vertexConsumer.addVertexWith2DPose(this.pose, this.x1.toFloat(), this.y1.toFloat()).setColor(this.col2)
        vertexConsumer.addVertexWith2DPose(this.pose, this.x1.toFloat(), this.y0.toFloat()).setColor(this.col2)
    }

    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    fun pose(): Matrix3x2f = pose

    fun x0(): Int = x0

    fun y0(): Int = y0

    fun x1(): Int = x1

    fun y1(): Int = y1

    fun col1(): Int = col1

    fun col2(): Int = col2

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle? = bounds

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HorizontalGradientRectangleRenderState) return false
        return pipeline == other.pipeline &&
            textureSetup == other.textureSetup &&
            pose == other.pose &&
            x0 == other.x0 &&
            y0 == other.y0 &&
            x1 == other.x1 &&
            y1 == other.y1 &&
            col1 == other.col1 &&
            col2 == other.col2 &&
            scissorArea == other.scissorArea &&
            bounds == other.bounds
    }

    override fun hashCode(): Int {
        var result = pipeline.hashCode()
        result = 31 * result + textureSetup.hashCode()
        result = 31 * result + pose.hashCode()
        result = 31 * result + x0
        result = 31 * result + y0
        result = 31 * result + x1
        result = 31 * result + y1
        result = 31 * result + col1
        result = 31 * result + col2
        result = 31 * result + (scissorArea?.hashCode() ?: 0)
        result = 31 * result + (bounds?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "HorizontalGradientRectangleRenderState[pipeline=$pipeline, textureSetup=$textureSetup, " +
            "pose=$pose, x0=$x0, y0=$y0, x1=$x1, y1=$y1, col1=$col1, col2=$col2, " +
            "scissorArea=$scissorArea, bounds=$bounds]"

    companion object {
        @JvmStatic
        fun create(graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, col1: Int, col2: Int): HorizontalGradientRectangleRenderState {
            val matrix = Matrix3x2f(graphics.pose())
            val area = graphics.scissorStack.peek()
            return HorizontalGradientRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), matrix, x0, y0, x1, y1, col1, col2,
                area, getBounds(x0, y0, x1, y1, matrix, area)
            )
        }

        private fun getBounds(i: Int, j: Int, k: Int, l: Int, matrix3x2f: Matrix3x2f, screenRectangle: ScreenRectangle?): ScreenRectangle? {
            val screenRectangle2 = ScreenRectangle(i, j, k - i, l - j).transformMaxBounds(matrix3x2f)
            return screenRectangle?.intersection(screenRectangle2) ?: screenRectangle2
        }
    }
}
