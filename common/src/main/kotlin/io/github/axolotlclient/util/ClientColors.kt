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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors

object ClientColors {
    @JvmField
    var WHITE: Color = Colors.WHITE

    @JvmField
    var BLACK: Color = Colors.BLACK

    @JvmField
    var GRAY: Color = Colors.GRAY

    @JvmField
    var DARK_GRAY: Color = Colors.DARK_GRAY

    @JvmField
    var SELECTOR_RED: Color = Color(191, 34, 34).immutable()

    @JvmField
    var GOLD: Color = Color.parse("#b8860b").immutable()

    @JvmField
    var SELECTOR_GREEN: Color = Color(53, 219, 103).immutable()

    @JvmField
    var SELECTOR_BLUE: Color = Color(51, 153, 255, 255).immutable()

    @JvmField
    var ERROR: Color = Color(255, 0, 255).immutable()

    /**
     * Blends two [Color]s based off of a percentage.
     *
     * @param original   color to start the blend with
     * @param blend      color that when fully blended, will be this
     * @param percentage the percentage to blend
     * @return the simple color
     */
    @JvmStatic
    fun blend(original: Color, blend: Color, percentage: Float): Color {
        if (percentage >= 1) {
            return blend
        }
        if (percentage <= 0) {
            return original
        }
        val red = blendInt(original.red, blend.red, percentage)
        val green = blendInt(original.green, blend.green, percentage)
        val blue = blendInt(original.blue, blend.blue, percentage)
        val alpha = blendInt(original.alpha, blend.alpha, percentage)
        return Color(red, green, blue, alpha)
    }

    /**
     * Blends two ints together based off of a percent.
     *
     * @param start   starting int
     * @param end     end int
     * @param percent percent to blend
     * @return the blended int
     */
    @JvmStatic
    fun blendInt(start: Int, end: Int, percent: Float): Int {
        if (percent <= 0) {
            return start
        }
        if (start == end || percent >= 1) {
            return end
        }
        val dif = end - start
        val add = Math.round(dif * percent)
        return start + add
    }

    object ARGB {
        @JvmStatic
        fun alpha(color: Int): Int = color ushr 24

        @JvmStatic
        fun red(color: Int): Int = (color shr 16) and 0xFF

        @JvmStatic
        fun green(color: Int): Int = (color shr 8) and 0xFF

        @JvmStatic
        fun blue(color: Int): Int = color and 0xFF

        @JvmStatic
        fun color(alpha: Int, red: Int, green: Int, blue: Int): Int =
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        @JvmStatic
        fun color(red: Int, green: Int, blue: Int): Int = color(255, red, green, blue)

        @JvmStatic
        fun multiply(color1: Int, color2: Int): Int = when {
            color1 == -1 -> color2
            color2 == -1 -> color1
            else -> color(
                alpha(color1) * alpha(color2) / 255,
                red(color1) * red(color2) / 255,
                green(color1) * green(color2) / 255,
                blue(color1) * blue(color2) / 255
            )
        }

        @JvmStatic
        fun scaleRGB(color: Int, scale: Float): Int = scaleRGB(color, scale, scale, scale)

        @JvmStatic
        fun scaleRGB(color: Int, redScale: Float, greenScale: Float, blueScale: Float): Int = color(
            alpha(color),
            MathUtil.clamp((red(color) * redScale).toInt(), 0, 255),
            MathUtil.clamp((green(color) * greenScale).toInt(), 0, 255),
            MathUtil.clamp((blue(color) * blueScale).toInt(), 0, 255)
        )

        @JvmStatic
        fun scaleRGB(color: Int, scale: Int): Int = color(
            alpha(color),
            MathUtil.clamp(red(color) * scale / 255, 0, 255),
            MathUtil.clamp(green(color) * scale / 255, 0, 255),
            MathUtil.clamp(blue(color) * scale / 255, 0, 255)
        )

        @JvmStatic
        fun greyscale(color: Int): Int {
            val i = (red(color) * 0.3f + green(color) * 0.59f + blue(color) * 0.11f).toInt()
            return color(i, i, i)
        }

        @JvmStatic
        fun lerp(delta: Float, color1: Int, color2: Int): Int {
            val i = MathUtil.lerp(delta, alpha(color1), alpha(color2))
            val j = MathUtil.lerp(delta, red(color1), red(color2))
            val k = MathUtil.lerp(delta, green(color1), green(color2))
            val l = MathUtil.lerp(delta, blue(color1), blue(color2))
            return color(i, j, k, l)
        }

        @JvmStatic
        fun opaque(color: Int): Int = color or 0xFF000000.toInt()

        @JvmStatic
        fun transparent(color: Int): Int = color and 16777215

        @JvmStatic
        fun color(alpha: Int, color: Int): Int = (alpha shl 24) or (color and 16777215)

        @JvmStatic
        fun color(alpha: Float, color: Int): Int = (as8BitChannel(alpha) shl 24) or (color and 16777215)

        @JvmStatic
        fun white(alpha: Float): Int = (as8BitChannel(alpha) shl 24) or 16777215

        @JvmStatic
        fun colorFromFloat(alpha: Float, red: Float, green: Float, blue: Float): Int =
            color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue))

        @JvmStatic
        fun average(color1: Int, color2: Int): Int = color(
            (alpha(color1) + alpha(color2)) / 2,
            (red(color1) + red(color2)) / 2,
            (green(color1) + green(color2)) / 2,
            (blue(color1) + blue(color2)) / 2
        )

        @JvmStatic
        fun as8BitChannel(value: Float): Int = MathUtil.floor(value * 255.0f)

        @JvmStatic
        fun alphaFloat(color: Int): Float = from8BitChannel(alpha(color))

        @JvmStatic
        fun redFloat(color: Int): Float = from8BitChannel(red(color))

        @JvmStatic
        fun greenFloat(color: Int): Float = from8BitChannel(green(color))

        @JvmStatic
        fun blueFloat(color: Int): Float = from8BitChannel(blue(color))

        private fun from8BitChannel(value: Int): Float = value / 255.0f

        @JvmStatic
        fun invertAlpha(color: Int): Int = color(255 - alpha(color), color)

        @JvmStatic
        fun toABGR(color: Int): Int =
            (color and -16711936) or ((color and 0xFF0000) shr 16) or ((color and 0xFF) shl 16)

        @JvmStatic
        fun fromABGR(color: Int): Int = toABGR(color)
    }
}
