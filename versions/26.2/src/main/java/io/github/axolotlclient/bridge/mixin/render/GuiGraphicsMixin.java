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

package io.github.axolotlclient.bridge.mixin.render;

import io.github.axolotlclient.bridge.impl.AxoSpriteImpl;
import io.github.axolotlclient.bridge.item.AxoItemStack;
import io.github.axolotlclient.bridge.render.AxoFont;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.render.AxoSprite;
import io.github.axolotlclient.bridge.util.AxoText;
import io.github.axolotlclient.util.DrawUtil;
import io.github.axolotlclient.util.HorizontalGradientRectangleRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin implements AxoRenderContext {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private Matrix3x2fStack pose;

	@Shadow
	public abstract void enableScissor(int x1, int y1, int x2, int y2);

	@Shadow
	public abstract void disableScissor();

	@Shadow
	public abstract void fill(int x1, int y1, int x2, int y2, int color);

	@Shadow
	public abstract void fillGradient(int startX, int startY, int endX, int endY, int startColor, int endColor);

	@Shadow
	public abstract void text(Font font, Component str, int x, int y, int color, boolean dropShadow);

	@Shadow
	public abstract void text(Font font, @Nullable String str, int x, int y, int color, boolean dropShadow);

	@Shadow
	public abstract void item(ItemStack itemStack, int x, int y);

	@Shadow
	public abstract void itemDecorations(Font font, ItemStack itemStack, int x, int y, @Nullable String countText);

	@Shadow
	public abstract int guiHeight();

	@Shadow
	public abstract int guiWidth();

	@Shadow
	public abstract void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow);

	@Shadow
	public abstract void centeredText(Font font, String str, int x, int y, int color);

	@Unique
	private @NotNull GuiGraphicsExtractor self() {
		return (GuiGraphicsExtractor) (Object) this;
	}

	@Override
	public void br$popMatrix() {
		pose.popMatrix();
	}

	@Override
	public void br$pushMatrix() {
		pose.pushMatrix();
	}

	@Override
	public void br$scaleMatrix(float sx, float sy) {
		pose.scale(sx, sy);
	}

	@Override
	public void br$translateMatrix(float x, float y) {
		pose.translate(x, y);
	}

	@Override
	public void br$rotateMatrix(float ang) {
		pose.rotate(ang);
	}

	@Override
	public void br$rotateMatrixAround(float ang, float x, float y) {
		pose.rotateAbout(ang, x, y);
	}

	// scissor
	@Override
	public void br$pushScissor(int x, int y, int w, int h) {
		enableScissor(x, y, x + w, y + h);
	}

	@Override
	public void br$popScissor() {
		disableScissor();
	}

	@Override
	public int br$drawString(String value, int x, int y, int color, boolean shadow) {
		text(minecraft.font, value, x, y, color, shadow);
		return x + minecraft.font.width(value);
	}

	@Override
	public int br$drawString(AxoText value, int x, int y, int color, boolean shadow) {
		text(minecraft.font, (Component) value, x, y, color, shadow);
		return x + minecraft.font.width((FormattedText) value);
	}

	@Unique
	private void drawWordWrap(Font renderer, FormattedText text, int x, int y, int width, boolean shadow, int color) {
		for (var orderedText : renderer.split(text, width)) {
			text(renderer, orderedText, x, y, color, shadow);
			y += renderer.lineHeight;
		}
	}

	@Unique
	private void drawCenteredWordWrap(Font renderer, FormattedText text, int x, int y, int width, boolean shadow, int color) {
		for (var orderedText : renderer.split(text, width)) {
			text(renderer, orderedText, x - renderer.width(orderedText) / 2, y, color, shadow);
			y += renderer.lineHeight;
		}
	}

	@Unique
	private void drawCenteredCenteredWordWrap(Font renderer, FormattedText text, int x, int y, int width, boolean shadow, int color) {
		var lines = renderer.split(text, width);
		y -= (lines.size() * renderer.lineHeight) / 2;
		for (var orderedText : lines) {
			text(renderer, orderedText, x - renderer.width(orderedText) / 2, y, color, shadow);
			y += renderer.lineHeight;
		}
	}

	@Override
	public void br$drawWordWrap(String text, int x, int y, int width, boolean shadow, int color) {
		drawWordWrap(minecraft.font, FormattedText.of(text), x, y, width, shadow, color);
	}

	@Override
	public void br$drawWordWrap(AxoText text, int x, int y, int width, boolean shadow, int color) {
		drawWordWrap(minecraft.font, (Component) text, x, y, width, shadow, color);
	}

	@Override
	public void br$drawCenteredWordWrap(String text, int x, int y, int width, boolean shadow, int color) {
		drawCenteredWordWrap(minecraft.font, FormattedText.of(text), x, y, width, shadow, color);
	}

	@Override
	public void br$drawCenteredWordWrap(AxoText text, int x, int y, int width, boolean shadow, int color) {
		drawCenteredWordWrap(minecraft.font, (Component) text, x, y, width, shadow, color);
	}

	@Override
	public void br$drawCenteredCenteredWordWrap(String text, int centerX, int centerY, int width, boolean shadow, int color) {
		drawCenteredCenteredWordWrap(minecraft.font, FormattedText.of(text), centerX, centerY, width, shadow, color);
	}

	@Override
	public void br$drawCenteredCenteredWordWrap(AxoText text, int centerX, int centerY, int width, boolean shadow, int color) {
		drawCenteredCenteredWordWrap(minecraft.font, (Component) text, centerX, centerY, width, shadow, color);
	}

	@Override
	public void br$fillRect(int x, int y, int width, int height, int color) {
		fill(x, y, x + width, y + height, color);
	}

	@Override
	public void br$fillRectGradientVert(int x, int y, int width, int height, int color1, int color2) {
		fillGradient(x, y, x + width, y + height, color1, color2);
	}

	@Override
	public void br$fillRectGradientHoriz(int x, int y, int width, int height, int color1, int color2) {
		HorizontalGradientRectangleRenderState.create(self(), x, y, x + width, y + height, color1, color2).submit();
	}

	@Override
	public void br$fillRectRoundGradient(int x, int y, int width, int height, int colorTopLeft, int colorBottomLeft, int colorBottomRight, int colorTopRight, float roundingPx) {
		self().axolotlclient_rendering$roundedRectGradient(x, y, x + width, y + height, colorTopLeft, colorBottomLeft, colorBottomRight, colorTopRight, roundingPx);
	}

	@Override
	public void br$fillSegment(int x0, int y0, int x1, int y1, int colorX0Y0, int colorX0Y1, int colorX1Y1, int colorX1Y0, float radius) {
		self().axolotlclient_rendering$segment(x0, y0, x1, y1, colorX0Y0, colorX0Y1, colorX1Y1, colorX1Y0, radius);
	}

	@Override
	public void br$outlineRect(int x, int y, int width, int height, int color) {
		DrawUtil.outlineRect(self(), x, y, width, height, color);
	}

	@Override
	public void br$fillRectRound(int x, int y, int width, int height, int color, float rounding) {
		self().axolotlclient_rendering$roundedRect(x, y, x + width, y + height, color, rounding);
	}

	@Override
	public void br$fillRectRoundVarying(int x, int y, int width, int height, int color, float roundingTL, float roundingBL, float roundingBR, float roundingTR) {
		self().axolotlclient_rendering$roundedRectVarying(x, y, x + width, y + height, color, roundingTL, roundingBL, roundingBR, roundingTR);
	}

	@Override
	public void br$outlineRectRound(int x, int y, int width, int height, int color, float rounding) {
		self().axolotlclient_rendering$outlineRoundedRect(x, y, x + width, y + height, color, rounding, 0.5f);
	}

	@Override
	public void br$outlineRectRoundVarying(int x, int y, int width, int height, int color, float roundingTL, float roundingBL, float roundingBR, float roundingTR, float outlineWidth) {
		self().axolotlclient_rendering$outlineRoundedRectVarying(x, y, x + width, y + height, color, roundingTL, roundingBL, roundingBR, roundingTR, outlineWidth);
	}

	@Override
	public void br$drawTexture(AxoSprite sprite, int x, int y, int width, int height, int color) {
		((AxoSpriteImpl) sprite).draw(minecraft, self(), x, y, width, height, color);
	}

	// item model rendering

	public void br$renderGuiItemModel(AxoItemStack stack, int x, int y) {
		item((ItemStack) stack, x, y);
	}

	public void br$renderGuiItemOverlay(AxoItemStack stack, int x, int y, String countLabel, int textColor,
										boolean shadow) {
		itemDecorations(minecraft.font, (ItemStack) stack, x, y, countLabel);
	}

	@ApiStatus.NonExtendable
	public void br$renderGuiItemOverlay(AxoItemStack stack, int x, int y, String countLabel) {
		br$renderGuiItemOverlay(stack, x, y, countLabel, 0xffffffff, true);
	}

	// misc methods
	public AxoFont br$getFont() {
		return minecraft.font;
	}

	@Override
	public int br$guiHeight() {
		return guiHeight();
	}

	@Override
	public int br$guiWidth() {
		return guiWidth();
	}
}
