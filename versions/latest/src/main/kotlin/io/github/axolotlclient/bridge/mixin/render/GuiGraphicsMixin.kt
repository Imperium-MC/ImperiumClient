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

package io.github.axolotlclient.bridge.mixin.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.render.AxoFont
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.blur.HudBlur
import io.github.axolotlclient.util.DrawUtil
import io.github.axolotlclient.util.HorizontalGradientRectangleRenderState
import io.github.axolotlclient.util.MathUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2fStack
import org.joml.Vector2f
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique

@Mixin(GuiGraphicsExtractor::class)
abstract class GuiGraphicsMixin : AxoRenderContext {
	@Shadow
	@field:Final
	private val minecraft: Minecraft? = null

	@Shadow
	@field:Final
	private val pose: Matrix3x2fStack? = null

	@Shadow
	abstract fun enableScissor(x1: Int, y1: Int, x2: Int, y2: Int)

	@Shadow
	abstract fun disableScissor()

	@Shadow
	abstract fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int)

	@Shadow
	abstract fun fillGradient(startX: Int, startY: Int, endX: Int, endY: Int, startColor: Int, endColor: Int)

	@Shadow
	abstract fun text(font: Font, str: Component, x: Int, y: Int, color: Int, dropShadow: Boolean)

	@Shadow
	abstract fun text(font: Font, str: String?, x: Int, y: Int, color: Int, dropShadow: Boolean)

	@Shadow
	abstract fun item(itemStack: ItemStack, x: Int, y: Int)

	@Shadow
	abstract fun itemDecorations(font: Font, itemStack: ItemStack, x: Int, y: Int, countText: String?)

	@Shadow
	abstract fun guiHeight(): Int

	@Shadow
	abstract fun guiWidth(): Int

	@Shadow
	abstract fun text(font: Font, str: FormattedCharSequence, x: Int, y: Int, color: Int, dropShadow: Boolean)

	@Shadow
	abstract fun centeredText(font: Font, str: String, x: Int, y: Int, color: Int)

	@Shadow
	abstract fun blit(view: com.mojang.renderpearl.api.textures.GpuTextureView, sampler: com.mojang.renderpearl.api.textures.GpuSampler, x: Int, y: Int, width: Int, height: Int, u1: Float, v1: Float, u2: Float, v2: Float)

	@Unique
	private fun self(): GuiGraphicsExtractor = this as GuiGraphicsExtractor

	override fun `br$popMatrix`() {
		pose!!.popMatrix()
	}

	override fun `br$pushMatrix`() {
		pose!!.pushMatrix()
	}

	override fun `br$scaleMatrix`(sx: Float, sy: Float) {
		pose!!.scale(sx, sy)
	}

	override fun `br$translateMatrix`(x: Float, y: Float) {
		pose!!.translate(x, y)
	}

	override fun `br$rotateMatrix`(ang: Float) {
		pose!!.rotate(ang)
	}

	override fun `br$rotateMatrixAround`(ang: Float, x: Float, y: Float) {
		pose!!.rotateAbout(ang, x, y)
	}

	// scissor
	override fun `br$pushScissor`(x: Int, y: Int, w: Int, h: Int) {
		enableScissor(x, y, x + w, y + h)
	}

	override fun `br$popScissor`() {
		disableScissor()
	}

	override fun `br$drawString`(value: String, x: Int, y: Int, color: Int, shadow: Boolean): Int {
		text(minecraft!!.font, value, x, y, color, shadow)
		return x + minecraft.font.width(value)
	}

	override fun `br$drawString`(value: AxoText, x: Int, y: Int, color: Int, shadow: Boolean): Int {
		text(minecraft!!.font, value as Component, x, y, color, shadow)
		return x + minecraft.font.width(value as FormattedText)
	}

	@Unique
	private fun drawWordWrap(renderer: Font, text: FormattedText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		var currentY = y
		for (orderedText in renderer.split(text, width)) {
			text(renderer, orderedText, x, currentY, color, shadow)
			currentY += renderer.lineHeight
		}
	}

	@Unique
	private fun drawCenteredWordWrap(renderer: Font, text: FormattedText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		var currentY = y
		for (orderedText in renderer.split(text, width)) {
			text(renderer, orderedText, x - renderer.width(orderedText) / 2, currentY, color, shadow)
			currentY += renderer.lineHeight
		}
	}

	@Unique
	private fun drawCenteredCenteredWordWrap(renderer: Font, text: FormattedText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		val lines = renderer.split(text, width)
		var currentY = y - (lines.size * renderer.lineHeight) / 2
		for (orderedText in lines) {
			text(renderer, orderedText, x - renderer.width(orderedText) / 2, currentY, color, shadow)
			currentY += renderer.lineHeight
		}
	}

	override fun `br$drawWordWrap`(text: String, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		drawWordWrap(minecraft!!.font, FormattedText.of(text), x, y, width, shadow, color)
	}

	override fun `br$drawWordWrap`(text: AxoText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		drawWordWrap(minecraft!!.font, text as Component, x, y, width, shadow, color)
	}

	override fun `br$drawCenteredWordWrap`(text: String, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		drawCenteredWordWrap(minecraft!!.font, FormattedText.of(text), x, y, width, shadow, color)
	}

	override fun `br$drawCenteredWordWrap`(text: AxoText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		drawCenteredWordWrap(minecraft!!.font, text as Component, x, y, width, shadow, color)
	}

	override fun `br$drawCenteredCenteredWordWrap`(text: String, centerX: Int, centerY: Int, width: Int, shadow: Boolean, color: Int) {
		drawCenteredCenteredWordWrap(minecraft!!.font, FormattedText.of(text), centerX, centerY, width, shadow, color)
	}

	override fun `br$drawCenteredCenteredWordWrap`(text: AxoText, centerX: Int, centerY: Int, width: Int, shadow: Boolean, color: Int) {
		drawCenteredCenteredWordWrap(minecraft!!.font, text as Component, centerX, centerY, width, shadow, color)
	}

	override fun `br$fillRect`(x: Int, y: Int, width: Int, height: Int, color: Int) {
		fill(x, y, x + width, y + height, color)
	}

	override fun `br$fillRectGradientVert`(x: Int, y: Int, width: Int, height: Int, color1: Int, color2: Int) {
		fillGradient(x, y, x + width, y + height, color1, color2)
	}

	override fun `br$hudBlurBackground`(x: Float, y: Float, width: Float, height: Float) {
		val view = HudBlur.getBlurredView()
			// Blur pass idle (module off or nothing wants it this frame): no blur, the caller's
			// flat background still draws.
			?: return
		// The blur frame covers the whole window in gui-scaled units, so the source rectangle is
		// the transformed on-screen position of this quad relative to the gui dimensions. The
		// quad itself is placed in the current matrix space (entry scale/offset included).
		val start = pose!!.transformPosition(x, y, Vector2f())
		val end = pose.transformPosition(x + width, y + height, Vector2f())
		val guiWidth = guiWidth().toFloat()
		val guiHeight = guiHeight().toFloat()
		val u1 = MathUtil.clamp(start.x / guiWidth, 0f, 1f)
		val v1 = MathUtil.clamp(start.y / guiHeight, 0f, 1f)
		val u2 = MathUtil.clamp(end.x / guiWidth, 0f, 1f)
		val v2 = MathUtil.clamp(end.y / guiHeight, 0f, 1f)
		if (u2 - u1 <= 0f || v2 - v1 <= 0f) {
			return
		}
		blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x.toInt(), y.toInt(), width.toInt(), height.toInt(), u1, v1, u2, v2)
	}

	override fun `br$fillRectGradientHoriz`(x: Int, y: Int, width: Int, height: Int, color1: Int, color2: Int) {
		HorizontalGradientRectangleRenderState.create(self(), x, y, x + width, y + height, color1, color2).submit()
	}

	override fun `br$fillRectRoundGradient`(x: Int, y: Int, width: Int, height: Int, colorTopLeft: Int, colorBottomLeft: Int, colorBottomRight: Int, colorTopRight: Int, roundingPx: Float) {
		self().`axolotlclient_rendering$roundedRectGradient`(x, y, x + width, y + height, colorTopLeft, colorBottomLeft, colorBottomRight, colorTopRight, roundingPx)
	}

	override fun `br$fillSegment`(x0: Int, y0: Int, x1: Int, y1: Int, colorX0Y0: Int, colorX0Y1: Int, colorX1Y1: Int, colorX1Y0: Int, radius: Float) {
		self().`axolotlclient_rendering$segment`(x0, y0, x1, y1, colorX0Y0, colorX0Y1, colorX1Y1, colorX1Y0, radius)
	}

	override fun `br$outlineRect`(x: Int, y: Int, width: Int, height: Int, color: Int) {
		DrawUtil.outlineRect(self(), x, y, width, height, color)
	}

	override fun `br$fillRectRound`(x: Int, y: Int, width: Int, height: Int, color: Int, rounding: Float) {
		self().`axolotlclient_rendering$roundedRect`(x, y, x + width, y + height, color, rounding)
	}

	override fun `br$fillRectRoundVarying`(x: Int, y: Int, width: Int, height: Int, color: Int, roundingTL: Float, roundingBL: Float, roundingBR: Float, roundingTR: Float) {
		self().`axolotlclient_rendering$roundedRectVarying`(x, y, x + width, y + height, color, roundingTL, roundingBL, roundingBR, roundingTR)
	}

	override fun `br$outlineRectRound`(x: Int, y: Int, width: Int, height: Int, color: Int, rounding: Float) {
		self().`axolotlclient_rendering$outlineRoundedRect`(x, y, x + width, y + height, color, rounding, 0.5f)
	}

	override fun `br$outlineRectRoundVarying`(x: Int, y: Int, width: Int, height: Int, color: Int, roundingTL: Float, roundingBL: Float, roundingBR: Float, roundingTR: Float, outlineWidth: Float) {
		self().`axolotlclient_rendering$outlineRoundedRectVarying`(x, y, x + width, y + height, color, roundingTL, roundingBL, roundingBR, roundingTR, outlineWidth)
	}

	override fun `br$drawTexture`(sprite: AxoSprite, x: Int, y: Int, width: Int, height: Int, color: Int) {
		(sprite as AxoSpriteImpl).draw(minecraft!!, self(), x, y, width, height, color)
	}

	// item model rendering

	override fun `br$renderGuiItemModel`(stack: AxoItemStack, x: Int, y: Int) {
		item(stack as ItemStack, x, y)
	}

	override fun `br$renderGuiItemOverlay`(stack: AxoItemStack, x: Int, y: Int, countLabel: String, textColor: Int, shadow: Boolean) {
		itemDecorations(minecraft!!.font, stack as ItemStack, x, y, countLabel)
	}

	override fun `br$renderGuiItemOverlay`(stack: AxoItemStack, x: Int, y: Int, countLabel: String) {
		`br$renderGuiItemOverlay`(stack, x, y, countLabel, 0xffffffff.toInt(), true)
	}

	// misc methods
	override fun `br$getFont`(): AxoFont = minecraft!!.font

	override fun `br$guiHeight`(): Int = guiHeight()

	override fun `br$guiWidth`(): Int = guiWidth()
}
