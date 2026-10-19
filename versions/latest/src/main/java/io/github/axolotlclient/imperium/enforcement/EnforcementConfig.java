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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;

/**
 * Config surface of the local rules enforcement layer (docs/wave4-roadmap.md W4-C). One category,
 * two options:
 *
 * <ul>
 *   <li>{@code enforcement.enabled} — master toggle, default ON. When off, the interceptor is
 *   completely inert and the client behaves like any other modded client; the server's normal
 *   anticheat applies. That is the honest fallback.</li>
 *   <li>{@code enforcement.server_allowlist} — comma-separated hostname allowlist. The layer is
 *   scoped to servers whose address matches an entry (hostname suffix match on the dot boundary).
 *   Default: exactly {@code imperiummc.net} and {@code play.imperiummc.net}.</li>
 * </ul>
 */
public final class EnforcementConfig {

	public static final OptionCategory category = OptionCategory.create("enforcement");

	public static final BooleanOption enabled = new BooleanOption("enabled", true);

	/** Default scope: the ImperiumMC addresses, exactly as the owner spec pins them. */
	public static final String DEFAULT_ALLOWLIST = "imperiummc.net,play.imperiummc.net";

	public static final StringOption serverAllowlist = new StringOption("server_allowlist", DEFAULT_ALLOWLIST);

	static {
		category.add(enabled);
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
