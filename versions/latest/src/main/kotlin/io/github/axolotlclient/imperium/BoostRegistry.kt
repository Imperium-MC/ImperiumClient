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

import java.util.concurrent.ConcurrentHashMap

/**
 * Active server-side boosts (Alpine "enchantedmc / boosts" parity). ImperiumMC has no boost
 * wire protocol yet, so this registry is the shell the future server channel populates:
 * when the `imperiumclient:features` protocol grows boost sync, its receiver calls
 * [put] / [remove] / [clear] and the Boosts HUD starts listing them.
 *
 * Expiry is absolute wall-clock ([System.currentTimeMillis]), which is what a
 * server payload would carry. Thread-safe: a netty-thread receiver may write before the
 * payload is handed to the client thread.
 */
object BoostRegistry {

	/** One boost: stable id, display name, wall-clock expiry in epoch millis. */
	class ActiveBoost(private val id: String, private val name: String, private val endsAtEpochMs: Long) {

		fun id(): String = id

		fun name(): String = name

		fun endsAtEpochMs(): Long = endsAtEpochMs

		override fun equals(other: Any?): Boolean =
			this === other || (other is ActiveBoost && id == other.id && name == other.name && endsAtEpochMs == other.endsAtEpochMs)

		override fun hashCode(): Int {
			var result = id.hashCode()
			result = 31 * result + name.hashCode()
			result = 31 * result + endsAtEpochMs.hashCode()
			return result
		}

		override fun toString(): String = "ActiveBoost[id=$id, name=$name, endsAtEpochMs=$endsAtEpochMs]"
	}

	private val BOOSTS = ConcurrentHashMap<String, ActiveBoost>()

	/** Register or refresh a boost that ends `durationMs` from now. */
	@JvmStatic
	fun start(id: String, name: String, durationMs: Long) {
		put(id, name, System.currentTimeMillis() + durationMs)
	}

	/** Register or refresh a boost with an absolute epoch-millis expiry. */
	@JvmStatic
	fun put(id: String, name: String, endsAtEpochMs: Long) {
		BOOSTS.put(id, ActiveBoost(id, name, endsAtEpochMs))
	}

	@JvmStatic
	fun remove(id: String) {
		BOOSTS.remove(id)
	}

	@JvmStatic
	fun clear() {
		BOOSTS.clear()
	}

	/** Unexpired boosts, soonest-ending first. Prunes expired entries as a side effect. */
	@JvmStatic
	fun active(): List<ActiveBoost> {
		val now = System.currentTimeMillis()
		BOOSTS.values.removeIf { boost: ActiveBoost -> boost.endsAtEpochMs() <= now }
		return BOOSTS.values.stream()
			.sorted(Comparator.comparingLong(ActiveBoost::endsAtEpochMs))
			.toList()
	}
}
