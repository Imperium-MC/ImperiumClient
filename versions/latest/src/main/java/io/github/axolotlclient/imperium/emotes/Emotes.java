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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.axolotlclient.imperium.menu.CarvedTheme;

/**
 * Built-in emote definitions and slot parsing for the W5-A emote wheel — all pure, no client
 * state, so the wheel's config surface is unit-testable headless.
 *
 * <p>A wheel slot config value ({@code emotes.emote.<n>}) is one of:</p>
 * <ul>
 *   <li><strong>blank</strong> — the slot is empty and renders nothing;</li>
 *   <li><strong>a built-in id</strong> — {@code wave}, {@code salute}, {@code cheer} or
 *       {@code sit}, resolving to that definition;</li>
 *   <li><strong>a custom triple</strong> — {@code id;Display Name;RRGGBB}, so players can fill
 *       the wheel with emote ids the server cosmetics channel will later define.</li>
 * </ul>
 */
public final class Emotes {

	/** The four built-in emotes (carved ramp colours, one per corner of the wheel demo). */
	public static final Emote WAVE = new Emote("wave", "Wave", CarvedTheme.BRONZE);
	public static final Emote SALUTE = new Emote("salute", "Salute", CarvedTheme.BRONZE_LIGHT);
	public static final Emote CHEER = new Emote("cheer", "Cheer", CarvedTheme.MARBLE);
	public static final Emote SIT = new Emote("sit", "Sit", CarvedTheme.MUTED);

	private static final Map<String, Emote> BUILT_INS = builtInMap();

	private static Map<String, Emote> builtInMap() {
		Map<String, Emote> map = new LinkedHashMap<>();
		for (Emote emote : List.of(WAVE, SALUTE, CHEER, SIT)) {
			map.put(emote.id(), emote);
		}
		return Map.copyOf(map);
	}

	private Emotes() {
	}

	public static List<Emote> builtIns() {
		return List.of(WAVE, SALUTE, CHEER, SIT);
	}

	public static Optional<Emote> builtIn(String id) {
		return Optional.ofNullable(BUILT_INS.get(id == null ? "" : id.trim().toLowerCase(Locale.ROOT)));
	}

	/**
	 * Parses one slot value; empty when the slot is blank or the value is too malformed to use.
	 */
	public static Optional<Emote> parseSlot(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		Optional<Emote> builtIn = builtIn(trimmed);
		if (builtIn.isPresent()) {
			return builtIn;
		}
		String[] parts = trimmed.split(";", 3);
		if (parts.length < 2 || parts[0].isBlank()) {
			return Optional.empty(); // neither a built-in id nor "id;name" — not usable
		}
		return Optional.of(new Emote(parts[0], parts[1], parseColor(parts.length > 2 ? parts[2] : null)));
	}

	/** Parses the whole wheel, keeping slot order and dropping empty/unusable slots. */
	public static List<Emote> parseSlots(List<String> values) {
		return values.stream()
			.map(Emotes::parseSlot)
			.flatMap(Optional::stream)
			.toList();
	}

	/**
	 * Accepts {@code RRGGBB} or {@code #RRGGBB} (case-insensitive); falls back to the bronze
	 * brand colour so a typo never renders an invisible slot.
	 */
	public static int parseColor(String raw) {
		if (raw == null || raw.isBlank()) {
			return CarvedTheme.BRONZE;
		}
		String hex = raw.trim().charAt(0) == '#' ? raw.trim().substring(1) : raw.trim();
		if (hex.length() != 6 || !hex.matches("[0-9a-fA-F]{6}")) {
			return CarvedTheme.BRONZE;
		}
		return 0xFF000000 | Integer.parseInt(hex, 16);
	}
}
