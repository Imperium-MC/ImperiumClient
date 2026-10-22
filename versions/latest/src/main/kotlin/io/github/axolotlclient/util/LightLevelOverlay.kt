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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.util.Mth
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

/**
 * light_level (Photon parity): a keybind-toggled spawn-safety overlay. Block tops around the
 * player are tinted by their effective light level — green where hostile mobs cannot spawn
 * (light > 7 after the sky-darkening scale), red where they can.
 *
 * **Sampling budget.** Column scanning is the expensive part (sturdy-top + two-air +
 * light-layer reads per position), so it never runs unbounded on the render path: the client
 * tick advances a work queue of at most [COLUMNS_PER_TICK] columns per tick, rebuilt
 * whenever the player anchor moves more than [ANCHOR_SLACK] blocks or every
 * [REBUILD_INTERVAL_TICKS] ticks (world/light changes are picked up by that periodic
 * rebuild). Results are cached per column and rendering only walks the cache. Unloaded chunks
 * are skipped (`hasChunk`) — both the scan and the render stay capped to loaded chunks.
 *
 * **Render path.** Each surface block gets a Photon-style colour cross (two
 * [Gizmos.line] segments) plus a thin translucent cuboid slab tint, submitted through the
 * frame's gizmo collector window that [init] opens at Fabric's
 * `LevelRenderEvents.BEFORE_GIZMOS` — the same submission pattern the WorldEdit CUI
 * selection uses, drained by `LevelRenderer.finalizeGizmoCollection` a moment later.
 */
object LightLevelOverlay {

    /**
     * A hostile mob can spawn at light `<= 7` — light `> 7` is spawn-safe. The
     * threshold applies to the scaled/effective light, not the raw sky value.
     */
    const val SAFE_LIGHT_THRESHOLD: Int = 7

    /** Columns sampled per client tick (the sampling budget). */
    private const val COLUMNS_PER_TICK = 128

    /** Full rebuild cadence in ticks; also how fast light changes get picked up. */
    private const val REBUILD_INTERVAL_TICKS = 20

    /** Anchor slack in blocks: rebuild when the player moved further than this from the anchor. */
    private const val ANCHOR_SLACK = 2

    /** Vertical scan window above/below the player's feet. */
    private const val VERTICAL_UP = 4
    private const val VERTICAL_DOWN = 8

    /** Height of the tint slab on top of a surface block, in blocks. */
    private const val SLAB_HEIGHT = 0.03

    /** Cross arm length and elevation of the marker lines above the block top, in blocks. */
    private const val CROSS_ARM = 0.35
    private const val LINE_LIFT = 0.01
    private const val LINE_WIDTH = 2.0f

    /**
     * A sampled column: the y of the topmost spawnable surface in the window, and whether that
     * spot is spawn-safe. `null` sample = no spawnable surface (or unloaded chunk).
     */
    @JvmRecord
    data class Sample(val y: Int, val safe: Boolean)

    private val SAMPLES = HashMap<Long, Sample>()
    private val PENDING = ArrayDeque<Long>()
    private var anchorX = 0
    private var anchorY = 0
    private var anchorZ = 0
    private var dimension = ""
    private var lastRebuildGameTime = Long.MIN_VALUE
    private var registered = false

    /**
     * Diagnostics: how many markers the last [emit] drew (render-path introspection
     * for the game test — distinguishes "nothing sampled" from "sampled but not rendered").
     */
    @Volatile
    private var lastEmittedMarkers = 0

    /** Registers the client-tick sampler and the render hook. Called once from the client entry point. */
    @JvmStatic
    @Synchronized
    fun init() {
        if (!registered) {
            registered = true
            ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client -> tick(client) })
            // The sanctioned submission point (same pattern as the WorldEdit CUI selection):
            // Fabric fires BEFORE_GIZMOS right before LevelRenderer.finalizeGizmoCollection
            // drains the render-thread collector, and nothing calls
            // collectPerFrameRenderThreadGizmos() in a normal frame unless a mod opens that
            // window itself — so the overlay opens it, submits, and lets the frame drain it.
            LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { context ->
                if (!AxolotlClient.config().lightOverlay.get()) {
                    return@BeforeGizmos
                }
                context.levelRenderer().collectPerFrameRenderThreadGizmos().use { emit() }
            })
        }
    }

    /**
     * Effective light level "after scale" (Photon semantics): the sky layer darkened by the
     * current sky darkening (time of day, rain), the block layer untouched. Pure function.
     */
    @JvmStatic
    fun effectiveLight(blockLight: Int, skyLight: Int, skyDarken: Int): Int {
        return max(blockLight, skyLight - skyDarken)
    }

    /**
     * Whether a position with these raw light values is spawn-safe (green) or mobs can spawn
     * there (red). Pure function.
     */
    @JvmStatic
    fun isSpawnSafe(blockLight: Int, skyLight: Int, skyDarken: Int): Boolean {
        return effectiveLight(blockLight, skyLight, skyDarken) > SAFE_LIGHT_THRESHOLD
    }

    private fun tick(minecraft: Minecraft) {
        val level = minecraft.level
        if (!AxolotlClient.config().lightOverlay.get() || level == null || minecraft.player == null) {
            if (SAMPLES.isNotEmpty() || PENDING.isNotEmpty()) {
                SAMPLES.clear()
                PENDING.clear()
                lastRebuildGameTime = Long.MIN_VALUE
            }
            return
        }

        val blockX = Mth.floor(minecraft.player!!.x)
        val blockY = Mth.floor(minecraft.player!!.y)
        val blockZ = Mth.floor(minecraft.player!!.z)
        val currentDimension = level.dimension().identifier().toString()
        val gameTime = level.gameTime
        val dimensionChanged = currentDimension != dimension
        val moved = abs(blockX - anchorX) > ANCHOR_SLACK ||
            abs(blockY - anchorY) > ANCHOR_SLACK ||
            abs(blockZ - anchorZ) > ANCHOR_SLACK
        val stale = gameTime - lastRebuildGameTime >= REBUILD_INTERVAL_TICKS
        if (dimensionChanged) {
            // Samples are world state — none of them mean anything in the new dimension.
            SAMPLES.clear()
        }
        if (dimensionChanged || moved || stale) {
            anchorX = blockX
            anchorY = blockY
            anchorZ = blockZ
            dimension = currentDimension
            lastRebuildGameTime = gameTime
            rebuildQueue(AxolotlClient.config().lightOverlayRadius.get())
        }

        var i = 0
        while (i < COLUMNS_PER_TICK && PENDING.isNotEmpty()) {
            val column = PENDING.removeFirst()
            val x = unpackX(column)
            val z = unpackZ(column)
            if (!level.hasChunk(x shr 4, z shr 4)) {
                i++
                continue
            }
            val sample = sampleColumn(level, x, z)
            if (sample != null) {
                SAMPLES[column] = sample
            } else {
                SAMPLES.remove(column)
            }
            i++
        }
    }

    /**
     * Refills the pending queue with every column of the radius around the anchor, closest
     * first (so the area around the player resolves first after a rebuild), and drops cached
     * samples that fell out of the radius.
     */
    private fun rebuildQueue(radius: Int) {
        PENDING.clear()
        val columns = ArrayList<Long>((2 * radius + 1) * (2 * radius + 1))
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                columns.add(pack(anchorX + dx, anchorZ + dz))
            }
        }
        columns.sortBy { distanceToAnchorSq(it) }
        PENDING.addAll(columns)

        val pruneRadius = radius + 4
        SAMPLES.keys.removeAll { column: Long ->
            abs(unpackX(column) - anchorX) > pruneRadius || abs(unpackZ(column) - anchorZ) > pruneRadius
        }
    }

    private fun distanceToAnchorSq(column: Long): Long {
        val dx = (unpackX(column) - anchorX).toLong()
        val dz = (unpackZ(column) - anchorZ).toLong()
        return dx * dx + dz * dz
    }

    /**
     * Finds the topmost spawnable surface of a column within the vertical window: a block with a
     * sturdy top and no fluid, with two collision-free positions above it. Reads the light layers
     * at the position a mob would occupy. Returns `null` if the window holds no such spot.
     */
    private fun sampleColumn(level: net.minecraft.client.multiplayer.ClientLevel, x: Int, z: Int): Sample? {
        val top = anchorY + VERTICAL_UP
        val bottom = anchorY - VERTICAL_DOWN
        for (y in top downTo bottom) {
            val pos = net.minecraft.core.BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (!state.isFaceSturdy(level, pos, Direction.UP) || !level.getFluidState(pos).isEmpty) {
                continue
            }
            val head = pos.above()
            val headState = level.getBlockState(head)
            val headAboveState = level.getBlockState(head.above())
            if (!headState.getCollisionShape(level, head).isEmpty ||
                !level.getFluidState(head).isEmpty ||
                !headAboveState.getCollisionShape(level, head.above()).isEmpty
            ) {
                continue
            }
            val safe = isSpawnSafe(
                level.getBrightness(LightLayer.BLOCK, head),
                level.getBrightness(LightLayer.SKY, head),
                level.skyDarken
            )
            return Sample(y, safe)
        }
        return null
    }

    /**
     * Draws the cached samples on the surface blocks: a Photon-style colour cross on each block
     * top (two short [Gizmos.line] segments) plus a translucent tint slab over the block
     * for the area effect. Must only be called while a gizmo collector window is active —
     * [init] opens one per frame at BEFORE_GIZMOS; outside a window the submissions
     * are silently refused.
     */
    @JvmStatic
    fun emit() {
        if (!AxolotlClient.config().lightOverlay.get() || SAMPLES.isEmpty()) {
            lastEmittedMarkers = 0
            return
        }
        val safeColor = AxolotlClient.config().lightOverlaySafeColor.get().toInt()
        val unsafeColor = AxolotlClient.config().lightOverlayUnsafeColor.get().toInt()
        var emitted = 0
        for ((key, sample) in SAMPLES) {
            val x = unpackX(key)
            val z = unpackZ(key)
            val color = if (sample.safe) safeColor else unsafeColor

            // Colour cross on the block top, inset like the classic light-level overlays.
            val cx = x + 0.5
            val cz = z + 0.5
            val top = sample.y + 1.0 + LINE_LIFT
            line(Vec3(cx - CROSS_ARM, top, cz), Vec3(cx + CROSS_ARM, top, cz), color)
            line(Vec3(cx, top, cz - CROSS_ARM), Vec3(cx, top, cz + CROSS_ARM), color)

            // Translucent tint slab across the whole block top.
            val slab = AABB(
                x.toDouble(), sample.y + 1.0, z.toDouble(),
                x + 1.0, sample.y + 1.0 + SLAB_HEIGHT, z + 1.0
            )
            Gizmos.cuboid(slab, GizmoStyle.fill(color))?.setAlwaysOnTop()
            emitted++
        }
        lastEmittedMarkers = emitted
    }

    private fun line(from: Vec3, to: Vec3, color: Int) {
        Gizmos.line(from, to, color, LINE_WIDTH)?.setAlwaysOnTop()
    }

    /**
     * Packs a column into a key: 26 bits per coordinate is ample for the +-30M block range and
     * keeps the sign.
     */
    private fun pack(x: Int, z: Int): Long {
        return ((x.toLong() and 0x3FFFFFFL) shl 26) or (z.toLong() and 0x3FFFFFFL)
    }

    private fun unpackX(column: Long): Int {
        return (column shl 12 shr 38).toInt()
    }

    private fun unpackZ(column: Long): Int {
        return (column shl 38 shr 38).toInt()
    }

    /**
     * Render-state introspection (used by the game test): how many columns currently carry a
     * sample.
     */
    @JvmStatic
    fun cachedSampleCount(): Int {
        return SAMPLES.size
    }

    /**
     * Render-state introspection (used by the game test): the cached sample of one column, or
     * `null` when that column has no spawnable surface in the window or is not loaded.
     */
    @JvmStatic
    fun cachedSample(x: Int, z: Int): Sample? {
        return SAMPLES[pack(x, z)]
    }

    /**
     * Render-path introspection (used by the game test): how many markers the most recent
     * [emit] call drew. Non-zero while the screen shows nothing means the failure is
     * downstream of the gizmo submission, not in the sampler.
     */
    @JvmStatic
    fun lastEmittedMarkers(): Int {
        return lastEmittedMarkers
    }
}
