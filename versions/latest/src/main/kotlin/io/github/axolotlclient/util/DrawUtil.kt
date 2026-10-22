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

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.modules.hud.util.Rectangle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.Function

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * License: GPL-3.0
 */
object DrawUtil {

    private val QUADS: RenderType = RenderType.create(
        "blockoutline_quads",
        RenderSetup.builder(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "blockoutline_quads"))
                .withDepthStencilState(DepthStencilState.DEFAULT).build()
        )
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    @JvmStatic
    fun fillRect(graphics: GuiGraphicsExtractor, rectangle: Rectangle, color: Color) {
        fillRect(graphics, rectangle.x, rectangle.y, rectangle.width, rectangle.height, color.toInt())
    }

    @JvmStatic
    fun fillRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + height, color)
    }

    @JvmStatic
    fun outlineRect(graphics: GuiGraphicsExtractor, rectangle: Rectangle, color: Color) {
        outlineRect(graphics, rectangle.x, rectangle.y, rectangle.width, rectangle.height, color.toInt())
    }

    @JvmStatic
    fun outlineRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + 1, color)
        graphics.fill(x, y + height - 1, x + width, y + height, color)
        graphics.fill(x, y + 1, x + 1, y + height - 1, color)
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color)
    }

    @JvmStatic
    fun drawCenteredString(graphics: GuiGraphicsExtractor, renderer: Font, text: String, x: Int, y: Int, color: Color, shadow: Boolean) {
        drawCenteredString(graphics, renderer, text, x, y, color.toInt(), shadow)
    }

    @JvmStatic
    fun drawCenteredString(graphics: GuiGraphicsExtractor, renderer: Font, text: String, x: Int, y: Int, color: Int, shadow: Boolean) {
        if (shadow) {
            graphics.centeredText(renderer, text, x, y, color)
        } else graphics.text(renderer, text, (x - renderer.width(text) / 2), y, color)
    }

    @JvmStatic
    fun drawString(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int, shadow: Boolean): Int {
        graphics.text(Minecraft.getInstance().font, text, x, y, color, shadow)
        return x + Minecraft.getInstance().font.width(text)
    }

    @JvmStatic
    fun drawString(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Color, shadow: Boolean): Int {
        return drawString(graphics, text, x, y, color.toInt(), shadow)
    }

    @JvmStatic
    fun drawOutlines(bufferSource: Function<RenderType, VertexConsumer>, pose: PoseStack.Pose, shape: VoxelShape) {
        if (AxolotlClient.config().enableCustomOutlines.get() && shouldDrawFill()) {
            val color = AxolotlClient.config().outlineFillColor.get().toInt()
            val x = 0
            val y = 0
            val z = 0
            val consumer = bufferSource.apply(QUADS)
            shape.forAllBoxes { x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double ->
                fillOutlineQuads(
                    consumer, pose, (x1 + x).toFloat(), (x2 + x).toFloat(),
                    (y1 + y).toFloat(), (y2 + y).toFloat(),
                    (z1 + z).toFloat(), (z2 + z).toFloat(),
                    color
                )
            }
        }
    }

    /**
     * The translucent fill draws in fill and both modes, and keeps honouring the legacy
     * `block_outlines.fill` toggle for configs saved before the mode option existed.
     */
    @JvmStatic
    fun shouldDrawFill(): Boolean {
        return AxolotlClient.config().outlineFill.get() ||
            AxolotlClient.config().outlineMode.get() != AxolotlClientConfigCommon.OutlineMode.OUTLINE
    }

    /**
     * In fill mode the outline lines are hidden so only the translucent highlight remains.
     */
    @JvmStatic
    fun shouldHideOutlineLines(): Boolean {
        return AxolotlClient.config().outlineMode.get() == AxolotlClientConfigCommon.OutlineMode.FILL
    }

    private fun fillOutlineQuads(consumer: VertexConsumer, matrix: PoseStack.Pose, x1: Float, x2: Float, y1: Float, y2: Float, z1: Float, z2: Float, color: Int) {
        consumer.addVertex(matrix, x2, y1, z1).setColor(color)
        consumer.addVertex(matrix, x1, y1, z1).setColor(color)
        consumer.addVertex(matrix, x1, y2, z1).setColor(color)
        consumer.addVertex(matrix, x2, y2, z1).setColor(color)

        consumer.addVertex(matrix, x1, y1, z2).setColor(color)
        consumer.addVertex(matrix, x2, y1, z2).setColor(color)
        consumer.addVertex(matrix, x2, y2, z2).setColor(color)
        consumer.addVertex(matrix, x1, y2, z2).setColor(color)

        consumer.addVertex(matrix, x1, y2, z2).setColor(color)
        consumer.addVertex(matrix, x1, y2, z1).setColor(color)
        consumer.addVertex(matrix, x1, y1, z1).setColor(color)
        consumer.addVertex(matrix, x1, y1, z2).setColor(color)

        consumer.addVertex(matrix, x2, y2, z1).setColor(color)
        consumer.addVertex(matrix, x2, y2, z2).setColor(color)
        consumer.addVertex(matrix, x2, y1, z2).setColor(color)
        consumer.addVertex(matrix, x2, y1, z1).setColor(color)

        consumer.addVertex(matrix, x2, y1, z1).setColor(color)
        consumer.addVertex(matrix, x2, y1, z2).setColor(color)
        consumer.addVertex(matrix, x1, y1, z2).setColor(color)
        consumer.addVertex(matrix, x1, y1, z1).setColor(color)

        consumer.addVertex(matrix, x2, y2, z2).setColor(color)
        consumer.addVertex(matrix, x2, y2, z1).setColor(color)
        consumer.addVertex(matrix, x1, y2, z1).setColor(color)
        consumer.addVertex(matrix, x1, y2, z2).setColor(color)
    }
}
