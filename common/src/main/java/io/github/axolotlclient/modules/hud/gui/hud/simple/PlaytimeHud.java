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

import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.bridge.Platform;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.util.AxoI18n;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry;

/**
 * Session playtime (Alpine "playtime" module, parity spec M4): wall-clock time spent
 * in the current world session, formatted HH:MM:SS by default.
 *
 * <p>The timer starts when a world is joined ({@code Events.WORLD_LOAD_EVENT}, which
 * fires from {@code Minecraft.setLevel}, so singleplayer world switches re-start it)
 * and resets on disconnect — reconnecting or loading another world begins a fresh
 * count, matching Alpine's per-session semantics.</p>
 */
public class PlaytimeHud extends SimpleTextHudEntry {

	public static final AxoIdentifier ID = AxoIdentifier.of("imperiumclient", "playtimehud");

	private final EnumOption<TimeFormat> format = new EnumOption("format", TimeFormat.class, TimeFormat.HH_MM_SS);

	private long sessionStart = -1;

	public PlaytimeHud() {
		super(true);
	}

	@Override
	public void init() {
		Events.WORLD_LOAD_EVENT.register(event -> sessionStart = Platform.getMeasuringTimeMs());
		Events.DISCONNECT.register(() -> sessionStart = -1);
	}

	@Override
	public String getValue() {
		if (client.br$getWorld() == null || sessionStart < 0) {
			return getPlaceholderValue();
		}

		return format.get().formatted(Math.max(0, Platform.getMeasuringTimeMs() - sessionStart));
	}

	@Override
	public String getPlaceholderValue() {
		return format.get().placeholder();
	}

	@Override
	public String getLabel() {
		return AxoI18n.translate("playtimehud.playtime");
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(format);
		return options;
	}

	public enum TimeFormat {
		HH_MM_SS(true, "00:07:24"),
		H_MM_SS(false, "0:07:24"),
		MM_SS(false, "07:24");

		private final boolean padHours;
		private final String placeholder;

		TimeFormat(boolean padHours, String placeholder) {
			this.padHours = padHours;
			this.placeholder = placeholder;
		}

		public String placeholder() {
			return placeholder;
		}

		public String formatted(long millis) {
			long totalSeconds = millis / 1000;
			long hours = totalSeconds / 3600;
			long minutes = (totalSeconds % 3600) / 60;
			long seconds = totalSeconds % 60;
			if (this == MM_SS) {
				return String.format(Locale.ROOT, "%02d:%02d", hours * 60 + minutes, seconds);
			}
			return String.format(Locale.ROOT, (padHours ? "%02d" : "%d") + ":%02d:%02d", hours, minutes, seconds);
		}

		@Override
		public String toString() {
			return "playtimehud.format." + name().toLowerCase(Locale.ROOT);
		}
	}
}
