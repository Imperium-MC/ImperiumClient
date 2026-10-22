/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.bridge.util

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.internal.RequiresImpl

interface AxoIdentifier {
	@RequiresImpl
	fun `br$getPath`(): String {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getNamespace`(): String {
		throw BridgeUtil.noImpl()
	}

	fun `br$getAsFriendlyString`(): String {
		var path = `br$getPath`()
		val namespace = `br$getNamespace`()
		if (namespace != "minecraft") {
			path += " (" + Character.toTitleCase(namespace[0]) + namespace.substring(1) + ")"
		}
		val str = path.replace("_", " ")
		if (str.isEmpty()) {
			return str
		}

		val codepoints = str.codePoints().toArray()
		var capitalizeNext = true
		for (i in codepoints.indices) {
			val ch = codepoints[i]
			if (Character.isWhitespace(ch)) {
				capitalizeNext = true
			} else if (capitalizeNext) {
				codepoints[i] = Character.toTitleCase(ch)
				capitalizeNext = false
			}
		}
		return String(codepoints, 0, codepoints.size)
	}

	companion object {
		@JvmStatic
		fun of(ns: String, path: String): AxoIdentifier = PlatformImplInternal.createIdentifier(ns, path)

		@JvmStatic
		fun of(path: String): AxoIdentifier = of("minecraft", path)

		@JvmStatic
		fun parse(id: String): AxoIdentifier = PlatformImplInternal.parseIdentifier(id)
	}
}
