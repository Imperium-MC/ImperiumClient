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

package io.github.axolotlclient.imperium.enforcement;

/**
 * Pure movement-envelope math for the local rules enforcement layer (docs/wave4-roadmap.md W4-C).
 * No Minecraft imports — everything arrives as a {@link State} snapshot and doubles, so the whole
 * model is unit-testable and thread-safe by construction.
 *
 * <h2>Model</h2>
 *
 * The horizontal ceiling mirrors the ImperiumMC plugin's own speed-punishment edge — the flat
 * ceiling of {@code CheatPreventionListener.onMove}:
 *
 * <pre>{@code
 * 0.42 * speedPotion * sprint(1.3) * ice(1.4) * sprintJumpBurst(1.7, first 2 airborne ticks)
 * }</pre>
 *
 * scaled by a {@link #SAFETY} factor (0.98) so the client always clamps strictly <em>before</em>
 * the server would flag the same delta. Every vanilla-achievable speed sits under the resulting
 * ceiling, which the unit tests pin against the vanilla reference tiers:
 *
 * <ul>
 *   <li>walk 4.317 b/s = 0.216/tick ({@link #WALK}) vs grounded walk ceiling 0.41</li>
 *   <li>sprint 5.612 b/s = 0.281/tick ({@link #SPRINT}) vs grounded sprint ceiling 0.52</li>
 *   <li>sprint-jump bursts ~0.56/tick ({@link #SPRINT_JUMP}) vs the burst-window ceiling 0.88;
 *       decaying mid-arc/landing momentum (~0.45-0.55) fits under the sustained ceiling 0.52 —
 *       the same band the server itself tolerates before its violation buffer trips</li>
 * </ul>
 *
 * <p>Vertical terms mirror {@code MovementSimulationCheck}'s FLY sub-check: the per-tick upward
 * arc {@code MAX_JUMP_BASE = 0.55 + 0.1 * jumpBoostLevel} with a 0.65 floor for the vanilla 0.6
 * step height, and the 3.92 terminal fall speed.</p>
 *
 * <p>States the plugin exempts entirely (elytra/glider, riptide spin-attack, vehicles, creative
 * flight, levitation) get one generous ceiling that only catches protocol-breaking speeds, so
 * legit firework chains and blue-ice boats are never touched.</p>
 *
 * <p>Clamping never drops a packet: the horizontal vector is scaled to the ceiling (direction
 * preserved) and dy is clamped to {@code [-downwardCeiling, upwardCeiling]}. A player whose
 * delta exceeds the envelope keeps moving, just at the maximum legal speed — a third-party cheat
 * mod cannot make them move illegally, and a legit player never desyncs.</p>
 */
public final class MovementEnvelope {

	// ── Vanilla reference tiers (blocks/tick) — what legit movement actually does ──
	/** Vanilla walking is 4.317 b/s = 0.216/tick. */
	public static final double WALK = 0.22;
	/** Vanilla sprinting is 5.612 b/s = 0.281/tick. */
	public static final double SPRINT = 0.31;
	/** Vanilla sprint-jump bursts to ~0.56/tick on the first airborne ticks. */
	public static final double SPRINT_JUMP = 0.65;

	// ── The plugin's flat-ceiling terms (CheatPreventionListener.onMove) ────────
	/** Plugin base flat ceiling: 0.42 blocks/tick before multipliers. */
	public static final double PLUGIN_FLAT_BASE = 0.42;
	/** Speed potion: +20% per level, amplifier 0-based (plugin speedMult). */
	public static final double SPEED_POTION_PER_LEVEL = 0.2;
	/** Sprint multiplier on the flat ceiling (plugin sprintMult). */
	public static final double SPRINT_MULTIPLIER = 1.3;
	/** Ice-family underfoot multiplier (plugin iceMult). */
	public static final double ICE_MULTIPLIER = 1.4;
	/**
	 * Sprint-jump burst multiplier for the first {@link #BURST_AIRBORNE_TICKS} airborne ticks of
	 * a sprint jump (plugin burstMult / isJumpBurst).
	 */
	public static final double SPRINT_JUMP_BURST_MULTIPLIER = 1.7;
	/**
	 * Client-side margin under the server's exact ceiling: clamp at 98% of what the server
	 * merely tolerates, so the client is always strictly "before the flag".
	 */
	public static final double SAFETY = 0.98;

	/**
	 * In-water ceiling as a flat value: covers sprint-swim, Depth Strider III and Dolphin's
	 * Grace (~0.5-0.9/tick) while staying under the plugin's water allowance
	 * (0.42 * 1.3 * 2.0 * ... &gt; 1.0), so no swim combination is ever clamped.
	 */
	public static final double WATER_CEILING = 1.2;

	// ── Vertical (MovementSimulationCheck FLY + vanilla constants) ──────────────
	/** Grounded upward floor: vanilla step height is 0.6; the first jump tick is 0.42. */
	public static final double UPWARD_FLOOR = 0.65;
	/** Plugin MAX_JUMP_BASE — the per-tick jump-arc ceiling while airborne. */
	public static final double JUMP_UP_BASE = 0.55;
	/** Plugin JUMP_BOOST_PER_LEVEL: each jump-boost level adds 0.1 to the arc. */
	public static final double JUMP_BOOST_PER_LEVEL = 0.1;
	/** Vanilla terminal fall speed is 3.92/tick; clamp a hair above so long falls never clip. */
	public static final double FALL_TERMINAL = 4.0;
	/**
	 * Bubble columns / fast water ascent exceed the walk arc; the plugin's flight check exempts
	 * water entirely, so the client ceiling only needs to be above vanilla ascent speeds.
	 */
	public static final double WATER_UPWARD_CEILING = 2.0;
	/** Plugin FastClimbCheck CLIMB_SPEED_CEILING — ladder/vine/scaffolding climb speed. */
	public static final double CLIMB_CEILING = 0.4;
	/** Slime/honey bounce launches: exempted server-side (isOnBouncyBlock), up to ~0.9/t. */
	public static final double BOUNCY_UPWARD_CEILING = 2.0;
	/**
	 * Elytra (firework chains reach ~1.7 b/t), riptide, vehicles (blue-ice boats hit 2.0 b/t)
	 * and creative flight: the plugin exempts these states entirely, so the envelope uses one
	 * generous ceiling that only catches protocol-breaking speeds.
	 */
	public static final double EXEMPT_STATE_CEILING = 8.0;

	/** Length of the sprint-jump burst window (plugin isJumpBurst: airborneTicks <= 2). */
	public static final int BURST_AIRBORNE_TICKS = 2;

	/** True movement state, snapshotted by the caller on the client thread each tick. */
	public record State(
		boolean sprinting,
		boolean onGround,
		/** Consecutive airborne ticks BEFORE the packet being evaluated (0 = last tick grounded). */
		int airborneTicks,
		/** Speed potion amplifier, or -1 when absent. */
		int speedAmplifier,
		/** Jump Boost amplifier, or -1 when absent. */
		int jumpBoostAmplifier,
		boolean onIce,
		boolean inWater,
		boolean gliding,
		boolean riptiding,
		boolean vehicle,
		boolean flying,
		boolean levitation,
		boolean climbing,
		/** Standing on/against slime or honey (bounce launches are exempt server-side). */
		boolean onBouncy,
		/** Velocity/teleport grace window active — server-sanctioned impulse, do not clamp. */
		boolean inGrace
	) {
	}

	/** Clamped delta plus which axes were touched (for logging). */
	public record Result(double dx, double dy, double dz, boolean clampedHorizontal, boolean clampedVertical) {
	}

	private MovementEnvelope() {
	}

	/** Maximum legal horizontal speed (blocks/tick) for the state (rising-tick burst window). */
	public static double horizontalCeiling(State s) {
		return horizontalCeiling(s, Double.POSITIVE_INFINITY);
	}

	/**
	 * Maximum legal horizontal speed for the state, given the packet's dy. The sprint-jump burst
	 * multiplier applies only on RISING airborne ticks (plugin isJumpBurst: {@code dy > 0.1 &&
	 * sprinting && !grounded && airborne <= 2}) — a falling tick must obey the sustained ceiling,
	 * exactly like the server's own check.
	 */
	public static double horizontalCeiling(State s, double dy) {
		if (s.gliding() || s.riptiding() || s.vehicle() || s.flying()) {
			return EXEMPT_STATE_CEILING;
		}
		if (s.inWater()) {
			// Flat water ceiling — every vanilla swim combination fits, none can abuse it.
			return WATER_CEILING;
		}
		// The plugin's flat ceiling, term for term.
		double ceiling = PLUGIN_FLAT_BASE;
		if (s.speedAmplifier() >= 0) {
			ceiling *= 1.0 + SPEED_POTION_PER_LEVEL * (s.speedAmplifier() + 1);
		}
		if (s.sprinting()) {
			ceiling *= SPRINT_MULTIPLIER;
		}
		if (s.onIce()) {
			ceiling *= ICE_MULTIPLIER;
		}
		if (dy > 0.1 && s.sprinting() && !s.onGround() && s.airborneTicks() <= BURST_AIRBORNE_TICKS) {
			ceiling *= SPRINT_JUMP_BURST_MULTIPLIER;
		}
		return ceiling * SAFETY;
	}

	/** Maximum legal upward dy (blocks/tick) for the state. */
	public static double upwardCeiling(State s) {
		if (s.gliding() || s.riptiding() || s.vehicle() || s.flying() || s.levitation()) {
			return EXEMPT_STATE_CEILING;
		}
		if (s.onBouncy()) {
			return BOUNCY_UPWARD_CEILING;
		}
		if (s.inWater()) {
			return WATER_UPWARD_CEILING;
		}
		if (s.climbing()) {
			// Plugin FastClimbCheck: climbing faster than 0.4/t is its own flag.
			return CLIMB_CEILING;
		}
		double jumpArc = s.jumpBoostAmplifier() >= 0
			? JUMP_UP_BASE + JUMP_BOOST_PER_LEVEL * s.jumpBoostAmplifier()
			: JUMP_UP_BASE;
		// Grounded: honor the vanilla step height (0.6) above the jump arc. Airborne: the plugin
		// flags sustained ascent above the arc, so the clamp sits exactly at the arc edge.
		return s.onGround() ? Math.max(UPWARD_FLOOR, jumpArc) : jumpArc;
	}

	/** Maximum legal downward dy magnitude (blocks/tick) for the state. */
	public static double downwardCeiling(State s) {
		// Elytra dives can exceed the walk terminal velocity; everything else is capped at 3.92.
		return s.gliding() || s.riptiding() ? EXEMPT_STATE_CEILING : FALL_TERMINAL;
	}

	/**
	 * Clamp the per-tick delta into the envelope. Never drops motion: the horizontal vector is
	 * scaled to its ceiling and dy is bounded. A delta already inside the envelope is returned
	 * unchanged.
	 */
	public static Result clamp(double dx, double dy, double dz, State s) {
		if (s.inGrace()) {
			return new Result(dx, dy, dz, false, false);
		}
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double horizontalMax = horizontalCeiling(s, dy);
		double scale = horizontal > horizontalMax && horizontal > 0.0 ? horizontalMax / horizontal : 1.0;

		double upMax = upwardCeiling(s);
		double downMax = downwardCeiling(s);
		double clampedDy = Math.min(Math.max(dy, -downMax), upMax);

		return new Result(dx * scale, clampedDy, dz * scale, scale < 1.0, clampedDy != dy);
	}
}
