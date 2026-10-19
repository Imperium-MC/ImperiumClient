/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import io.github.axolotlclient.bridge.util.AxoI18n;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry;

/**
 * Active status effect count (Alpine "potion_count" module, parity spec M4): how many
 * effects are currently applied to the player. Alpine carries no own settings for
 * this beyond the shared HUD style block.
 */
public class PotionCountHud extends SimpleTextHudEntry {

	public static final AxoIdentifier ID = AxoIdentifier.of("imperiumclient", "potioncounthud");

	public PotionCountHud() {
		super(true);
	}

	@Override
	public String getValue() {
		var player = client.br$getPlayer();

		if (player == null) {
			return getPlaceholderValue();
		}

		return String.valueOf(player.br$getStatusEffects().size());
	}

	@Override
	public String getPlaceholderValue() {
		return "3";
	}

	@Override
	public String getLabel() {
		return AxoI18n.translate("potioncounthud.effects");
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}
}
