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

package io.github.axolotlclient.imperium.menu;

import java.util.Locale;

public enum MenuCategory {
	ALL, HUD, RENDER, CHAT, WORLD, UTILITY, SERVER;

	public String translationKey() {
		return "imperium.menu.category." + name().toLowerCase(Locale.ROOT);
	}

	// Config categories carry no type, so infer one from the category name.
	public static MenuCategory infer(String categoryName) {
		String n = categoryName.toLowerCase(Locale.ROOT);
		if (containsAny(n, "hypixel", "bedwars", "skyblock", "levelhead", "autogg", "autotip", "autoboop", "nick", "mcci")) {
			return SERVER;
		}
		if (containsAny(n, "chat")) {
			return CHAT;
		}
		if (containsAny(n, "time", "weather", "tnt", "rain", "sky", "world")) {
			return WORLD;
		}
		if (containsAny(n, "zoom", "freelook", "snaplook", "blur", "beam", "particle", "outline", "nametag", "render", "fov", "fire", "shield", "hit", "bright", "crosshair")) {
			return RENDER;
		}
		return UTILITY;
	}

	private static boolean containsAny(String s, String... needles) {
		for (String needle : needles) {
			if (s.contains(needle)) return true;
		}
		return false;
	}
}
