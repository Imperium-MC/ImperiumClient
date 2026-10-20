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

package io.github.axolotlclient.imperium

/**
 * The player's current ImperiumMC legion (Alpine "factions status HUD" parity, reimplemented
 * for Legions — see docs/legions-tooling.md). ImperiumMC has no legion wire protocol yet, so
 * this registry is the shell the future server channel populates: when an
 * `imperiumclient:legion` channel ships, its receiver calls [set] on state
 * pushes and [clear] on leave/disconnect, and the Legion HUD starts rendering.
 *
 * Field names mirror what the plugin's LegionManager already tracks server-side
 * (Legion.name / memberCount / level, plus the war-score "power" standings), so the future
 * receiver is a straight mapping. `null` state means "no legion" — the HUD hides
 * itself entirely (BoostsHud shell pattern). Thread-safe: a netty-thread receiver may write
 * before the payload reaches the client thread.
 */
object LegionStateRegistry {

	/** One snapshot of the player's legion. Power is the plugin's war-score currency. */
	class LegionState(
		private val name: String,
		private val onlineMembers: Int,
		private val totalMembers: Int,
		private val power: Long,
		private val level: Int
	) {

		fun name(): String = name

		fun onlineMembers(): Int = onlineMembers

		fun totalMembers(): Int = totalMembers

		fun power(): Long = power

		fun level(): Int = level

		override fun equals(other: Any?): Boolean =
			this === other || (other is LegionState && name == other.name && onlineMembers == other.onlineMembers
				&& totalMembers == other.totalMembers && power == other.power && level == other.level)

		override fun hashCode(): Int {
			var result = name.hashCode()
			result = 31 * result + onlineMembers
			result = 31 * result + totalMembers
			result = 31 * result + power.hashCode()
			result = 31 * result + level
			return result
		}

		override fun toString(): String =
			"LegionState[name=$name, onlineMembers=$onlineMembers, totalMembers=$totalMembers, power=$power, level=$level]"
	}

	@Volatile
	private var state: LegionState? = null

	/** Test-friendly setter; also the exact entry point a future channel receiver will call. */
	@JvmStatic
	fun set(name: String, onlineMembers: Int, totalMembers: Int, power: Long, level: Int) {
		state = LegionState(name, onlineMembers, totalMembers, power, level)
	}

	/** Updates only the fields a delta push carries, keeping the rest of the snapshot. */
	@JvmStatic
	fun update(onlineMembers: Int?, power: Long?) {
		val current = state ?: return // nothing to patch: wait for the next full snapshot
		state = LegionState(
			current.name(),
			onlineMembers ?: current.onlineMembers(),
			current.totalMembers(),
			power ?: current.power(),
			current.level()
		)
	}

	@JvmStatic
	fun get(): LegionState? = state

	@JvmStatic
	fun clear() {
		state = null
	}
}
