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

package io.github.axolotlclient.util

import net.minecraft.util.ARGB

/**
 * Camera-overlay opacity math (Alpine client grab-bag, wave 3): the pumpkin, spyglass and
 * powder-snow frost overlays each get a percent option (0-100, 100 = vanilla); these
 * helpers turn that percent into the alpha math the `Hud` extraction wraps apply.
 *
 * Pure functions on purpose — the gametests pin the clamping and the channel arithmetic.
 */
object OverlayOpacity {

    /** Percent option value to a 0..1 alpha factor, clamped on both ends. */
    @JvmStatic
    fun factor(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    /** Vanilla overlay alpha scaled by a percent option (e.g. frost's freeze ramp). */
    @JvmStatic
    fun scaleAlpha(alpha: Float, percent: Int): Float = alpha * factor(percent)

    /** An ARGB color whose alpha channel is scaled by a percent option (spyglass fills). */
    @JvmStatic
    fun scaleArgb(argb: Int, percent: Int): Int {
        val alpha = (ARGB.alpha(argb) * factor(percent)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }
}
