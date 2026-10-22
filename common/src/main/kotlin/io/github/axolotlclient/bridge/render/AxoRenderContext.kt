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

package io.github.axolotlclient.bridge.render

import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.util.Rectangle
import org.jetbrains.annotations.ApiStatus

@Suppress("unused")
interface AxoRenderContext {
	// Matrix management
	@RequiresImpl
	fun `br$popMatrix`() {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$pushMatrix`() {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$scaleMatrix`(sx: Float, sy: Float) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$translateMatrix`(x: Float, y: Float) {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Rotate the current matrix
	 *
	 * @param ang the angle, in radians.
	 * @see Math.toRadians
	 */
	@RequiresImpl
	fun `br$rotateMatrix`(ang: Float) {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Rotate the current matrix
	 *
	 * @param ang the angle, in radians.
	 * @param x   the x-coordinate of the rotation origin
	 * @param y   the y-coordinate of the rotation origin
	 * @see Math.toRadians
	 */
	fun `br$rotateMatrixAround`(ang: Float, x: Float, y: Float) {
		// naive default impl, may be overridden if a better impl is available in a version.
		`br$translateMatrix`(x, y)
		`br$rotateMatrix`(ang)
		`br$translateMatrix`(-x, -y)
	}

	// scissor
	@RequiresImpl
	fun `br$pushScissor`(x: Int, y: Int, w: Int, h: Int) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$popScissor`() {
		throw BridgeUtil.noImpl()
	}

	// string rendering
	@RequiresImpl
	fun `br$drawString`(value: String, x: Int, y: Int, color: Int, shadow: Boolean): Int {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: String, x: Int, y: Int, color: Int): Int = `br$drawString`(value, x, y, color, true)

	@RequiresImpl
	fun `br$drawString`(value: AxoText, x: Int, y: Int, color: Int, shadow: Boolean): Int {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: AxoText, x: Int, y: Int, color: Int): Int = `br$drawString`(value, x, y, color, true)

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: AxoText, x: Int, y: Int, color: Color): Int = `br$drawString`(value, x, y, color, true)

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: String, x: Int, y: Int, color: Color, shadow: Boolean): Int =
		`br$drawString`(value, x, y, color.toInt(), shadow)

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: String, x: Int, y: Int, color: Color): Int = `br$drawString`(value, x, y, color, true)

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: String, x: Int, y: Int, color: Int, shadow: Boolean) {
		`br$drawString`(value, x - `br$getFont`().`br$getWidth`(value) / 2, y, color, shadow)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: String, x: Int, y: Int, color: Int) {
		`br$drawCenteredString`(value, x, y, color, true)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: String, x: Int, y: Int, color: Color, shadow: Boolean) {
		`br$drawCenteredString`(value, x, y, color.toInt(), shadow)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: String, x: Int, y: Int, color: Color) {
		`br$drawCenteredString`(value, x, y, color, true)
	}

	@ApiStatus.NonExtendable
	fun `br$drawString`(value: AxoText, x: Int, y: Int, color: Color, shadow: Boolean): Int =
		`br$drawString`(value, x, y, color.toInt(), shadow)

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: AxoText, centerX: Int, y: Int, color: Int, shadow: Boolean) {
		`br$drawString`(value, centerX - `br$getFont`().`br$getWidth`(value) / 2, y, color, shadow)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: AxoText, centerX: Int, y: Int, color: Color, shadow: Boolean) {
		`br$drawCenteredString`(value, centerX, y, color.toInt(), shadow)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredString`(value: AxoText, centerX: Int, y: Int, color: Color) {
		`br$drawCenteredString`(value, centerX, y, color, true)
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: String, x: Int, y: Int, width: Int) {
		`br$drawWordWrap`(text, x, y, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: String, x: Int, y: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, x, y, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: String, x: Int, y: Int, width: Int, color: Int) {
		`br$drawWordWrap`(text, x, y, width, false, color)
	}

	@RequiresImpl
	fun `br$drawWordWrap`(text: String, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: String, centerX: Int, y: Int, width: Int) {
		`br$drawWordWrap`(text, centerX, y, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: String, centerX: Int, y: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, centerX, y, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: String, centerX: Int, y: Int, width: Int, color: Int) {
		`br$drawCenteredWordWrap`(text, centerX, y, width, false, color)
	}

	@RequiresImpl
	fun `br$drawCenteredWordWrap`(text: String, centerX: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: AxoText, x: Int, y: Int, width: Int) {
		`br$drawWordWrap`(text, x, y, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: AxoText, x: Int, y: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, x, y, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawWordWrap`(text: AxoText, x: Int, y: Int, width: Int, color: Int) {
		`br$drawWordWrap`(text, x, y, width, false, color)
	}

	@RequiresImpl
	fun `br$drawWordWrap`(text: AxoText, x: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: AxoText, centerX: Int, y: Int, width: Int) {
		`br$drawWordWrap`(text, centerX, y, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: AxoText, centerX: Int, y: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, centerX, y, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredWordWrap`(text: AxoText, centerX: Int, y: Int, width: Int, color: Int) {
		`br$drawWordWrap`(text, centerX, y, width, false, color)
	}

	@RequiresImpl
	fun `br$drawCenteredWordWrap`(text: AxoText, centerX: Int, y: Int, width: Int, shadow: Boolean, color: Int) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: String, centerX: Int, centerY: Int, width: Int) {
		`br$drawWordWrap`(text, centerX, centerY, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: String, centerX: Int, centerY: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, centerX, centerY, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: String, centerX: Int, centerY: Int, width: Int, color: Int) {
		`br$drawCenteredWordWrap`(text, centerX, centerY, width, false, color)
	}

	@RequiresImpl
	fun `br$drawCenteredCenteredWordWrap`(
		text: String,
		centerX: Int,
		centerY: Int,
		width: Int,
		shadow: Boolean,
		color: Int
	) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: AxoText, centerX: Int, centerY: Int, width: Int) {
		`br$drawWordWrap`(text, centerX, centerY, width, -1)
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: AxoText, centerX: Int, centerY: Int, width: Int, color: Color) {
		`br$drawWordWrap`(text, centerX, centerY, width, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$drawCenteredCenteredWordWrap`(text: AxoText, centerX: Int, centerY: Int, width: Int, color: Int) {
		`br$drawWordWrap`(text, centerX, centerY, width, false, color)
	}

	@RequiresImpl
	fun `br$drawCenteredCenteredWordWrap`(
		text: AxoText,
		centerX: Int,
		centerY: Int,
		width: Int,
		shadow: Boolean,
		color: Int
	) {
		throw BridgeUtil.noImpl()
	}

	// fillRect overloads
	@ApiStatus.NonExtendable
	fun `br$fillRect`(rect: Rectangle, color: Color) {
		`br$fillRect`(rect.x, rect.y, rect.width, rect.height, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$fillRect`(rect: Rectangle, color: Int) {
		`br$fillRect`(rect.x, rect.y, rect.width, rect.height, color)
	}

	@ApiStatus.NonExtendable
	fun `br$fillRect`(x: Int, y: Int, width: Int, height: Int, color: Color) {
		`br$fillRect`(x, y, width, height, color.toInt())
	}

	@RequiresImpl
	fun `br$fillRect`(x: Int, y: Int, width: Int, height: Int, color: Int) {
		throw BridgeUtil.noImpl()
	}

	fun `br$fillRectWithCutout`(
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		color: Int,
		cutoutX: Int,
		cutoutY: Int,
		cutoutWidth: Int,
		cutoutHeight: Int
	) {
		if (cutoutX > x) {
			`br$fillRect`(x, y, cutoutX - x, height, color)
		}
		if (cutoutY > y) {
			`br$fillRect`(cutoutX, y, cutoutWidth, cutoutY - y, color)
		}
		if (cutoutWidth < width) {
			`br$fillRect`(cutoutX + cutoutWidth, y, x + width - (cutoutX + cutoutWidth), height, color)
		}
		if (cutoutHeight < height) {
			`br$fillRect`(cutoutX, cutoutY + cutoutHeight, cutoutWidth, y + height - (cutoutY + cutoutHeight), color)
		}
	}

	@ApiStatus.NonExtendable
	fun `br$fillRectRound`(rect: Rectangle, color: Color, rounding: Float) {
		`br$fillRectRound`(rect.x, rect.y, rect.width, rect.height, color.toInt(), rounding)
	}

	@ApiStatus.NonExtendable
	fun `br$fillRectRound`(rect: Rectangle, color: Int, rounding: Float) {
		`br$fillRectRound`(rect.x, rect.y, rect.width, rect.height, color, rounding)
	}

	@ApiStatus.NonExtendable
	fun `br$fillRectRound`(x: Int, y: Int, width: Int, height: Int, color: Color, rounding: Float) {
		`br$fillRectRound`(x, y, width, height, color.toInt(), rounding)
	}

	@RequiresImpl
	fun `br$fillRectRound`(x: Int, y: Int, width: Int, height: Int, color: Int, rounding: Float) {
		throw BridgeUtil.noImpl()
	}

	fun `br$fillRectRoundVarying`(
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		color: Color,
		roundingTL: Float,
		roundingBL: Float,
		roundingBR: Float,
		roundingTR: Float
	) {
		`br$fillRectRoundVarying`(x, y, width, height, color.toInt(), roundingTL, roundingBL, roundingBR, roundingTR)
	}

	@RequiresImpl
	fun `br$fillRectRoundVarying`(
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		color: Int,
		roundingTL: Float,
		roundingBL: Float,
		roundingBR: Float,
		roundingTR: Float
	) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$fillRectGradientVert`(x: Int, y: Int, width: Int, height: Int, color1: Int, color2: Int) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$fillRectGradientHoriz`(x: Int, y: Int, width: Int, height: Int, color1: Int, color2: Int) {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Draws the blurred-game-world frame behind a HUD background: the region of the blurred frame
	 * matching the given (matrix-transformed) screen rectangle. A no-op when the version has no
	 * blur pass running (or the blur module is off), so callers can always invoke it and keep
	 * their flat background on top.
	 *
	 * @param x      rectangle x in the current matrix space
	 * @param y      rectangle y in the current matrix space
	 * @param width  rectangle width in the current matrix space
	 * @param height rectangle height in the current matrix space
	 */
	fun `br$hudBlurBackground`(x: Float, y: Float, width: Float, height: Float) {
	}

	@RequiresImpl
	fun `br$fillRectRoundGradient`(
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		colorTopLeft: Int,
		colorBottomLeft: Int,
		colorBottomRight: Int,
		colorTopRight: Int,
		roundingPx: Float
	) {
		throw BridgeUtil.noImpl()
	}

	fun `br$fillSegment`(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, radius: Float) {
		`br$fillSegment`(x0, y0, x1, y1, color, color, color, color, radius)
	}

	@RequiresImpl
	fun `br$fillSegment`(
		x0: Int,
		y0: Int,
		x1: Int,
		y1: Int,
		colorX0Y0: Int,
		colorX0Y1: Int,
		colorX1Y1: Int,
		colorX1Y0: Int,
		radius: Float
	) {
	}

	// outlineRect overloads
	@ApiStatus.NonExtendable
	fun `br$outlineRect`(rect: Rectangle, color: Color) {
		`br$outlineRect`(rect.x, rect.y, rect.width, rect.height, color.toInt())
	}

	@ApiStatus.NonExtendable
	fun `br$outlineRect`(x: Int, y: Int, width: Int, height: Int, color: Color) {
		`br$outlineRect`(x, y, width, height, color.toInt())
	}

	@RequiresImpl
	fun `br$outlineRect`(x: Int, y: Int, width: Int, height: Int, color: Int) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$outlineRectRound`(rect: Rectangle, color: Color, rounding: Float) {
		`br$outlineRectRound`(rect.x, rect.y, rect.width, rect.height, color.toInt(), rounding)
	}

	@ApiStatus.NonExtendable
	fun `br$outlineRectRound`(x: Int, y: Int, width: Int, height: Int, color: Color, rounding: Float) {
		`br$outlineRectRound`(x, y, width, height, color.toInt(), rounding)
	}

	@RequiresImpl
	fun `br$outlineRectRound`(x: Int, y: Int, width: Int, height: Int, color: Int, rounding: Float) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$outlineRectRoundVarying`(
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		color: Int,
		roundingTL: Float,
		roundingBL: Float,
		roundingBR: Float,
		roundingTR: Float,
		outlineWidth: Float
	) {
		throw BridgeUtil.noImpl()
	}

	// texture drawing
	@ApiStatus.NonExtendable
	fun `br$drawTexture`(texture: AxoSprite, coords: Rectangle) {
		`br$drawTexture`(texture, coords.x, coords.y, coords.width, coords.height)
	}

	@ApiStatus.NonExtendable
	fun `br$drawTexture`(sprite: AxoSprite, x: Int, y: Int, width: Int, height: Int) {
		`br$drawTexture`(sprite, x, y, width, height, -1)
	}

	@RequiresImpl
	fun `br$drawTexture`(sprite: AxoSprite, x: Int, y: Int, width: Int, height: Int, color: Int) {
		throw BridgeUtil.noImpl()
	}

	// item model rendering

	@RequiresImpl
	fun `br$renderGuiItemModel`(stack: AxoItemStack, x: Int, y: Int) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$renderGuiItemOverlay`(stack: AxoItemStack, x: Int, y: Int, countLabel: String, textColor: Int, shadow: Boolean) {
		throw BridgeUtil.noImpl()
	}

	@ApiStatus.NonExtendable
	fun `br$renderGuiItemOverlay`(stack: AxoItemStack, x: Int, y: Int, countLabel: String) {
		`br$renderGuiItemOverlay`(stack, x, y, countLabel, 0xffffffff.toInt(), true)
	}

	// misc methods
	@RequiresImpl
	fun `br$getFont`(): AxoFont {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$guiHeight`(): Int {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$guiWidth`(): Int {
		throw BridgeUtil.noImpl()
	}
}
