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

/**
 * One emote definition (W5-A emote wheel): a stable wire id the future ImperiumMC cosmetics
 * channel will broadcast, the display name the wheel carves into its slot, and the slot accent
 * colour (ARGB). A record so slot parsing and dispatcher tests can assert equality directly.
 */
public record Emote(String id, String name, int color) {

	public Emote {
		id = id == null ? "" : id.trim().toLowerCase();
		name = name == null || name.isBlank() ? id : name.trim();
	}
}
