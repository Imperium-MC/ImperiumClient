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

import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.Minecraft;

/**
 * The enforcement half of docs/server-gated-features.md, shared by the gated automation
 * modules (auto_fish, auto_click). Read-only consumer of {@link ImperiumClientSession}:
 * a gated module is usable only when
 *
 * <ul>
 *   <li>the player is in a singleplayer world (rule 2 — singleplayer counts as granted), or</li>
 *   <li>the CURRENT server sent an {@code imperiumclient:features} grant for it this session.</li>
 * </ul>
 *
 * <p>{@link #enforce} is called from the module's {@code tick()}, i.e. before every automation
 * activation, and keeps the module's {@code enabled} option force-off (with a lock-reason
 * tooltip) while the gate is closed. It only clears a force-off it set itself — one applied
 * by the {@code block_mods} channel (ban_reason) survives a grant, as it should.</p>
 */
public final class ServerGating {

	/** Gated module ids from the protocol's table (docs/server-gated-features.md). */
	public static final String AUTO_FISH = "auto_fish";
	public static final String AUTO_CLICK = "auto_click";

	private ServerGating() {
	}

	/** True when the current session permits the gated module (singleplayer or explicit grant). */
	public static boolean granted(String moduleId) {
		return Minecraft.getInstance().hasSingleplayerServer()
			|| ImperiumClientSession.getInstance().isAllowed(moduleId);
	}

	/**
	 * Applies the gate to a module's {@code enabled} option. Call every tick before acting:
	 * a mid-session re-grant opens the module, a replacement packet without the grant
	 * (or a deny) closes it again.
	 *
	 * @param option          the module's enabled option
	 * @param moduleId        protocol id ({@code auto_fish} / {@code auto_click})
	 * @param lockReasonKey   translation key shown as the option's tooltip while locked
	 */
	public static void enforce(ForceableBooleanOption option, String moduleId, String lockReasonKey) {
		ImperiumClientSession session = ImperiumClientSession.getInstance();
		boolean locked = session.isDenied(moduleId) || !granted(moduleId);
		boolean ours = lockReasonKey.equals(option.getReason());
		if (locked) {
			if (!option.isForceOff() || !ours) {
				option.setForceOff(true, lockReasonKey);
			}
		} else if (option.isForceOff() && ours) {
			option.setForceOff(false, "");
		}
	}
}
