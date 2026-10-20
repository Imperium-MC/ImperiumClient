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

import java.util.Locale
import java.util.Optional

import io.github.axolotlclient.imperium.menu.CarvedTheme

/**
 * Built-in emote definitions and slot parsing for the W5-A emote wheel — all pure, no client
 * state, so the wheel's config surface is unit-testable headless.
 *
 * A wheel slot config value (`emotes.emote.<n>`) is one of:
 *  - **blank** — the slot is empty and renders nothing;
 *  - **a built-in id** — `wave`, `salute`, `cheer` or
 *    `sit`, resolving to that definition;
 *  - **a custom triple** — `id;Display Name;RRGGBB`, so players can fill
 *    the wheel with emote ids the server cosmetics channel will later define.
 */
object Emotes {

	/** The four built-in emotes (carved ramp colours, one per corner of the wheel demo). */
	@JvmField
	val WAVE = Emote("wave", "Wave", CarvedTheme.BRONZE)

	@JvmField
	val SALUTE = Emote("salute", "Salute", CarvedTheme.BRONZE_LIGHT)

	@JvmField
	val CHEER = Emote("cheer", "Cheer", CarvedTheme.MARBLE)

	@JvmField
	val SIT = Emote("sit", "Sit", CarvedTheme.MUTED)

	private val BUILT_INS = builtInMap()

	private fun builtInMap(): Map<String, Emote> {
		val map = LinkedHashMap<String, Emote>()
		for (emote in listOf(WAVE, SALUTE, CHEER, SIT)) {
			map.put(emote.id(), emote)
		}
		return map
	}

	@JvmStatic
	fun builtIns(): List<Emote> = listOf(WAVE, SALUTE, CHEER, SIT)

	@JvmStatic
	fun builtIn(id: String?): Optional<Emote> =
		Optional.ofNullable(BUILT_INS[id?.trim()?.lowercase(Locale.ROOT) ?: ""])

	/**
	 * Parses one slot value; empty when the slot is blank or the value is too malformed to use.
	 */
	@JvmStatic
	fun parseSlot(value: String?): Optional<Emote> {
		if (value == null || value.isBlank()) {
			return Optional.empty()
		}
		val trimmed = value.trim()
		val builtIn = builtIn(trimmed)
		if (builtIn.isPresent) {
			return builtIn
		}
		val parts = trimmed.split(";", limit = 3)
		if (parts.size < 2 || parts[0].isBlank()) {
			return Optional.empty() // neither a built-in id nor "id;name" — not usable
		}
		return Optional.of(Emote(parts[0], parts[1], if (parts.size > 2) parseColor(parts[2]) else parseColor(null)))
	}

	/** Parses the whole wheel, keeping slot order and dropping empty/unusable slots. */
	@JvmStatic
	fun parseSlots(values: List<String>): List<Emote> = values.stream()
		.map { value: String -> parseSlot(value) }
		.flatMap { optional: Optional<Emote> -> optional.stream() }
		.toList()

	/**
	 * Accepts `RRGGBB` or `#RRGGBB` (case-insensitive); falls back to the bronze
	 * brand colour so a typo never renders an invisible slot.
	 */
	@JvmStatic
	fun parseColor(raw: String?): Int {
		if (raw == null || raw.isBlank()) {
			return CarvedTheme.BRONZE
		}
		val trimmed = raw.trim()
		val hex = if (trimmed[0] == '#') trimmed.substring(1) else trimmed
		if (hex.length != 6 || !hex.matches(Regex("[0-9a-fA-F]{6}"))) {
			return CarvedTheme.BRONZE
		}
		return 0xFF000000.toInt() or hex.toInt(16)
	}
}
