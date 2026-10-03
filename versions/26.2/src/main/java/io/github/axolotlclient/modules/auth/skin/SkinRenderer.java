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

package io.github.axolotlclient.modules.auth.skin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.NullMarked;

public class SkinRenderer extends PictureInPictureRenderer<SkinRenderState> {
	private static final Map<String, SkinRenderer> renderers = new ConcurrentHashMap<>();

	public static void closeRenderers() {
		renderers.values().forEach(PictureInPictureRenderer::close);
		renderers.clear();
	}

	public static SkinRenderer getOrCreate(Minecraft minecraft, String id) {
		return renderers.computeIfAbsent(id, _id -> new SkinRenderer(minecraft, id));
	}

	private Model.Simple classicModel, slimModel, capeModel;
	private final Minecraft minecraft;
	private final String id;

	private SkinRenderer(Minecraft minecraft, String id) {
		super();
		this.minecraft = minecraft;
		this.id = id;
	}

	@Override
	public @NotNull Class<SkinRenderState> getRenderStateClass() {
		return SkinRenderState.class;
	}

	@NullMarked
	@Override
	protected void renderToTexture(SkinRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector) {
		if (classicModel == null && renderState.classicVariant()) {
			classicModel = new Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
		}
		if (slimModel == null && !renderState.classicVariant()) {
			this.slimModel = new Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
		}
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		float f = renderState.scale() * i;
		matrix4fStack.rotateAround(Axis.XP.rotationDegrees(renderState.rotationX()), 0.0F, f * -renderState.pivotY(), 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-renderState.rotationY()));
		poseStack.translate(0.0F, -1.6010001F, 0.0F);
		var model = renderState.classicVariant() ? classicModel : slimModel;
		collector.submitModel(model, Unit.INSTANCE, poseStack, renderState.skinTexture(), 15728880, OverlayTexture.NO_OVERLAY, 0, null);
		if (renderState.cape() != null) {
			if (capeModel == null) {
				capeModel = new Model.Simple(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_CAPE), RenderTypes::entityTranslucent);
			}
			poseStack.mulPose(Axis.XP.rotationDegrees(6.0F));
			collector.submitModel(capeModel, Unit.INSTANCE, poseStack, renderState.cape(), 15728880, OverlayTexture.NO_OVERLAY, 0, null);
		}
		matrix4fStack.popMatrix();
	}

	@Override
	protected @NotNull String getTextureLabel() {
		return "axolotlclient/skin_render/" + id;
	}
}
