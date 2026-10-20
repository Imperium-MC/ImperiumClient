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

package io.github.axolotlclient.imperium.enforcement;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;

/**
 * Config surface of the local rules enforcement layer (docs/wave4-roadmap.md W4-C). One category,
 * one option:
 *
 * <ul>
 *   <li>{@code enforcement.server_allowlist} — comma-separated hostname allowlist. The layer is
 *   scoped to servers whose address matches an entry (hostname suffix match on the dot boundary).
 *   Default: exactly {@code imperiummc.net} and {@code play.imperiummc.net}.</li>
 * </ul>
 *
 * <p><strong>There is deliberately no master {@code enabled} toggle (issue #15 item 11):</strong>
 * server rules are server policy, not a per-player switch a client user may turn off. The layer is
 * on whenever its scope conditions hold (a real network connection to an allowlisted host); memory
 * connections (singleplayer/LAN) and every other server are out of scope structurally. A stale
 * {@code enabled} key in an old config file is ignored: the loader only reads keys that match a
 * registered option.</p>
 */
public final class EnforcementConfig {

	public static final OptionCategory category = OptionCategory.create("enforcement");

	/** Default scope: the ImperiumMC addresses, exactly as the owner spec pins them. */
	public static final String DEFAULT_ALLOWLIST = "imperiummc.net,play.imperiummc.net";

	public static final StringOption serverAllowlist = new StringOption("server_allowlist", DEFAULT_ALLOWLIST);

	static {
		category.add(serverAllowlist);
	}

	private EnforcementConfig() {
	}

	/** Parses the allowlist option into trimmed, lowercase entries. */
	public static List<String> allowlist() {
		String raw = serverAllowlist.get();
		List<String> entries = new ArrayList<>();
		if (raw != null) {
			for (String entry : raw.split("[,\\s]+")) {
				String trimmed = entry.trim().toLowerCase();
				if (!trimmed.isEmpty()) {
					entries.add(trimmed);
				}
			}
		}
		return entries;
	}
}
