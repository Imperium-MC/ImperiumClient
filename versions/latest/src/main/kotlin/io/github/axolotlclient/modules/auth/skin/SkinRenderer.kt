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

import java.util.concurrent.ConcurrentHashMap

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Unit

class SkinRenderer private constructor(private val minecraft: Minecraft, private val id: String) :
    PictureInPictureRenderer<SkinRenderState>() {

    private var classicModel: Model.Simple? = null
    private var slimModel: Model.Simple? = null
    private var capeModel: Model.Simple? = null

    override fun getRenderStateClass(): Class<SkinRenderState> = SkinRenderState::class.java

    override fun renderToTexture(renderState: SkinRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        if (classicModel == null && renderState.classicVariant()) {
            classicModel = Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent)
        }
        if (slimModel == null && !renderState.classicVariant()) {
            this.slimModel = Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent)
        }
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.PLAYER_SKIN)
        val i = Minecraft.getInstance().getWindow().getGuiScale()
        val matrix4fStack = RenderSystem.getModelViewStack()
        matrix4fStack.pushMatrix()
        val f = renderState.scale() * i
        matrix4fStack.rotateAround(Axis.XP.rotationDegrees(renderState.rotationX()), 0.0f, f * -renderState.pivotY(), 0.0f)
        poseStack.rotate(Axis.YP.rotationDegrees(-renderState.rotationY()))
        poseStack.translate(0.0f, -1.6010001f, 0.0f)
        val model = if (renderState.classicVariant()) classicModel else slimModel
        collector.submitModel(model!!, Unit.INSTANCE, poseStack, renderState.skinTexture(), 15728880, OverlayTexture.NO_OVERLAY, 0)
        if (renderState.cape() != null) {
            if (capeModel == null) {
                capeModel = Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_CAPE), RenderTypes::entityTranslucent)
            }
            poseStack.rotate(Axis.XP.rotationDegrees(6.0f))
            collector.submitModel(capeModel!!, Unit.INSTANCE, poseStack, renderState.cape()!!, 15728880, OverlayTexture.NO_OVERLAY, 0)
        }
        matrix4fStack.popMatrix()
    }

    override fun getTextureLabel(): String = "axolotlclient/skin_render/$id"

    companion object {
        private val renderers = ConcurrentHashMap<String, SkinRenderer>()

        @JvmStatic
        fun closeRenderers() {
            renderers.values.forEach(PictureInPictureRenderer<*>::close)
            renderers.clear()
        }

        @JvmStatic
        fun getOrCreate(minecraft: Minecraft, id: String): SkinRenderer =
            renderers.computeIfAbsent(id) { _ -> SkinRenderer(minecraft, id) }
    }
}
