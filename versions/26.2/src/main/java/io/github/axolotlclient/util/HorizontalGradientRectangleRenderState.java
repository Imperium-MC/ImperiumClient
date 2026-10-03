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

package io.github.axolotlclient.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record HorizontalGradientRectangleRenderState(RenderPipeline pipeline,
													 TextureSetup textureSetup,
													 Matrix3x2f pose,
													 int x0,
													 int y0,
													 int x1,
													 int y1,
													 int col1,
													 int col2,
													 @Nullable ScreenRectangle scissorArea,
													 @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

	public static HorizontalGradientRectangleRenderState create(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col1, int col2) {
		var matrix = new Matrix3x2f(graphics.pose());
		var area = graphics.scissorStack.peek();
		return new HorizontalGradientRectangleRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), matrix, x0, y0, x1, y1, col1, col2, area, getBounds(x0, y0, x1, y1, matrix, area));
	}

	public void submit() {
		Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.addGuiElement(this);
	}

	@Override
	public void buildVertices(VertexConsumer vertexConsumer) {
		vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0()).setColor(this.col1());
		vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1()).setColor(this.col1());
		vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1()).setColor(this.col2());
		vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0()).setColor(this.col2());
	}

	@Nullable
	private static ScreenRectangle getBounds(int i, int j, int k, int l, Matrix3x2f matrix3x2f, @Nullable ScreenRectangle screenRectangle) {
		ScreenRectangle screenRectangle2 = new ScreenRectangle(i, j, k - i, l - j).transformMaxBounds(matrix3x2f);
		return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
	}
}
