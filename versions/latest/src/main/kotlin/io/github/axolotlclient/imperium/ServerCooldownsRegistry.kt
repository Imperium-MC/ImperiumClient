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
 * Server-pushed feature cooldowns (`imperiumclient:cooldowns` channel): the plugin sends the
 * player's active command/feature cooldowns, the cooldowns HUD renders them beneath the hotbar
 * item rows. Entries expire lazily on read so a missed final packet cannot leave a stale row.
 */
object ServerCooldownsRegistry {

	class ServerCooldown internal constructor(
		private val id: String,
		private val name: String,
		private val totalMs: Long,
		private val endsAtEpochMs: Long,
	) {
		fun id(): String = id

		fun name(): String = name

		/** Progress 0..1 remaining — drives the HUD's draining bar. */
		fun remainingFraction(nowMs: Long): Float {
			if (totalMs <= 0) return 0f
			val remaining = endsAtEpochMs - nowMs
			if (remaining <= 0) return 0f
			return (remaining.toFloat() / totalMs).coerceIn(0f, 1f)
		}

		fun remainingMs(nowMs: Long): Long = (endsAtEpochMs - nowMs).coerceAtLeast(0)

		fun endsAtEpochMs(): Long = endsAtEpochMs

		override fun equals(other: Any?): Boolean =
			this === other || (other is ServerCooldown && id == other.id && name == other.name &&
				totalMs == other.totalMs && endsAtEpochMs == other.endsAtEpochMs)

		override fun hashCode(): Int {
			var result = id.hashCode()
			result = 31 * result + name.hashCode()
			result = 31 * result + totalMs.hashCode()
			result = 31 * result + endsAtEpochMs.hashCode()
			return result
		}

		override fun toString(): String =
			"ServerCooldown[id=$id, name=$name, totalMs=$totalMs, endsAt=$endsAtEpochMs]"
	}

	@Volatile
	private var entries: List<ServerCooldown> = emptyList()

	/** Replace-semantics snapshot from a channel packet; [receivedAtMs] anchors the deadlines. */
	@JvmStatic
	fun put(entries: List<Entry>, receivedAtMs: Long) {
		this.entries = entries.map {
			ServerCooldown(it.id, it.name, it.totalMs.coerceAtLeast(0), receivedAtMs + it.remainingMs.coerceAtLeast(0))
		}
	}

	/** Wire shape of one cooldown from the `imperiumclient:cooldowns` packet. */
	data class Entry(val id: String, val name: String, val totalMs: Long, val remainingMs: Long)

	@JvmStatic
	fun clear() {
		entries = emptyList()
	}

	/** Non-expired entries, lazily dropping stale ones. */
	@JvmStatic
	fun active(nowMs: Long): List<ServerCooldown> {
		val current = entries
		if (current.none { it.endsAtEpochMs() <= nowMs }) {
			return current
		}
		val kept = current.filter { it.endsAtEpochMs() > nowMs }
		entries = kept
		return kept
	}
}
