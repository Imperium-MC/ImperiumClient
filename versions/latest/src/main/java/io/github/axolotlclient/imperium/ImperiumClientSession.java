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
 */
public final class ImperiumClientSession {

	@Getter
	private static final ImperiumClientSession instance = new ImperiumClientSession();

	/** The server's protocol version from the latest features packet (0 = none received). */
	private volatile int serverProtocolVersion = 0;

	private final Set<String> allowed = ConcurrentHashMap.newKeySet();
	private final Set<String> denied = ConcurrentHashMap.newKeySet();

	private ImperiumClientSession() {
	}

	public boolean isAllowed(String moduleId) {
		return allowed.contains(moduleId);
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

	public Set<String> getDenied() {
		return Set.copyOf(denied);
	}

	/**
	 * Apply a features packet payload: {"version":1,"allow":[...],"deny":[...]}. Unknown ids
	 * are ignored by protocol; unknown keys are ignored for forward compatibility. Replaces
	 * any prior grant set (the packet is authoritative per send).
	 */
	void applyFeatures(String json) {
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
			denied.clear();
			allowed.addAll(readStringArray(obj, "allow"));
			denied.addAll(readStringArray(obj, "deny"));
		} catch (Exception ignored) {
			// Malformed payload: keep everything locked (the protocol's safe default).
		}
	}

	/** Grants reset on disconnect — rule 2 of the protocol. */
	void reset() {
		serverProtocolVersion = 0;
		allowed.clear();
		denied.clear();
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
}
