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

package io.github.axolotlclient.imperium.enforcement

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption

/**
 * Config surface of the local rules enforcement layer (docs/wave4-roadmap.md W4-C). One category,
 * two options:
 *
 *  Enforcement is server policy, never a player switch (review #15): there is no enabled
 *  completely inert and the client behaves like any other modded client; the server's normal
 *  anticheat applies. That is the honest fallback.
 *  - `enforcement.server_allowlist` — comma-separated hostname allowlist. The layer is
 *  scoped to servers whose address matches an entry (hostname suffix match on the dot boundary).
 *  Default: exactly `imperiummc.net` and `play.imperiummc.net`.
 */
object EnforcementConfig {

	@JvmField
	val category = OptionCategory.create("enforcement")

	/** Default scope: the ImperiumMC addresses, exactly as the owner spec pins them. */
	const val DEFAULT_ALLOWLIST = "imperiummc.net,play.imperiummc.net"

	@JvmField
	val serverAllowlist = StringOption("server_allowlist", DEFAULT_ALLOWLIST)

	init {
		category.add(serverAllowlist)
	}

	/** Parses the allowlist option into trimmed, lowercase entries. */
	@JvmStatic
	fun allowlist(): MutableList<String> {
		val raw = serverAllowlist.get()
		val entries = ArrayList<String>()
		if (raw != null) {
			for (entry in raw.split(Regex("[,\\s]+"))) {
				val trimmed = entry.trim().lowercase()
				if (trimmed.isNotEmpty()) {
					entries.add(trimmed)
				}
			}
		}
		return entries
	}
}
