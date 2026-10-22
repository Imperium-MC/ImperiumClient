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

/**
 * Counters the M3 performance mixins bump when they actually suppress work, so the gametest
 * and the benchmark can prove a toggle did something instead of passing vacuously. Render
 * thread only.
 */
object PerformanceStats {

    private var signTextSkipped = 0
    private var nameTagsSkipped = 0
    private var nameTagDisplaysSeen = 0
    private var tntSwellSuppressed = 0
    private var spawnerEntitiesSkipped = 0
    private var enchantBooksSkipped = 0

    @JvmStatic
    fun signTextSkipped() {
        signTextSkipped++
    }

    @JvmStatic
    fun signTextSkippedCount(): Int = signTextSkipped

    @JvmStatic
    fun nameTagSkipped() {
        nameTagsSkipped++
    }

    @JvmStatic
    fun nameTagsSkippedCount(): Int = nameTagsSkipped

    @JvmStatic
    fun nameTagDisplaySeen() {
        nameTagDisplaysSeen++
    }

    @JvmStatic
    fun nameTagDisplaysSeenCount(): Int = nameTagDisplaysSeen

    @JvmStatic
    fun tntSwellSuppressed() {
        tntSwellSuppressed++
    }

    @JvmStatic
    fun tntSwellSuppressedCount(): Int = tntSwellSuppressed

    @JvmStatic
    fun spawnerEntitySkipped() {
        spawnerEntitiesSkipped++
    }

    @JvmStatic
    fun spawnerEntitiesSkippedCount(): Int = spawnerEntitiesSkipped

    @JvmStatic
    fun enchantBookSkipped() {
        enchantBooksSkipped++
    }

    @JvmStatic
    fun enchantBooksSkippedCount(): Int = enchantBooksSkipped

    @JvmStatic
    fun reset() {
        signTextSkipped = 0
        nameTagsSkipped = 0
        nameTagDisplaysSeen = 0
        tntSwellSuppressed = 0
        spawnerEntitiesSkipped = 0
        enchantBooksSkipped = 0
    }
}
