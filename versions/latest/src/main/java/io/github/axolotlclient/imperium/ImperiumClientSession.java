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

package io.github.axolotlclient.imperium;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;

/**
 * Session state for the ImperiumMC server integration (docs/server-gated-features.md).
 *
 * Holds the current server's feature grants and denies. Grants reset on disconnect by
 * protocol rule — a gated module must never stay unlocked across servers. Cleared on
 * DISCONNECT by {@link ImperiumIntegration}.
 *
 * Gated modules (M2/M5: auto_fish, auto_click) query {@link #isAllowed(String)} before
 * enabling: a module cannot be enabled unless the CURRENT server granted it this session.
 * {@link #isDenied(String)} applies to any module the server wants force-disabled for
 * fairness, gated or not.
 *
 * <p>Protocol v2 adds the <strong>limited tier</strong>: an id listed in {@code limited} is
 * usable but rate-budgeted. The optional {@code limits} object carries per-id rate hints
 * ({"auto_walk":{"per_hour":N}}); each becomes a token bucket ({@link RateBudget}) that
 * limited-tier modules consume from per activation ({@link #tryConsumeBudget(String)}).
 * v1 payloads (no {@code limited}/{@code limits}) keep working unchanged.</p>
 */
public final class ImperiumClientSession {

	@Getter
	private static final ImperiumClientSession instance = new ImperiumClientSession();

	/** The server's protocol version from the latest features packet (0 = none received). */
	private volatile int serverProtocolVersion = 0;

	private final Set<String> allowed = ConcurrentHashMap.newKeySet();
	private final Set<String> limited = ConcurrentHashMap.newKeySet();
	private final Set<String> denied = ConcurrentHashMap.newKeySet();

	/** Token buckets for limited-tier ids, rebuilt from each features packet. */
	private final ConcurrentHashMap<String, RateBudget> budgets = new ConcurrentHashMap<>();

	private ImperiumClientSession() {
	}

	public boolean isAllowed(String moduleId) {
		return allowed.contains(moduleId);
	}

	/** True when the id sits in the v2 {@code limited} tier (enabled, rate-budgeted). */
	public boolean isLimited(String moduleId) {
		return limited.contains(moduleId);
	}

	public boolean isDenied(String moduleId) {
		return denied.contains(moduleId);
	}

	/** True when any server sent a features packet this session (grants are meaningful). */
	public boolean serverResponded() {
		return serverProtocolVersion > 0;
	}

	public Set<String> getAllowed() {
		return Set.copyOf(allowed);
	}

	public Set<String> getLimited() {
		return Set.copyOf(limited);
	}

	public Set<String> getDenied() {
		return Set.copyOf(denied);
	}

	/**
	 * True when the limited-tier id may still act (refills lazily, does not consume). An id
	 * in {@code limited} without a {@code limits} entry has no server-set rate and counts as
	 * always within budget.
	 */
	public boolean hasBudget(String moduleId) {
		RateBudget budget = budgets.get(moduleId);
		return budget == null || budget.hasBudget();
	}

	/**
	 * Consumes one activation token for the limited-tier id. Returns true when the action is
	 * within budget (including the no-limit case); false when the bucket is empty and the
	 * module must hold off. Called by the module itself, once per activation.
	 */
	public boolean tryConsumeBudget(String moduleId) {
		RateBudget budget = budgets.get(moduleId);
		return budget == null || budget.tryConsume();
	}

	/** Current tokens for the id; {@link Double#POSITIVE_INFINITY} when unbounded. For tooltips/tests. */
	public double getBudgetTokens(String moduleId) {
		RateBudget budget = budgets.get(moduleId);
		return budget == null ? Double.POSITIVE_INFINITY : budget.tokens();
	}

	/**
	 * Apply a features packet payload: {"version":1,"allow":[...],"deny":[...]} (v1) or
	 * {"version":2,"allow":[...],"limited":[...],"deny":[...],"limits":{id:{"per_hour":N}}}
	 * (v2). Unknown ids are ignored by protocol; unknown keys are ignored for forward
	 * compatibility. Replaces any prior grant set (the packet is authoritative per send).
	 */
	public void applyFeatures(String json) {
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				return;
			}
			JsonObject obj = parsed.getAsJsonObject();
			if (obj.has("version") && obj.get("version").isJsonPrimitive()) {
				try {
					serverProtocolVersion = obj.get("version").getAsInt();
				} catch (NumberFormatException ignored) {
				}
			}
			allowed.clear();
			limited.clear();
			denied.clear();
			budgets.clear();
			allowed.addAll(readStringArray(obj, "allow"));
			limited.addAll(readStringArray(obj, "limited"));
			denied.addAll(readStringArray(obj, "deny"));
			readLimits(obj);
		} catch (Exception ignored) {
			// Malformed payload: keep everything locked (the protocol's safe default).
		}
	}

	/** Grants reset on disconnect — rule 2 of the protocol. */
	public void reset() {
		serverProtocolVersion = 0;
		allowed.clear();
		limited.clear();
		denied.clear();
		budgets.clear();
	}

	/**
	 * Parses the v2 {@code limits} object: id → {"per_hour":N} or {"per_minute":N}
	 * (per_minute wins when both are present — the finer window). Each entry becomes a
	 * fresh, full token bucket sized to the window.
	 */
	private void readLimits(JsonObject obj) {
		if (!obj.has("limits") || !obj.get("limits").isJsonObject()) {
			return;
		}
		JsonObject limits = obj.getAsJsonObject("limits");
		for (Map.Entry<String, JsonElement> entry : limits.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			RateBudget budget = RateBudget.fromJson(entry.getValue().getAsJsonObject());
			if (budget != null) {
				budgets.put(entry.getKey(), budget);
			}
		}
	}

	private static List<String> readStringArray(JsonObject obj, String key) {
		if (!obj.has(key) || !obj.get(key).isJsonArray()) {
			return List.of();
		}
		JsonArray array = obj.getAsJsonArray(key);
		java.util.ArrayList<String> out = new java.util.ArrayList<>(array.size());
		for (JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				out.add(element.getAsString());
			}
		}
		return out;
	}

	/**
	 * Simple token bucket backing the v2 limited tier. Capacity equals the server's rate
	 * amount per window; tokens refill continuously at amount/window so long sessions
	 * converge on the advertised rate. A fresh bucket starts full (a new session gets its
	 * window's worth up front — servers wanting strict pacing set a small amount). Buckets
	 * are created and consumed on the client thread only; the synchronized blocks are
	 * tick-scoped and never shared with virtual-thread executors.
	 */
	static final class RateBudget {

		private static final long MINUTE_MS = 60_000L;
		private static final long HOUR_MS = 3_600_000L;

		private final double capacity;
		private final double tokensPerMs;
		private double tokens;
		private long lastRefillMs;

		private RateBudget(double capacity, double tokensPerMs, long nowMs) {
			this.capacity = capacity;
			this.tokensPerMs = tokensPerMs;
			this.tokens = capacity;
			this.lastRefillMs = nowMs;
		}

		static RateBudget fromJson(JsonObject obj) {
			double perMinute = readAmount(obj, "per_minute");
			if (perMinute > 0) {
				return new RateBudget(perMinute, perMinute / MINUTE_MS, System.currentTimeMillis());
			}
			double perHour = readAmount(obj, "per_hour");
			if (perHour > 0) {
				return new RateBudget(perHour, perHour / HOUR_MS, System.currentTimeMillis());
			}
			return null;
		}

		private static double readAmount(JsonObject obj, String key) {
			if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
				try {
					return Math.max(0, obj.get(key).getAsDouble());
				} catch (NumberFormatException ignored) {
				}
			}
			return 0;
		}

		synchronized boolean hasBudget() {
			refill();
			return tokens >= 1.0;
		}

		synchronized boolean tryConsume() {
			refill();
			if (tokens < 1.0) {
				return false;
			}
			tokens -= 1.0;
			return true;
		}

		synchronized double tokens() {
			refill();
			return tokens;
		}

		double capacity() {
			return capacity;
		}

		private void refill() {
			long now = System.currentTimeMillis();
			if (now <= lastRefillMs) {
				return;
			}
			tokens = Math.min(capacity, tokens + (now - lastRefillMs) * tokensPerMs);
			lastRefillMs = now;
		}
	}
}
