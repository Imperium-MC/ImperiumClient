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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.bridge.util.AxoI18n;

/**
 * Builds the menu's module list from what the client already registers: every HUD entry, plus every
 * config category outside the HUD tree.
 */
public final class MenuEntries {
	// "hud" is covered by HUD entries; "storedOptions" holds internal state, not user settings.
	private static final Set<String> SKIPPED_ROOTS = Set.of("hud", "storedOptions");

	private MenuEntries() {
	}

	public static List<MenuEntry> build() {
		List<MenuEntry> entries = new ArrayList<>();
		for (HudEntry hud : HudManager.getInstance().getEntries()) {
			if (hud.getCategory() == null) continue;
			entries.add(new MenuEntry("hud/" + hud.getId(), hud.getName(), MenuCategory.HUD, hud.getCategory(),
				hud::isEnabled, value -> {
				hud.setEnabled(value);
				hud.onBoundsUpdate();
				AxolotlClientCommon.getInstance().saveConfig();
			}));
		}

		OptionCategory root = AxolotlClientCommon.getInstance().getConfigManager().getRoot();
		for (OptionCategory top : root.getSubCategories()) {
			if (SKIPPED_ROOTS.contains(top.getName())) continue;
			if (!top.getOptions().isEmpty()) {
				entries.add(fromCategory(top, top.getName()));
			}
			for (OptionCategory child : top.getSubCategories()) {
				entries.add(fromCategory(child, top.getName() + "/" + child.getName()));
			}
		}

		entries.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
		return entries;
	}

	private static MenuEntry fromCategory(OptionCategory category, String id) {
		BooleanOption toggle = findToggle(category);
		return new MenuEntry(id, AxoI18n.translate(category.getName()), MenuCategory.infer(category.getName()), category,
			toggle == null ? null : toggle::get,
			toggle == null ? null : value -> {
				toggle.set(value);
				AxolotlClientCommon.getInstance().saveConfig();
			});
	}

	// Modules name their switch "enabled", "<module>Enabled" or after the module itself.
	private static BooleanOption findToggle(OptionCategory category) {
		String name = category.getName().toLowerCase(Locale.ROOT);
		for (Option<?> option : category.getOptions()) {
			if (!(option instanceof BooleanOption bool)) continue;
			String optionName = bool.getName().toLowerCase(Locale.ROOT);
			if (optionName.equals("enabled") || optionName.endsWith(".enabled") || optionName.equals(name)
				|| optionName.equals(name + "enabled")) {
				return bool;
			}
		}
		return null;
	}
}
