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

package io.github.axolotlclient.imperium.menu

import java.util.Locale
import java.util.function.BooleanSupplier
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.bridge.util.AxoI18n

/**
 * Builds the menu's module list from what the client already registers: every HUD entry, plus every
 * config category outside the HUD tree.
 */
object MenuEntries {
	// "hud" is covered by HUD entries; "storedOptions" holds internal state, not user settings.
	private val SKIPPED_ROOTS = setOf("hud", "storedOptions")

	// Config categories that are not modules (account/session management) stay out of the module
	// grid — they have no toggle and read as broken cards. They remain under All Settings.
	private val SKIPPED_CHILDREN = setOf("auth")

	// HUD entries that read as world-rendering modules in Alpine's menu, not HUD widgets.
	private val CATEGORY_OVERRIDES = mapOf("imperiumclient:minimaphud" to MenuCategory.RENDER)

	@JvmStatic
	fun build(): MutableList<MenuEntry> {
		val entries = ArrayList<MenuEntry>()
		for (hud in HudManager.getInstance().entries) {
			val category = hud.category ?: continue
			val resolved = CATEGORY_OVERRIDES[hud.id.toString()] ?: MenuCategory.HUD
			entries.add(
				MenuEntry(
					"hud/" + hud.id, hud.name, resolved, category,
					BooleanSupplier { hud.isEnabled }, Consumer { value: Boolean ->
						hud.setEnabled(value)
						hud.onBoundsUpdate()
						AxolotlClientCommon.getInstance().saveConfig()
					}
				)
			)
		}

		val root = AxolotlClientCommon.getInstance().configManager.root
		for (top in root.subCategories) {
			if (SKIPPED_ROOTS.contains(top.name)) continue
			if (top.options.isNotEmpty()) {
				entries.add(fromCategory(top, top.name))
			}
			for (child in top.subCategories) {
				if (SKIPPED_CHILDREN.contains(child.name)) continue
				entries.add(fromCategory(child, top.name + "/" + child.name))
			}
		}

		entries.sortWith(compareBy { it.name().lowercase(Locale.ROOT) })
		return entries
	}

	private fun fromCategory(category: OptionCategory, id: String): MenuEntry {
		val toggle = findToggle(category)
		return MenuEntry(
			id, AxoI18n.translate(category.name), MenuCategory.infer(category.name), category,
			if (toggle == null) null else BooleanSupplier { toggle.get() },
			if (toggle == null) null else Consumer { value: Boolean ->
				toggle.set(value)
				AxolotlClientCommon.getInstance().saveConfig()
			}
		)
	}

	// Modules name their switch "enabled", "<module>Enabled" or after the module itself.
	private fun findToggle(category: OptionCategory): BooleanOption? {
		val name = category.name.lowercase(Locale.ROOT)
		for (option in category.options) {
			val bool = option as? BooleanOption ?: continue
			val optionName = bool.name.lowercase(Locale.ROOT)
			if (optionName == "enabled" || optionName.endsWith(".enabled") || optionName == name
				|| optionName == name + "enabled"
			) {
				return bool
			}
		}
		return null
	}
}
