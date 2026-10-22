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

import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.feature.FeatureFrameContext
import net.minecraft.client.renderer.feature.FeatureRendererType
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer
import net.minecraft.client.renderer.feature.submit.SubmitNode
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.resources.Identifier
import org.joml.Matrix4f

class BadgeFeatureRenderer : RenderTypeFeatureRenderer<BadgeFeatureRenderer.Submit>() {

    override fun buildGroup(context: FeatureFrameContext, submits: List<Submit>) {
        val builder = getVertexBuilder(TEXTURED_TYPE)
        submits.forEach { submit ->
            val x = submit.x
            val y = submit.y
            builder.addVertex(submit.pose, x, y.toFloat(), 0f).setUv(0f, 0f).setColor(-1)
            builder.addVertex(submit.pose, x, (y + 8).toFloat(), 0f).setUv(0f, 1f).setColor(-1)
            builder.addVertex(submit.pose, (x + 8), (y + 8).toFloat(), 0f).setUv(1f, 1f).setColor(-1)
            builder.addVertex(submit.pose, (x + 8), y.toFloat(), 0f).setUv(1f, 0f).setColor(-1)
        }
    }

    @JvmRecord
    data class Submit(val pose: Matrix4f, val x: Float, val y: Int) : SubmitNode {
        override fun featureType(): FeatureRendererType<out SubmitNode> = TYPE
    }

    companion object {
        @JvmField
        val TYPE: FeatureRendererType<Submit> = FeatureRendererType.create("AxolotlClient Badge")

        private val TEXTURED_TYPE: RenderType = RenderType.create(
            "axolotlclient_textured_quads",
            RenderSetup.builder(
                RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "pipeline/badge"))
                    .withDepthStencilState(DepthStencilState.DEFAULT).build()
            )
                .withTexture("Sampler0", AxolotlClientCommon.BADGE_PATH as Identifier)
                .setTextureTransform(TextureTransform.DEFAULT_TEXTURING)
                .createRenderSetup()
        )
    }
}
