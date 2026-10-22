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

import io.github.axolotlclient.AxolotlClient
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * chunk_borders (Alpine parity): world-space boundary lines for the player's current chunk
 * (16x16 column) and, optionally, the containing region (512x512 grid).
 *
 * 26.3 has a gizmo layer exactly for this ([Gizmos.line] — the same path the
 * vanilla F3+G chunk border debug renderer uses). The lines are emitted into a gizmo
 * collection window opened from Fabric's `LevelRenderEvents.BEFORE_GIZMOS` every
 * frame the option is on, and are drawn on top of the world.
 */
object ChunkBorders {

    /**
     * Blocks per region (32 chunks), used for the optional region border grid.
     */
    const val REGION_SIZE: Int = 512

    private const val CHUNK_LINE_WIDTH = 2.0f
    private const val REGION_LINE_WIDTH = 3.0f

    /**
     * Opens the module's own per-frame gizmo collection window. `BEFORE_GIZMOS` fires
     * inside `LevelRenderer#submitFeatures` on every frame; without our own window the
     * lines could only ride a debug gizmo mode (F3+G) or another module's window, neither
     * of which is guaranteed in a normal frame.
     */
    @JvmStatic
    fun register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { context ->
            val minecraft = Minecraft.getInstance()
            if (!AxolotlClient.config().chunkBorders.get() || minecraft.level == null || minecraft.player == null) {
                return@BeforeGizmos
            }
            context.levelRenderer().collectPerFrameRenderThreadGizmos().use { emit() }
        })
    }

    /**
     * Chunk base coordinate of a world block coordinate (works for negatives; the two's
     * complement mask is equivalent to floorDiv/floorMul by 16).
     */
    @JvmStatic
    fun chunkBase(blockCoord: Int): Int {
        return blockCoord and 0xF.inv()
    }

    /**
     * Region base coordinate of a world block coordinate (multiples of 512).
     */
    @JvmStatic
    fun regionBase(blockCoord: Int): Int {
        return blockCoord and (REGION_SIZE - 1).inv()
    }

    @JvmStatic
    fun emit() {
        val minecraft = Minecraft.getInstance()
        if (!AxolotlClient.config().chunkBorders.get() || minecraft.level == null || minecraft.player == null) {
            return
        }
        val level = minecraft.level!!
        val blockX = Mth.floor(minecraft.player!!.x)
        val blockZ = Mth.floor(minecraft.player!!.z)
        val minY = level.minY
        val maxY = level.maxY + 1

        emitCell(
            chunkBase(blockX), chunkBase(blockZ), 16,
            AxolotlClient.config().chunkBorderColor.get().toInt(), CHUNK_LINE_WIDTH, minY, maxY
        )

        if (AxolotlClient.config().regionBorders.get()) {
            emitCell(
                regionBase(blockX), regionBase(blockZ), REGION_SIZE,
                AxolotlClient.config().regionBorderColor.get().toInt(), REGION_LINE_WIDTH, minY, maxY
            )
        }
    }

    /**
     * Draws the vertical pillars and the top/bottom perimeter of a cell whose north-west
     * corner is (`baseX`, `baseZ`) and whose edge length is `size`.
     */
    private fun emitCell(baseX: Int, baseZ: Int, size: Int, color: Int, width: Float, minY: Int, maxY: Int) {
        val x1 = baseX
        val z1 = baseZ
        val x2 = baseX + size
        val z2 = baseZ + size

        line(x1, minY, z1, x1, maxY, z1, color, width)
        line(x2, minY, z1, x2, maxY, z1, color, width)
        line(x2, minY, z2, x2, maxY, z2, color, width)
        line(x1, minY, z2, x1, maxY, z2, color, width)

        for (y in intArrayOf(minY, maxY)) {
            line(x1, y, z1, x2, y, z1, color, width)
            line(x2, y, z1, x2, y, z2, color, width)
            line(x2, y, z2, x1, y, z2, color, width)
            line(x1, y, z2, x1, y, z1, color, width)
        }
    }

    private fun line(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, color: Int, width: Float) {
        Gizmos.line(Vec3(x1.toDouble(), y1.toDouble(), z1.toDouble()), Vec3(x2.toDouble(), y2.toDouble(), z2.toDouble()), color, width)
            ?.setAlwaysOnTop()
    }
}
