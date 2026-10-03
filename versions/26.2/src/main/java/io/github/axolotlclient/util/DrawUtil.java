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

package io.github.axolotlclient.util;

import java.util.function.Function;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class DrawUtil {

	public static void fillRect(GuiGraphicsExtractor graphics, Rectangle rectangle, Color color) {
		fillRect(graphics, rectangle.x, rectangle.y, rectangle.width, rectangle.height, color.toInt());
	}

	public static void fillRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + height, color);
	}

	public static void outlineRect(GuiGraphicsExtractor graphics, Rectangle rectangle, Color color) {
		outlineRect(graphics, rectangle.x, rectangle.y, rectangle.width, rectangle.height, color.toInt());
	}

	public static void outlineRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	public static void drawCenteredString(GuiGraphicsExtractor graphics, Font renderer, String text, int x, int y, Color color, boolean shadow) {
		drawCenteredString(graphics, renderer, text, x, y, color.toInt(), shadow);
	}

	public static void drawCenteredString(GuiGraphicsExtractor graphics, Font renderer, String text, int x, int y, int color, boolean shadow) {
		if (shadow) {
			graphics.centeredText(renderer, text, x, y, color);
		} else graphics.text(renderer, text, (x - renderer.width(text) / 2), y, color);
	}

	public static int drawString(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean shadow) {
		graphics.text(Minecraft.getInstance().font, text, x, y, color, shadow);
		return x + Minecraft.getInstance().font.width(text);
	}

	public static int drawString(GuiGraphicsExtractor graphics, String text, int x, int y, Color color, boolean shadow) {
		return drawString(graphics, text, x, y, color.toInt(), shadow);
	}

	private static final RenderType QUADS = RenderType.create("blockoutline_quads", RenderSetup.builder(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "blockoutline_quads"))
			.withDepthStencilState(DepthStencilState.DEFAULT).build())
		.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
		.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).createRenderSetup());

	public static void drawOutlines(Function<RenderType, VertexConsumer> bufferSource, PoseStack.Pose pose, VoxelShape shape) {
		if (AxolotlClient.config().enableCustomOutlines.get() && AxolotlClient.config().outlineFill.get()) {
			var color = AxolotlClient.config().outlineFillColor.get().toInt();
			//var pos = levelRenderState.cameraRenderState.pos;
			//var blockPos = state.pos();
			var x = 0;//blockPos.getX() - pos.x();
			var y = 0;//blockPos.getY() - pos.y();
			var z = 0;//blockPos.getZ() - pos.z();
			var consumer = bufferSource.apply(QUADS);
			shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
				fillOutlineQuads(consumer, pose, (float) (x1 + x), (float) (x2 + x),
					(float) (y1 + y), (float) (y2 + y),
					(float) (z1 + z), (float) (z2 + z),
					color));
		}
	}

	private static void fillOutlineQuads(VertexConsumer consumer, PoseStack.Pose matrix, float x1, float x2, float y1, float y2, float z1, float z2, int color) {
		consumer.addVertex(matrix, x2, y1, z1).setColor(color);
		consumer.addVertex(matrix, x1, y1, z1).setColor(color);
		consumer.addVertex(matrix, x1, y2, z1).setColor(color);
		consumer.addVertex(matrix, x2, y2, z1).setColor(color);

		consumer.addVertex(matrix, x1, y1, z2).setColor(color);
		consumer.addVertex(matrix, x2, y1, z2).setColor(color);
		consumer.addVertex(matrix, x2, y2, z2).setColor(color);
		consumer.addVertex(matrix, x1, y2, z2).setColor(color);

		consumer.addVertex(matrix, x1, y2, z2).setColor(color);
		consumer.addVertex(matrix, x1, y2, z1).setColor(color);
		consumer.addVertex(matrix, x1, y1, z1).setColor(color);
		consumer.addVertex(matrix, x1, y1, z2).setColor(color);

		consumer.addVertex(matrix, x2, y2, z1).setColor(color);
		consumer.addVertex(matrix, x2, y2, z2).setColor(color);
		consumer.addVertex(matrix, x2, y1, z2).setColor(color);
		consumer.addVertex(matrix, x2, y1, z1).setColor(color);

		consumer.addVertex(matrix, x2, y1, z1).setColor(color);
		consumer.addVertex(matrix, x2, y1, z2).setColor(color);
		consumer.addVertex(matrix, x1, y1, z2).setColor(color);
		consumer.addVertex(matrix, x1, y1, z1).setColor(color);

		consumer.addVertex(matrix, x2, y2, z2).setColor(color);
		consumer.addVertex(matrix, x2, y2, z1).setColor(color);
		consumer.addVertex(matrix, x1, y2, z1).setColor(color);
		consumer.addVertex(matrix, x1, y2, z2).setColor(color);
	}
}
