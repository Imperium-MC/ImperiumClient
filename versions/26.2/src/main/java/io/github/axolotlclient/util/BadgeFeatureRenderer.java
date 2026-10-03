/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util;

import java.util.List;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class BadgeFeatureRenderer extends RenderTypeFeatureRenderer<BadgeFeatureRenderer.Submit> {
	public static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("AxolotlClient Badge");
	private static final RenderType TEXTURED_TYPE = RenderType.create("axolotlclient_textured_quads",
		RenderSetup.builder(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "pipeline/badge"))
				.withDepthStencilState(DepthStencilState.DEFAULT).build())
			.withTexture("Sampler0", (Identifier) AxolotlClientCommon.BADGE_PATH)
			.setTextureTransform(TextureTransform.DEFAULT_TEXTURING)
			.createRenderSetup());

	@Override
	protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {

		var builder = getVertexBuilder(TEXTURED_TYPE);
		submits.forEach(submit -> {
			var x = submit.x;
			var y = submit.y;
			builder.addVertex(submit.pose, x, y, 0).setUv(0, 0).setColor(-1);
			builder.addVertex(submit.pose, x, y + 8, 0).setUv(0, 1).setColor(-1);
			builder.addVertex(submit.pose, x + 8, y + 8, 0).setUv(1, 1).setColor(-1);
			builder.addVertex(submit.pose, x + 8, y, 0).setUv(1, 0).setColor(-1);
		});
	}

	public record Submit(Matrix4f pose, float x, int y) implements SubmitNode {
		@Override
		public FeatureRendererType<? extends SubmitNode> featureType() {
			return TYPE;
		}
	}
}
