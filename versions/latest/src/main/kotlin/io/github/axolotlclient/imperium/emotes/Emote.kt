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

package io.github.axolotlclient.imperium.emotes

/**
 * One emote definition (W5-A emote wheel): a stable wire id the future ImperiumMC cosmetics
 * channel will broadcast, the display name the wheel carves into its slot, and the slot accent
 * colour (ARGB). Equality compares id, name and colour so slot parsing and dispatcher tests can
 * assert against built-ins directly.
 */
class Emote(id: String?, name: String?, color: Int) {

	private val id: String = (id ?: "").trim().lowercase()
	private val name: String = if (name == null || name.isBlank()) this.id else name.trim()
	private val color: Int = color

	fun id(): String = id

	fun name(): String = name

	fun color(): Int = color

	override fun equals(other: Any?): Boolean =
		this === other || (other is Emote && id == other.id && name == other.name && color == other.color)

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + name.hashCode()
		result = 31 * result + color
		return result
	}

	override fun toString(): String = "Emote[id=$id, name=$name, color=" + Integer.toHexString(color) + "]"
}
