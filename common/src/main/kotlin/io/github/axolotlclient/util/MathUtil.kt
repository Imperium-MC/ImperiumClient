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

import kotlin.math.pow

object MathUtil {
    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int = minOf(maxOf(value, min), max)

    @JvmStatic
    fun clamp(value: Long, max: Long, min: Long): Long = minOf(maxOf(value, max), min)

    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float = if (value < min) min else minOf(value, max)

    @JvmStatic
    fun clamp(value: Double, min: Double, max: Double): Double = if (value < min) min else minOf(value, max)

    @JvmStatic
    fun lerp(delta: Float, start: Float, end: Float): Float = start + delta * (end - start)

    @JvmStatic
    fun lerp(delta: Float, start: Int, end: Int): Int = start + floor(delta * (end - start))

    @JvmStatic
    fun floor(value: Float): Int {
        val i = value.toInt()
        return if (value < i) i - 1 else i
    }

    @JvmStatic
    fun distSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    @JvmStatic
    fun distSq(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    @JvmStatic
    fun easeInOutCubic(x: Double): Double =
        if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    @JvmStatic
    fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val i = (hue * 6.0f).toInt() % 6
        val f = hue * 6.0f - i.toFloat()
        val g = value * (1.0f - saturation)
        val h = value * (1.0f - f * saturation)
        val j = value * (1.0f - (1.0f - f) * saturation)
        val (k, l, m) = when (i) {
            0 -> Triple(value, j, g)
            1 -> Triple(h, value, g)
            2 -> Triple(g, value, j)
            3 -> Triple(g, h, value)
            4 -> Triple(j, g, value)
            5 -> Triple(value, g, h)
            else -> throw RuntimeException(
                "Something went wrong when converting from HSV to RGB. Input was $hue, $saturation, $value"
            )
        }

        val n = clamp((k * 255.0f).toInt(), 0, 255)
        val o = clamp((l * 255.0f).toInt(), 0, 255)
        val p = clamp((m * 255.0f).toInt(), 0, 255)
        return (n shl 16) or (o shl 8) or p
    }
}
