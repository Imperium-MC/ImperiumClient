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

package io.github.axolotlclient.util;

/**
 * Counters the M3 performance mixins bump when they actually suppress work, so the gametest
 * and the benchmark can prove a toggle did something instead of passing vacuously. Render
 * thread only.
 */
public final class PerformanceStats {

	private PerformanceStats() {
	}

	private static int signTextSkipped;
	private static int nameTagsSkipped;
	private static int nameTagDisplaysSeen;
	private static int tntSwellSuppressed;
	private static int spawnerEntitiesSkipped;
	private static int enchantBooksSkipped;

	public static void signTextSkipped() {
		signTextSkipped++;
	}

	public static int signTextSkippedCount() {
		return signTextSkipped;
	}

	public static void nameTagSkipped() {
		nameTagsSkipped++;
	}

	public static int nameTagsSkippedCount() {
		return nameTagsSkipped;
	}

	public static void nameTagDisplaySeen() {
		nameTagDisplaysSeen++;
	}

	public static int nameTagDisplaysSeenCount() {
		return nameTagDisplaysSeen;
	}

	public static void tntSwellSuppressed() {
		tntSwellSuppressed++;
	}

	public static int tntSwellSuppressedCount() {
		return tntSwellSuppressed;
	}

	public static void spawnerEntitySkipped() {
		spawnerEntitiesSkipped++;
	}

	public static int spawnerEntitiesSkippedCount() {
		return spawnerEntitiesSkipped;
	}

	public static void enchantBookSkipped() {
		enchantBooksSkipped++;
	}

	public static int enchantBooksSkippedCount() {
		return enchantBooksSkipped;
	}

	public static void reset() {
		signTextSkipped = 0;
		nameTagsSkipped = 0;
		nameTagDisplaysSeen = 0;
		tntSwellSuppressed = 0;
		spawnerEntitiesSkipped = 0;
		enchantBooksSkipped = 0;
	}
}
