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

package io.github.axolotlclient.imperium;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active server-side boosts (Alpine "enchantedmc / boosts" parity). ImperiumMC has no boost
 * wire protocol yet, so this registry is the shell the future server channel populates:
 * when the {@code imperiumclient:features} protocol grows boost sync, its receiver calls
 * {@link #put} / {@link #remove} / {@link #clear} and the Boosts HUD starts listing them.
 *
 * <p>Expiry is absolute wall-clock ({@link System#currentTimeMillis()}), which is what a
 * server payload would carry. Thread-safe: a netty-thread receiver may write before the
 * payload is handed to the client thread.</p>
 */
public final class BoostRegistry {

	/** One boost: stable id, display name, wall-clock expiry in epoch millis. */
	public record ActiveBoost(String id, String name, long endsAtEpochMs) {
	}

	private static final Map<String, ActiveBoost> BOOSTS = new ConcurrentHashMap<>();

	private BoostRegistry() {
	}

	/** Register or refresh a boost that ends {@code durationMs} from now. */
	public static void start(String id, String name, long durationMs) {
		put(id, name, System.currentTimeMillis() + durationMs);
	}

	/** Register or refresh a boost with an absolute epoch-millis expiry. */
	public static void put(String id, String name, long endsAtEpochMs) {
		BOOSTS.put(id, new ActiveBoost(id, name, endsAtEpochMs));
	}

	public static void remove(String id) {
		BOOSTS.remove(id);
	}

	public static void clear() {
		BOOSTS.clear();
	}

	/** Unexpired boosts, soonest-ending first. Prunes expired entries as a side effect. */
	public static List<ActiveBoost> active() {
		long now = System.currentTimeMillis();
		BOOSTS.values().removeIf(boost -> boost.endsAtEpochMs() <= now);
		return BOOSTS.values().stream()
			.sorted(Comparator.comparingLong(ActiveBoost::endsAtEpochMs))
			.toList();
	}
}
