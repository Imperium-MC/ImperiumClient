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

package io.github.axolotlclient.imperium.enforcement
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure movement-envelope math for the local rules enforcement layer (docs/wave4-roadmap.md W4-C).
 * No Minecraft imports — everything arrives as a [State] snapshot and doubles, so the whole
 * model is unit-testable and thread-safe by construction.
 *
 * ## Model
 *
 * The horizontal ceiling mirrors the ImperiumMC plugin's own speed-punishment edge — the flat
 * ceiling of `CheatPreventionListener.onMove`:
 *
 * `0.42 * speedPotion * sprint(1.3) * ice(1.4) * sprintJumpBurst(1.7, first 2 airborne ticks)`
 *
 * scaled by a [SAFETY] factor (0.98) so the client always clamps strictly *before*
 * the server would flag the same delta. Every vanilla-achievable speed sits under the resulting
 * ceiling, which the unit tests pin against the vanilla reference tiers:
 *
 *  - walk 4.317 b/s = 0.216/tick ([WALK]) vs grounded walk ceiling 0.41
 *  - sprint 5.612 b/s = 0.281/tick ([SPRINT]) vs grounded sprint ceiling 0.52
 *  - sprint-jump bursts ~0.56/tick ([SPRINT_JUMP]) vs the burst-window ceiling 0.88;
 *    decaying mid-arc/landing momentum (~0.45-0.55) fits under the sustained ceiling 0.52 —
 *    the same band the server itself tolerates before its violation buffer trips
 *
 * Vertical terms mirror `MovementSimulationCheck`'s FLY sub-check: the per-tick upward
 * arc `MAX_JUMP_BASE = 0.55 + 0.1 * jumpBoostLevel` with a 0.65 floor for the vanilla 0.6
 * step height, and the 3.92 terminal fall speed.
 *
 * States the plugin exempts entirely (elytra/glider, riptide spin-attack, vehicles, creative
 * flight, levitation) get one generous ceiling that only catches protocol-breaking speeds, so
 * legit firework chains and blue-ice boats are never touched.
 *
 * Clamping never drops a packet: the horizontal vector is scaled to the ceiling (direction
 * preserved) and dy is clamped to `[-downwardCeiling, upwardCeiling]`. A player whose
 * delta exceeds the envelope keeps moving, just at the maximum legal speed — a third-party cheat
 * mod cannot make them move illegally, and a legit player never desyncs.
 */
object MovementEnvelope {

	// ── Vanilla reference tiers (blocks/tick) — what legit movement actually does ──
	/** Vanilla walking is 4.317 b/s = 0.216/tick. */
	const val WALK = 0.22

	/** Vanilla sprinting is 5.612 b/s = 0.281/tick. */
	const val SPRINT = 0.31

	/** Vanilla sprint-jump bursts to ~0.56/tick on the first airborne ticks. */
	const val SPRINT_JUMP = 0.65

	// ── The plugin's flat-ceiling terms (CheatPreventionListener.onMove) ────────
	/** Plugin base flat ceiling: 0.42 blocks/tick before multipliers. */
	const val PLUGIN_FLAT_BASE = 0.42

	/** Speed potion: +20% per level, amplifier 0-based (plugin speedMult). */
	const val SPEED_POTION_PER_LEVEL = 0.2

	/** Sprint multiplier on the flat ceiling (plugin sprintMult). */
	const val SPRINT_MULTIPLIER = 1.3

	/** Ice-family underfoot multiplier (plugin iceMult). */
	const val ICE_MULTIPLIER = 1.4

	/**
	 * Sprint-jump burst multiplier for the first [BURST_AIRBORNE_TICKS] airborne ticks of
	 * a sprint jump (plugin burstMult / isJumpBurst).
	 */
	const val SPRINT_JUMP_BURST_MULTIPLIER = 1.7

	/**
	 * Client-side margin under the server's exact ceiling: clamp at 98% of what the server
	 * merely tolerates, so the client is always strictly "before the flag".
	 */
	const val SAFETY = 0.98

	/**
	 * In-water ceiling as a flat value: covers sprint-swim, Depth Strider III and Dolphin's
	 * Grace (~0.5-0.9/tick) while staying under the plugin's water allowance
	 * (0.42 * 1.3 * 2.0 * ... > 1.0), so no swim combination is ever clamped.
	 */
	const val WATER_CEILING = 1.2

	// ── Vertical (MovementSimulationCheck FLY + vanilla constants) ──────────────
	/** Grounded upward floor: vanilla step height is 0.6; the first jump tick is 0.42. */
	const val UPWARD_FLOOR = 0.65

	/** Plugin MAX_JUMP_BASE — the per-tick jump-arc ceiling while airborne. */
	const val JUMP_UP_BASE = 0.55

	/** Plugin JUMP_BOOST_PER_LEVEL: each jump-boost level adds 0.1 to the arc. */
	const val JUMP_BOOST_PER_LEVEL = 0.1

	/** Vanilla terminal fall speed is 3.92/tick; clamp a hair above so long falls never clip. */
	const val FALL_TERMINAL = 4.0

	/**
	 * Bubble columns / fast water ascent exceed the walk arc; the plugin's flight check exempts
	 * water entirely, so the client ceiling only needs to be above vanilla ascent speeds.
	 */
	const val WATER_UPWARD_CEILING = 2.0

	/** Plugin FastClimbCheck CLIMB_SPEED_CEILING — ladder/vine/scaffolding climb speed. */
	const val CLIMB_CEILING = 0.4

	/** Slime/honey bounce launches: exempted server-side (isOnBouncyBlock), up to ~0.9/t. */
	const val BOUNCY_UPWARD_CEILING = 2.0

	/**
	 * Elytra (firework chains reach ~1.7 b/t), riptide, vehicles (blue-ice boats hit 2.0 b/t)
	 * and creative flight: the plugin exempts these states entirely, so the envelope uses one
	 * generous ceiling that only catches protocol-breaking speeds.
	 */
	const val EXEMPT_STATE_CEILING = 8.0

	/** Length of the sprint-jump burst window (plugin isJumpBurst: airborneTicks <= 2). */
	const val BURST_AIRBORNE_TICKS = 2

	/** True movement state, snapshotted by the caller on the client thread each tick. */
	class State(
		private val sprinting: Boolean,
		private val onGround: Boolean,
		/** Consecutive airborne ticks BEFORE the packet being evaluated (0 = last tick grounded). */
		private val airborneTicks: Int,
		/** Speed potion amplifier, or -1 when absent. */
		private val speedAmplifier: Int,
		/** Jump Boost amplifier, or -1 when absent. */
		private val jumpBoostAmplifier: Int,
		private val onIce: Boolean,
		private val inWater: Boolean,
		private val gliding: Boolean,
		private val riptiding: Boolean,
		private val vehicle: Boolean,
		private val flying: Boolean,
		private val levitation: Boolean,
		private val climbing: Boolean,
		/** Standing on/against slime or honey (bounce launches are exempt server-side). */
		private val onBouncy: Boolean,
		/** Velocity/teleport grace window active — server-sanctioned impulse, do not clamp. */
		private val inGrace: Boolean
	) {

		fun sprinting(): Boolean = sprinting

		fun onGround(): Boolean = onGround

		fun airborneTicks(): Int = airborneTicks

		fun speedAmplifier(): Int = speedAmplifier

		fun jumpBoostAmplifier(): Int = jumpBoostAmplifier

		fun onIce(): Boolean = onIce

		fun inWater(): Boolean = inWater

		fun gliding(): Boolean = gliding

		fun riptiding(): Boolean = riptiding

		fun vehicle(): Boolean = vehicle

		fun flying(): Boolean = flying

		fun levitation(): Boolean = levitation

		fun climbing(): Boolean = climbing

		fun onBouncy(): Boolean = onBouncy

		fun inGrace(): Boolean = inGrace
	}

	/** Clamped delta plus which axes were touched (for logging). */
	class Result(
		private val dx: Double,
		private val dy: Double,
		private val dz: Double,
		private val clampedHorizontal: Boolean,
		private val clampedVertical: Boolean
	) {

		fun dx(): Double = dx

		fun dy(): Double = dy

		fun dz(): Double = dz

		fun clampedHorizontal(): Boolean = clampedHorizontal

		fun clampedVertical(): Boolean = clampedVertical
	}

	/** Maximum legal horizontal speed (blocks/tick) for the state (rising-tick burst window). */
	@JvmStatic
	fun horizontalCeiling(s: State): Double = horizontalCeiling(s, Double.POSITIVE_INFINITY)

	/**
	 * Maximum legal horizontal speed for the state, given the packet's dy. The sprint-jump burst
	 * multiplier applies only on RISING airborne ticks (plugin isJumpBurst: `dy > 0.1 &&
	 * sprinting && !grounded && airborne <= 2`) — a falling tick must obey the sustained ceiling,
	 * exactly like the server's own check.
	 */
	@JvmStatic
	fun horizontalCeiling(s: State, dy: Double): Double {
		if (s.gliding() || s.riptiding() || s.vehicle() || s.flying()) {
			return EXEMPT_STATE_CEILING
		}
		if (s.inWater()) {
			// Flat water ceiling — every vanilla swim combination fits, none can abuse it.
			return WATER_CEILING
		}
		// The plugin's flat ceiling, term for term.
		var ceiling = PLUGIN_FLAT_BASE
		if (s.speedAmplifier() >= 0) {
			ceiling *= 1.0 + SPEED_POTION_PER_LEVEL * (s.speedAmplifier() + 1)
		}
		if (s.sprinting()) {
			ceiling *= SPRINT_MULTIPLIER
		}
		if (s.onIce()) {
			ceiling *= ICE_MULTIPLIER
		}
		if (dy > 0.1 && s.sprinting() && !s.onGround() && s.airborneTicks() <= BURST_AIRBORNE_TICKS) {
			ceiling *= SPRINT_JUMP_BURST_MULTIPLIER
		}
		return ceiling * SAFETY
	}

	/** Maximum legal upward dy (blocks/tick) for the state. */
	@JvmStatic
	fun upwardCeiling(s: State): Double {
		if (s.gliding() || s.riptiding() || s.vehicle() || s.flying() || s.levitation()) {
			return EXEMPT_STATE_CEILING
		}
		if (s.onBouncy()) {
			return BOUNCY_UPWARD_CEILING
		}
		if (s.inWater()) {
			return WATER_UPWARD_CEILING
		}
		if (s.climbing()) {
			// Plugin FastClimbCheck: climbing faster than 0.4/t is its own flag.
			return CLIMB_CEILING
		}
		val jumpArc = if (s.jumpBoostAmplifier() >= 0) JUMP_UP_BASE + JUMP_BOOST_PER_LEVEL * s.jumpBoostAmplifier() else JUMP_UP_BASE
		// Grounded: honor the vanilla step height (0.6) above the jump arc. Airborne: the plugin
		// flags sustained ascent above the arc, so the clamp sits exactly at the arc edge.
		return if (s.onGround()) max(UPWARD_FLOOR, jumpArc) else jumpArc
	}

	/** Maximum legal downward dy magnitude (blocks/tick) for the state. */
	@JvmStatic
	fun downwardCeiling(s: State): Double =
		// Elytra dives can exceed the walk terminal velocity; everything else is capped at 3.92.
		if (s.gliding() || s.riptiding()) EXEMPT_STATE_CEILING else FALL_TERMINAL

	/**
	 * Clamp the per-tick delta into the envelope. Never drops motion: the horizontal vector is
	 * scaled to its ceiling and dy is bounded. A delta already inside the envelope is returned
	 * unchanged.
	 */
	@JvmStatic
	fun clamp(dx: Double, dy: Double, dz: Double, s: State): Result {
		if (s.inGrace()) {
			return Result(dx, dy, dz, false, false)
		}
		val horizontal = sqrt(dx * dx + dz * dz)
		val horizontalMax = horizontalCeiling(s, dy)
		val scale = if (horizontal > horizontalMax && horizontal > 0.0) horizontalMax / horizontal else 1.0

		val upMax = upwardCeiling(s)
		val downMax = downwardCeiling(s)
		val clampedDy = min(max(dy, -downMax), upMax)

		return Result(dx * scale, clampedDy, dz * scale, scale < 1.0, clampedDy != dy)
	}
}
