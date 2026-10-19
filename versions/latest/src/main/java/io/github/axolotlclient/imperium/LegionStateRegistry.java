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

import org.jspecify.annotations.Nullable;

/**
 * The player's current ImperiumMC legion (Alpine "factions status HUD" parity, reimplemented
 * for Legions — see docs/legions-tooling.md). ImperiumMC has no legion wire protocol yet, so
 * this registry is the shell the future server channel populates: when an
 * {@code imperiumclient:legion} channel ships, its receiver calls {@link #set} on state
 * pushes and {@link #clear} on leave/disconnect, and the Legion HUD starts rendering.
 *
 * <p>Field names mirror what the plugin's LegionManager already tracks server-side
 * (Legion.name / memberCount / level, plus the war-score "power" standings), so the future
 * receiver is a straight mapping. {@code null} state means "no legion" — the HUD hides
 * itself entirely (BoostsHud shell pattern). Thread-safe: a netty-thread receiver may write
 * before the payload reaches the client thread.</p>
 */
public final class LegionStateRegistry {

	/** One snapshot of the player's legion. Power is the plugin's war-score currency. */
	public record LegionState(String name, int onlineMembers, int totalMembers, long power, int level) {
	}

	private static volatile @Nullable LegionState state;

	private LegionStateRegistry() {
	}

	/** Test-friendly setter; also the exact entry point a future channel receiver will call. */
	public static void set(String name, int onlineMembers, int totalMembers, long power, int level) {
		state = new LegionState(name, onlineMembers, totalMembers, power, level);
	}

	/** Updates only the fields a delta push carries, keeping the rest of the snapshot. */
	public static void update(@Nullable Integer onlineMembers, @Nullable Long power) {
		LegionState current = state;
		if (current == null) {
			return; // nothing to patch: wait for the next full snapshot
		}
		state = new LegionState(current.name(),
			onlineMembers != null ? onlineMembers : current.onlineMembers(),
			current.totalMembers(),
			power != null ? power : current.power(),
			current.level());
	}

	public static @Nullable LegionState get() {
		return state;
	}

	public static void clear() {
		state = null;
	}
}
