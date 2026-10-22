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

package io.github.axolotlclient.modules.inventoryTweaks

/**
 * compact_item_counts (inventory_tweaks): shortens item stack counts in GUIs once they get wide
 * enough to overflow their slot. Counts below [THRESHOLD] render exactly as vanilla
 * ("9999"); from the threshold up they switch to the k/m suffix style ("10k", "12.3k", "1.2m"),
 * with the decimal dropped when it would only trail a zero. Pure string math, no game state —
 * the unit tests exercise it directly.
 */
object CompactCounts {
    /** Counts at or above this value are compacted (four digits already crowd a 16px slot). */
    const val THRESHOLD: Int = 10_000

    /**
     * Formats a stack count: plain digits below the threshold, k/m suffix style above.
     */
    @JvmStatic
    fun format(count: Int): String {
        if (count < THRESHOLD) {
            return count.toString()
        }
        return if (count < 1_000_000) {
            compact(count, 1_000, "k")
        } else {
            compact(count, 1_000_000, "m")
        }
    }

    /**
     * One decimal place while the leading digits still fit ("12.3k"), plain units when the whole
     * number is short enough to matter ("123k", "10k").
     */
    private fun compact(count: Int, unit: Int, suffix: String): String {
        val whole = count / unit
        val remainder = count % unit
        if (remainder == 0 || whole >= 100) {
            return whole.toString() + suffix
        }
        // One decimal, derived from the first digit of the remainder only (so 12_340 -> "12.3k").
        val tenth = remainder * 10 / unit
        return if (tenth == 0) whole.toString() + suffix else "$whole.$tenth$suffix"
    }
}
