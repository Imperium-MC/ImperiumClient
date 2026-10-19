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

package io.github.axolotlclient.imperium.media;

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;

/**
 * Config surface of the now-playing media integration (W5-B). One category, one option:
 * {@code media} — the master toggle, <strong>default OFF</strong> because it runs a background
 * poller against a local endpoint. Opting in starts {@link SpotifyLocalPoller}; opting out stops
 * it and hides the HUD. Named after the category so the module menu's card picks it up as the
 * switch, matching {@link io.github.axolotlclient.imperium.emotes.EmoteWheelConfig}.
 */
public final class MediaConfig {

	public static final OptionCategory category = OptionCategory.create("media");

	/** Default OFF: no local polling until the player asks for it. */
	public static final BooleanOption media = new BooleanOption("media", false);

	static {
		category.add(media);
	}

	private MediaConfig() {
	}
}
