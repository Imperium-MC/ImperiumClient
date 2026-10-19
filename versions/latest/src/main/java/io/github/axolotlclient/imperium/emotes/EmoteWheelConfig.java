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

package io.github.axolotlclient.imperium.emotes;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;

/**
 * Config surface of the emote wheel (W5-A). One category, nine options:
 *
 * <ul>
 *   <li>{@code emotes} — master toggle (named after the category so the module menu's card
 *       picks it up as the switch). Default ON: the wheel is client-local until the cosmetics
 *       channel exists, so there is nothing to be cautious about yet.</li>
 *   <li>{@code emote.1} .. {@code emote.8} — the eight wheel slots. Each value is a built-in id
 *       ({@code wave}/{@code salute}/{@code cheer}/{@code sit}) or a custom
 *       {@code id;Display Name;RRGGBB} triple; see {@link Emotes#parseSlot}. The defaults load
 *       the four built-ins into the first four slots so the wheel demos out of the box.</li>
 * </ul>
 */
public final class EmoteWheelConfig {

	public static final int SLOT_COUNT = 8;

	public static final OptionCategory category = OptionCategory.create("emotes");

	/** Named after the category: the menu's findToggle picks this up as the module switch. */
	public static final BooleanOption emotes = new BooleanOption("emotes", true);

	private static final List<StringOption> slots = buildSlots();

	private static List<StringOption> buildSlots() {
		String[] defaults = {"wave", "salute", "cheer", "sit", "", "", "", ""};
		List<StringOption> options = new ArrayList<>(SLOT_COUNT);
		for (int i = 0; i < SLOT_COUNT; i++) {
			options.add(new StringOption("emote." + (i + 1), defaults[i]));
		}
		return List.copyOf(options);
	}

	static {
		category.add(emotes);
		for (StringOption slot : slots) {
			category.add(slot);
		}
	}

	private EmoteWheelConfig() {
	}

	/** The raw slot values in wheel order (slot 1 first). */
	public static List<String> slotValues() {
		List<String> values = new ArrayList<>(SLOT_COUNT);
		for (StringOption slot : slots) {
			values.add(slot.get());
		}
		return values;
	}

	/** Writes slot {@code slot} (1-based); used by tests to pin deterministic wheel state. */
	public static void setSlot(int slot, String value) {
		slots.get(slot - 1).set(value);
	}
}
