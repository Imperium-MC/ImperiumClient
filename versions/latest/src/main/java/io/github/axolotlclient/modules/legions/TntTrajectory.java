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

package io.github.axolotlclient.modules.legions;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure vanilla-accurate ballistic integrator for primed TNT (Alpine "factions/cannon_view +
 * cannon_playback" parity, reimplemented for Legions tooling).
 *
 * <p>Constants are the ones {@code PrimedTnt} actually runs with in 26.3 (javap of the mapped
 * jar, {@code minecraft-merged-deobf-26.3.jar}):</p>
 * <ul>
 *   <li>{@code PrimedTnt.getDefaultGravity()} returns {@code 0.04}; {@code Entity.applyGravity()}
 *       subtracts it from the y velocity <em>before</em> the move;</li>
 *   <li>{@code Entity.getAirDrag()} returns {@code 0.98F} (PrimedTnt does not override it);
 *       {@code PrimedTnt.tick()} scales the whole velocity vector by it <em>after</em> the move;</li>
 *   <li>on the ground the velocity is multiplied by {@code (0.7, -0.5, 0.7)} — the TNT skids and
 *       bounces. The prediction stops at the first solid contact instead, so the bounce factors
 *       are documented here for reference but intentionally unused.</li>
 * </ul>
 *
 * <p>Per-tick order (exactly {@code PrimedTnt.tick()} minus entity collision resolution):
 * gravity, move, air drag; the move is rejected when the destination sits inside a solid block
 * (queried through {@link SolidCheck}, an indirection so tests run without a world), and that
 * rejected destination becomes the reported landing site. A TNT's fuse burns 80 ticks by
 * default ({@code PrimedTnt.DEFAULT_FUSE_TIME} is 80), so the default horizon of 100 ticks
 * covers a full flight with margin.</p>
 *
 * <p>This class has no Minecraft imports on purpose: it is plain doubles in, plain doubles out,
 * and the cannon module supplies the eye position, look vector and world block queries.</p>
 */
public final class TntTrajectory {

	/** {@code PrimedTnt.getDefaultGravity()} (26.3): y velocity lost per tick, before the move. */
	public static final double GRAVITY_PER_TICK = 0.04;

	/** {@code Entity.getAirDrag()} (26.3): velocity multiplier applied after the move. */
	public static final double AIR_DRAG_PER_TICK = 0.98;

	/** Ground friction PrimedTnt applies on x/z while resting ({@code multiply(0.7, -0.5, 0.7)}). */
	public static final double GROUND_FRICTION_XZ = 0.7;

	/** Ground bounce PrimedTnt applies on y while resting ({@code multiply(0.7, -0.5, 0.7)}). */
	public static final double GROUND_BOUNCE_Y = -0.5;

	/** Vanilla TNT fuse; the default integration horizon should cover it. */
	public static final int DEFAULT_FUSE_TICKS = 80;

	/** Default cap on simulated ticks; stops a sky-aimed arc from integrating forever. */
	public static final int DEFAULT_MAX_TICKS = 100;

	/**
	 * Per-tick terminal displacement: the move velocity follows {@code v' = v*d - g} (gravity
	 * before the move, drag after it), whose fixed point is
	 * {@code -GRAVITY_PER_TICK / (1 - AIR_DRAG_PER_TICK)} = -2.0 blocks — the TNT falls at most
	 * two blocks per tick. (The commonly quoted -1.96 is the post-drag velocity,
	 * {@code v * AIR_DRAG_PER_TICK}.)
	 */
	public static final double TERMINAL_DISPLACEMENT_PER_TICK = -GRAVITY_PER_TICK / (1 - AIR_DRAG_PER_TICK);

	/**
	 * World indirection: is the point inside a solid block? The render path answers with the
	 * client level's block states; tests answer with lambdas.
	 */
	@FunctionalInterface
	public interface SolidCheck {

		boolean isSolid(double x, double y, double z);
	}

	/** One sampled point of the flight (position after {@code tick} ticks). */
	public record Point(double x, double y, double z, int tick) {
	}

	/**
	 * A finished integration: every sampled point (including the launch position at tick 0),
	 * whether a solid block stopped the flight, and the landing site — the last in-air point
	 * when {@code collided} (the rejected destination is one block-shape away, straight along
	 * the last velocity), or the last sampled point when the horizon ran out.
	 */
	public record Result(List<Point> points, boolean collided, Point landing, int ticks) {
	}

	private TntTrajectory() {
	}

	/**
	 * Integrates a TNT flight from {@code (x, y, z)} with initial velocity
	 * {@code (vx, vy, vz)} for at most {@code maxTicks} ticks, stopping at the first move that
	 * would place the TNT inside a solid block.
	 *
	 * @param maxTicks tick horizon, clamped to at least 1
	 * @param solid    block collision query, called with candidate destinations
	 * @return the sampled flight; never empty (it always carries the launch point)
	 */
	public static Result integrate(double x, double y, double z, double vx, double vy, double vz,
								   int maxTicks, SolidCheck solid) {
		int horizon = Math.max(1, maxTicks);
		List<Point> points = new ArrayList<>(horizon + 1);
		points.add(new Point(x, y, z, 0));
		for (int tick = 1; tick <= horizon; tick++) {
			// PrimedTnt.tick: applyGravity(), then move(SELF, deltaMovement).
			vy -= GRAVITY_PER_TICK;
			double nx = x + vx;
			double ny = y + vy;
			double nz = z + vz;
			if (solid.isSolid(nx, ny, nz)) {
				Point last = points.getLast();
				return new Result(points, true, last, tick - 1);
			}
			x = nx;
			y = ny;
			z = nz;
			// PrimedTnt.tick: setDeltaMovement(getDeltaMovement().scale(getAirDrag())).
			vx *= AIR_DRAG_PER_TICK;
			vy *= AIR_DRAG_PER_TICK;
			vz *= AIR_DRAG_PER_TICK;
			points.add(new Point(x, y, z, tick));
		}
		return new Result(points, false, points.getLast(), horizon);
	}

	/**
	 * Closed-form horizontal displacement after {@code ticks} unobstructed ticks: drag applies
	 * uniformly, so x/z decouple from gravity — displacement is
	 * {@code v * (1 - d^ticks) / (1 - d)} with {@code d = AIR_DRAG_PER_TICK}. Used by tests as
	 * an independent pin (and handy for sanity-checking power settings).
	 */
	public static double horizontalDistance(double velocity, int ticks) {
		return velocity * (1 - Math.pow(AIR_DRAG_PER_TICK, ticks)) / (1 - AIR_DRAG_PER_TICK);
	}
}
