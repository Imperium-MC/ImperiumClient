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

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption

/**
 * Config surface of the emote wheel (W5-A). One category, nine options:
 *
 *  - `emotes` — master toggle (named after the category so the module menu's card
 *      picks it up as the switch). Default ON: the wheel is client-local until the cosmetics
 *      channel exists, so there is nothing to be cautious about yet.
 *  - `emote.1` .. `emote.8` — the eight wheel slots. Each value is a built-in id
 *      (`wave`/`salute`/`cheer`/`sit`) or a custom
 *      `id;Display Name;RRGGBB` triple; see [Emotes.parseSlot]. The defaults load
 *      the four built-ins into the first four slots so the wheel demos out of the box.
 */
object EmoteWheelConfig {

	const val SLOT_COUNT = 8

	@JvmField
	val category = OptionCategory.create("emotes")

	/** Named after the category: the menu's findToggle picks this up as the module switch. */
	@JvmField
	val emotes = BooleanOption("emotes", true)

	private val slots = buildSlots()

	private fun buildSlots(): List<StringOption> {
		val defaults = arrayOf("wave", "salute", "cheer", "sit", "", "", "", "")
		val options = ArrayList<StringOption>(SLOT_COUNT)
		for (i in 0 until SLOT_COUNT) {
			options.add(StringOption("emote." + (i + 1), defaults[i]))
		}
		return options.toList()
	}

	init {
		category.add(emotes)
		for (slot in slots) {
			category.add(slot)
		}
	}

	/** The raw slot values in wheel order (slot 1 first). */
	@JvmStatic
	fun slotValues(): MutableList<String> {
		val values = ArrayList<String>(SLOT_COUNT)
		for (slot in slots) {
			values.add(slot.get())
		}
		return values
	}

	/** Writes slot `slot` (1-based); used by tests to pin deterministic wheel state. */
	@JvmStatic
	fun setSlot(slot: Int, value: String) {
		slots[slot - 1].set(value)
	}
}
