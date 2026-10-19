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

package io.github.axolotlclient.modules.inventoryTweaks;

import org.jetbrains.annotations.VisibleForTesting;

/**
 * compact_item_counts (inventory_tweaks): shortens item stack counts in GUIs once they get wide
 * enough to overflow their slot. Counts below {@link #THRESHOLD} render exactly as vanilla
 * ("9999"); from the threshold up they switch to the k/m suffix style ("10k", "12.3k", "1.2m"),
 * with the decimal dropped when it would only trail a zero. Pure string math, no game state —
 * the unit tests exercise it directly.
 */
public final class CompactCounts {

	/** Counts at or above this value are compacted (four digits already crowd a 16px slot). */
	@VisibleForTesting
	public static final int THRESHOLD = 10_000;

	private CompactCounts() {
	}

	/**
	 * Formats a stack count: plain digits below the threshold, k/m suffix style above.
	 *
	 * @param count the stack count to format
	 * @return the display string for the count
	 */
	public static String format(int count) {
		if (count < THRESHOLD) {
			return Integer.toString(count);
		}
		if (count < 1_000_000) {
			return compact(count, 1_000, "k");
		}
		return compact(count, 1_000_000, "m");
	}

	/**
	 * One decimal place while the leading digits still fit ("12.3k"), plain units when the whole
	 * number is short enough to matter ("123k", "10k").
	 */
	private static String compact(int count, int unit, String suffix) {
		int whole = count / unit;
		int remainder = count % unit;
		if (remainder == 0 || whole >= 100) {
			return whole + suffix;
		}
		// One decimal, derived from the first digit of the remainder only (so 12_340 -> "12.3k").
		int tenth = remainder * 10 / unit;
		return tenth == 0 ? whole + suffix : whole + "." + tenth + suffix;
	}
}
