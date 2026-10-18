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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import org.jetbrains.annotations.Nullable;

/**
 * One card in the Imperium menu. {@code isEnabled}/{@code setEnabled} are null for entries that
 * have settings but no on/off state (for example the general options page).
 */
public record MenuEntry(String id, String name, MenuCategory category, OptionCategory settings,
						@Nullable BooleanSupplier isEnabled, @Nullable Consumer<Boolean> setEnabled) {

	public boolean hasToggle() {
		return isEnabled != null && setEnabled != null;
	}

	public boolean enabled() {
		return hasToggle() && isEnabled.getAsBoolean();
	}

	public void toggle() {
		if (hasToggle()) setEnabled.accept(!isEnabled.getAsBoolean());
	}

	public boolean matches(String query) {
		if (query.isBlank()) return true;
		String q = query.toLowerCase(Locale.ROOT).trim();
		return name.toLowerCase(Locale.ROOT).contains(q) || id.toLowerCase(Locale.ROOT).contains(q);
	}
}
