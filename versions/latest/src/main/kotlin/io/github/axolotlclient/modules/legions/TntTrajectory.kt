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

package io.github.axolotlclient.modules.legions

import java.util.ArrayList
import kotlin.math.max
import kotlin.math.pow

/**
 * Pure vanilla-accurate ballistic integrator for primed TNT (Alpine "factions/cannon_view +
 * cannon_playback" parity, reimplemented for Legions tooling).
 *
 * Constants are the ones `PrimedTnt` actually runs with in 26.3 (javap of the mapped
 * jar, `minecraft-merged-deobf-26.3.jar`):
 * - `PrimedTnt.getDefaultGravity()` returns `0.04`; `Entity.applyGravity()`
 *   subtracts it from the y velocity *before* the move;
 * - `Entity.getAirDrag()` returns `0.98F` (PrimedTnt does not override it);
 *   `PrimedTnt.tick()` scales the whole velocity vector by it *after* the move;
 * - on the ground the velocity is multiplied by `(0.7, -0.5, 0.7)` — the TNT skids and
 *   bounces. The prediction stops at the first solid contact instead, so the bounce factors
 *   are documented here for reference but intentionally unused.
 *
 * Per-tick order (exactly `PrimedTnt.tick()` minus entity collision resolution):
 * gravity, move, air drag; the move is rejected when the destination sits inside a solid block
 * (queried through [SolidCheck], an indirection so tests run without a world), and that
 * rejected destination becomes the reported landing site. A TNT's fuse burns 80 ticks by
 * default (`PrimedTnt.DEFAULT_FUSE_TIME` is 80), so the default horizon of 100 ticks
 * covers a full flight with margin.
 *
 * This class has no Minecraft imports on purpose: it is plain doubles in, plain doubles out,
 * and the cannon module supplies the eye position, look vector and world block queries.
 */
object TntTrajectory {

    /** `PrimedTnt.getDefaultGravity()` (26.3): y velocity lost per tick, before the move. */
    const val GRAVITY_PER_TICK = 0.04

    /** `Entity.getAirDrag()` (26.3): velocity multiplier applied after the move. */
    const val AIR_DRAG_PER_TICK = 0.98

    /** Ground friction PrimedTnt applies on x/z while resting (`multiply(0.7, -0.5, 0.7)`). */
    const val GROUND_FRICTION_XZ = 0.7

    /** Ground bounce PrimedTnt applies on y while resting (`multiply(0.7, -0.5, 0.7)`). */
    const val GROUND_BOUNCE_Y = -0.5

    /** Vanilla TNT fuse; the default integration horizon should cover it. */
    const val DEFAULT_FUSE_TICKS = 80

    /** Default cap on simulated ticks; stops a sky-aimed arc from integrating forever. */
    const val DEFAULT_MAX_TICKS = 100

    /**
     * Per-tick terminal displacement: the move velocity follows `v' = v*d - g` (gravity
     * before the move, drag after it), whose fixed point is
     * `-GRAVITY_PER_TICK / (1 - AIR_DRAG_PER_TICK)` = -2.0 blocks — the TNT falls at most
     * two blocks per tick. (The commonly quoted -1.96 is the post-drag velocity,
     * `v * AIR_DRAG_PER_TICK`.)
     */
    const val TERMINAL_DISPLACEMENT_PER_TICK = -GRAVITY_PER_TICK / (1 - AIR_DRAG_PER_TICK)

    /**
     * World indirection: is the point inside a solid block? The render path answers with the
     * client level's block states; tests answer with lambdas.
     */
    fun interface SolidCheck {
        fun isSolid(x: Double, y: Double, z: Double): Boolean
    }

    /** One sampled point of the flight (position after  ticks). */
    @JvmRecord
    data class Point(val x: Double, val y: Double, val z: Double, val tick: Int)

    /**
     * A finished integration: every sampled point (including the launch position at tick 0),
     * whether a solid block stopped the flight, and the landing site — the last in-air point
     * when `collided` (the rejected destination is one block-shape away, straight along
     * the last velocity), or the last sampled point when the horizon ran out.
     */
    @JvmRecord
    data class Result(val points: List<Point>, val collided: Boolean, val landing: Point, val ticks: Int)

    /**
     * Integrates a TNT flight from `(x, y, z)` with initial velocity
     * `(vx, vy, vz)` for at most `maxTicks` ticks, stopping at the first move that
     * would place the TNT inside a solid block.
     *
     * @param maxTicks tick horizon, clamped to at least 1
     * @param solid    block collision query, called with candidate destinations
     * @return the sampled flight; never empty (it always carries the launch point)
     */
    @JvmStatic
    fun integrate(
        x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double,
        maxTicks: Int, solid: SolidCheck
    ): Result {
        val horizon = max(1, maxTicks)
        val points = ArrayList<Point>(horizon + 1)
        points.add(Point(x, y, z, 0))
        var px = x
        var py = y
        var pz = z
        var pvx = vx
        var pvy = vy
        var pvz = vz
        for (tick in 1..horizon) {
            // PrimedTnt.tick: applyGravity(), then move(SELF, deltaMovement).
            pvy -= GRAVITY_PER_TICK
            val nx = px + pvx
            val ny = py + pvy
            val nz = pz + pvz
            if (solid.isSolid(nx, ny, nz)) {
                val last = points.last()
                return Result(points, true, last, tick - 1)
            }
            px = nx
            py = ny
            pz = nz
            // PrimedTnt.tick: setDeltaMovement(getDeltaMovement().scale(getAirDrag())).
            pvx *= AIR_DRAG_PER_TICK
            pvy *= AIR_DRAG_PER_TICK
            pvz *= AIR_DRAG_PER_TICK
            points.add(Point(px, py, pz, tick))
        }
        return Result(points, false, points.last(), horizon)
    }

    /**
     * Closed-form horizontal displacement after `ticks` unobstructed ticks: drag applies
     * uniformly, so x/z decouple from gravity — displacement is
     * `v * (1 - d^ticks) / (1 - d)` with `d = AIR_DRAG_PER_TICK`. Used by tests as
     * an independent pin (and handy for sanity-checking power settings).
     */
    @JvmStatic
    fun horizontalDistance(velocity: Double, ticks: Int): Double =
        velocity * (1 - AIR_DRAG_PER_TICK.pow(ticks)) / (1 - AIR_DRAG_PER_TICK)
}
