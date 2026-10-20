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

package io.github.axolotlclient.imperium

import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.ConcurrentHashMap

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Session state for the ImperiumMC server integration (docs/server-gated-features.md).
 *
 * Holds the current server's feature grants and denies. Grants reset on disconnect by
 * protocol rule — a gated module must never stay unlocked across servers. Cleared on
 * DISCONNECT by [ImperiumIntegration].
 *
 * Gated modules (M2/M5: auto_fish, auto_click) query [isAllowed] before
 * enabling: a module cannot be enabled unless the CURRENT server granted it this session.
 * [isDenied] applies to any module the server wants force-disabled for
 * fairness, gated or not.
 *
 * Protocol v2 adds the **limited tier**: an id listed in `limited` is
 * usable but rate-budgeted. The optional `limits` object carries per-id rate hints
 * ({"auto_walk":{"per_hour":N}}); each becomes a token bucket ([RateBudget]) that
 * limited-tier modules consume from per activation ([tryConsumeBudget]).
 * v1 payloads (no `limited`/`limits`) keep working unchanged.
 */
object ImperiumClientSession {

	/** The server's protocol version from the latest features packet (0 = none received). */
	@Volatile
	private var serverProtocolVersion = 0

	private val allowed = ConcurrentHashMap.newKeySet<String>()
	private val limited = ConcurrentHashMap.newKeySet<String>()
	private val denied = ConcurrentHashMap.newKeySet<String>()

	/** Token buckets for limited-tier ids, rebuilt from each features packet. */
	private val budgets = ConcurrentHashMap<String, RateBudget>()

	@JvmStatic
	fun getInstance(): ImperiumClientSession = this

	fun isAllowed(moduleId: String): Boolean = allowed.contains(moduleId)

	/** True when the id sits in the v2 `limited` tier (enabled, rate-budgeted). */
	fun isLimited(moduleId: String): Boolean = limited.contains(moduleId)

	fun isDenied(moduleId: String): Boolean = denied.contains(moduleId)

	/** True when any server sent a features packet this session (grants are meaningful). */
	fun serverResponded(): Boolean = serverProtocolVersion > 0

	fun getAllowed(): Set<String> = allowed.toSet()

	fun getLimited(): Set<String> = limited.toSet()

	fun getDenied(): Set<String> = denied.toSet()

	/**
	 * True when the limited-tier id may still act (refills lazily, does not consume). An id
	 * in `limited` without a `limits` entry has no server-set rate and counts as
	 * always within budget.
	 */
	fun hasBudget(moduleId: String): Boolean {
		val budget = budgets[moduleId]
		return budget == null || budget.hasBudget()
	}

	/**
	 * Consumes one activation token for the limited-tier id. Returns true when the action is
	 * within budget (including the no-limit case); false when the bucket is empty and the
	 * module must hold off. Called by the module itself, once per activation.
	 */
	fun tryConsumeBudget(moduleId: String): Boolean {
		val budget = budgets[moduleId]
		return budget == null || budget.tryConsume()
	}

	/** Current tokens for the id; [Double.POSITIVE_INFINITY] when unbounded. For tooltips/tests. */
	fun getBudgetTokens(moduleId: String): Double {
		val budget = budgets[moduleId]
		return budget?.tokens() ?: Double.POSITIVE_INFINITY
	}

	/**
	 * Apply a features packet payload: {"version":1,"allow":[...],"deny":[...]} (v1) or
	 * {"version":2,"allow":[...],"limited":[...],"deny":[...],"limits":{id:{"per_hour":N}}}
	 * (v2). Unknown ids are ignored by protocol; unknown keys are ignored for forward
	 * compatibility. Replaces any prior grant set (the packet is authoritative per send).
	 */
	fun applyFeatures(json: String) {
		try {
			val parsed = JsonParser.parseString(json)
			if (!parsed.isJsonObject) {
				return
			}
			val obj = parsed.asJsonObject
			if (obj.has("version") && obj.get("version").isJsonPrimitive) {
				try {
					serverProtocolVersion = obj.get("version").asInt
				} catch (ignored: NumberFormatException) {
				}
			}
			allowed.clear()
			limited.clear()
			denied.clear()
			budgets.clear()
			allowed.addAll(readStringArray(obj, "allow"))
			limited.addAll(readStringArray(obj, "limited"))
			denied.addAll(readStringArray(obj, "deny"))
			readLimits(obj)
		} catch (ignored: Exception) {
			// Malformed payload: keep everything locked (the protocol's safe default).
		}
	}

	/** Grants reset on disconnect — rule 2 of the protocol. */
	fun reset() {
		serverProtocolVersion = 0
		allowed.clear()
		limited.clear()
		denied.clear()
		budgets.clear()
	}

	/**
	 * Parses the v2 `limits` object: id → {"per_hour":N} or {"per_minute":N}
	 * (per_minute wins when both are present — the finer window). Each entry becomes a
	 * fresh, full token bucket sized to the window.
	 */
	private fun readLimits(obj: JsonObject) {
		if (!obj.has("limits") || !obj.get("limits").isJsonObject) {
			return
		}
		val limits = obj.getAsJsonObject("limits")
		for (entry in limits.entrySet()) {
			if (!entry.value.isJsonObject) {
				continue
			}
			val budget = RateBudget.fromJson(entry.value.asJsonObject)
			if (budget != null) {
				budgets.put(entry.key, budget)
			}
		}
	}

	private fun readStringArray(obj: JsonObject, key: String): List<String> {
		if (!obj.has(key) || !obj.get(key).isJsonArray) {
			return listOf()
		}
		val array = obj.getAsJsonArray(key)
		val out = ArrayList<String>(array.size())
		for (element in array) {
			if (element.isJsonPrimitive) {
				out.add(element.asString)
			}
		}
		return out
	}

	/**
	 * Simple token bucket backing the v2 limited tier. Capacity equals the server's rate
	 * amount per window; tokens refill continuously at amount/window so long sessions
	 * converge on the advertised rate. A fresh bucket starts full (a new session gets its
	 * window's worth up front — servers wanting strict pacing set a small amount). Buckets
	 * are created and consumed on the client thread only; the synchronized blocks are
	 * tick-scoped and never shared with virtual-thread executors.
	 */
	class RateBudget private constructor(
		private val capacity: Double,
		private val tokensPerMs: Double,
		nowMs: Long
	) {

		private var tokens: Double
		private var lastRefillMs: Long

		init {
			tokens = capacity
			lastRefillMs = nowMs
		}

		fun hasBudget(): Boolean = synchronized(this) {
			refill()
			tokens >= 1.0
		}

		fun tryConsume(): Boolean = synchronized(this) {
			refill()
			if (tokens < 1.0) {
				false
			} else {
				tokens -= 1.0
				true
			}
		}

		fun tokens(): Double = synchronized(this) {
			refill()
			tokens
		}

		internal fun capacity(): Double = capacity

		private fun refill() {
			val now = System.currentTimeMillis()
			if (now <= lastRefillMs) {
				return
			}
			tokens = min(capacity, tokens + (now - lastRefillMs) * tokensPerMs)
			lastRefillMs = now
		}

		companion object {

			private const val MINUTE_MS = 60_000L
			private const val HOUR_MS = 3_600_000L

			internal fun fromJson(obj: JsonObject): RateBudget? {
				val perMinute = readAmount(obj, "per_minute")
				if (perMinute > 0) {
					return RateBudget(perMinute, perMinute / MINUTE_MS, System.currentTimeMillis())
				}
				val perHour = readAmount(obj, "per_hour")
				if (perHour > 0) {
					return RateBudget(perHour, perHour / HOUR_MS, System.currentTimeMillis())
				}
				return null
			}

			private fun readAmount(obj: JsonObject, key: String): Double {
				if (obj.has(key) && obj.get(key).isJsonPrimitive) {
					try {
						return max(0.0, obj.get(key).asDouble)
					} catch (ignored: NumberFormatException) {
					}
				}
				return 0.0
			}
		}
	}
}
