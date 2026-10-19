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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.bridge.Platform;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoI18n;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.hud.CoordsHud;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.util.ClientColors;

/**
 * Configurable multi-line info HUD (Alpine "info_hud" module, parity spec M2).
 *
 * <p>Alpine's info HUD is a registry of line sources the user toggles individually.
 * There is no list-shaped option type in AxolotlClientConfig, so each line source
 * (coordinates, facing, biome, fps, world time, day, session playtime) is one
 * {@link BooleanOption} — the set of enabled options is the registry. Data reading
 * reuses the existing modules' logic where it is shared code: cardinal direction via
 * {@link CoordsHud#getDirection}/{@link CoordsHud#getWordedDirection}, fps via
 * {@code AxoMinecraftClient.getCurrentFps()}, day counter and playtime formatting
 * identical to {@link DayCounterHud} and {@link PlaytimeHud}.</p>
 */
public class InfoHud extends TextHudEntry {

	public static final AxoIdentifier ID = AxoIdentifier.of(AxolotlClientCommon.MODID, "infohud");

	private static final String PLACEHOLDER_COORDS = "109 180 -5098";
	private static final String PLACEHOLDER_BIOME = "Plains";
	private static final String PLACEHOLDER_FPS = "60";
	private static final String PLACEHOLDER_TIME = "06:00";
	private static final String PLACEHOLDER_PLAYTIME = "00:07:24";

	private final BooleanOption showCoordinates = new BooleanOption("infohud.show_coordinates", true);
	private final BooleanOption showFacing = new BooleanOption("infohud.show_facing", true);
	private final BooleanOption showBiome = new BooleanOption("infohud.show_biome", false);
	private final BooleanOption showFps = new BooleanOption("infohud.show_fps", true);
	private final BooleanOption showTime = new BooleanOption("infohud.show_time", false);
	private final BooleanOption showDay = new BooleanOption("infohud.show_day", false);
	private final BooleanOption showPlaytime = new BooleanOption("infohud.show_playtime", false);
	private final ColorOption labelColor = new ColorOption("infohud.label_color", ClientColors.SELECTOR_BLUE);

	private long sessionStart = -1;

	public InfoHud() {
		super(60, 13, true);
	}

	@Override
	public void init() {
		// Same session semantics as PlaytimeHud: timer starts on world join, resets on disconnect.
		Events.WORLD_LOAD_EVENT.register(event -> sessionStart = Platform.getMeasuringTimeMs());
		Events.DISCONNECT.register(() -> sessionStart = -1);
	}

	private record InfoLine(String label, String value) {
	}

	@Override
	public void renderComponent(AxoRenderContext context, float delta) {
		drawLines(context, collectLines(true));
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext context, float delta) {
		drawLines(context, collectLines(false));
	}

	private List<InfoLine> collectLines(boolean live) {
		var player = client.br$getPlayer();
		var world = client.br$getWorld();
		boolean inGame = live && player != null && world != null;
		List<InfoLine> lines = new ArrayList<>();

		if (showCoordinates.get()) {
			String value = PLACEHOLDER_COORDS;
			if (inGame) {
				var pos = player.br$getPos();
				value = (int) Math.floor(pos.x()) + " " + (int) Math.floor(pos.y()) + " " + (int) Math.floor(pos.z());
			}
			lines.add(new InfoLine(AxoI18n.translate("infohud.line.coordinates"), value));
		}

		if (showFacing.get()) {
			String value = "N (0\u00B0)";
			if (inGame) {
				float yaw = player.br$getYaw();
				// Same convention as CoordsHud/CompassHud: bearing = (yaw + 180) % 360, N = 0.
				int bearing = Math.floorMod((int) Math.floor(yaw + 180), 360);
				value = CoordsHud.getWordedDirection(CoordsHud.getDirection(yaw + 180)) + " (" + bearing + "\u00B0)";
			}
			lines.add(new InfoLine(AxoI18n.translate("infohud.line.facing"), value));
		}

		if (showBiome.get()) {
			String value = PLACEHOLDER_BIOME;
			if (inGame) {
				value = world.br$getBiomeName(player.br$getPos());
			}
			lines.add(new InfoLine(AxoI18n.translate("infohud.line.biome"), value));
		}

		if (showFps.get()) {
			String value = PLACEHOLDER_FPS;
			if (live) {
				value = String.valueOf(AxoMinecraftClient.getCurrentFps());
			}
			lines.add(new InfoLine(AxoI18n.translate("infohud.line.fps"), value));
		}

		if (showTime.get() || showDay.get()) {
			long gameTime = inGame ? world.br$getTimeOfDay() : 0;
			if (showTime.get()) {
				// 0 ticks = 06:00 in vanilla; 1000 ticks per in-game hour.
				long ticks = Math.floorMod(gameTime, 24000);
				int hours = (int) ((ticks / 1000 + 6) % 24);
				int minutes = (int) (ticks % 1000 * 60 / 1000);
				lines.add(new InfoLine(AxoI18n.translate("infohud.line.time"),
					String.format(Locale.ROOT, "%02d:%02d", hours, minutes)));
			}
			if (showDay.get()) {
				lines.add(new InfoLine(AxoI18n.translate("infohud.line.day"),
					AxoI18n.translate("daycounterhud.days", gameTime / 24000)));
			}
		}

		if (showPlaytime.get()) {
			String value = PLACEHOLDER_PLAYTIME;
			if (sessionStart >= 0) {
				value = PlaytimeHud.TimeFormat.HH_MM_SS.formatted(Math.max(0, Platform.getMeasuringTimeMs() - sessionStart));
			}
			lines.add(new InfoLine(AxoI18n.translate("infohud.line.playtime"), value));
		}

		return lines;
	}

	private void drawLines(AxoRenderContext context, List<InfoLine> lines) {
		if (lines.isEmpty()) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
			return;
		}

		DrawPosition pos = getContentPos();
		int lineHeight = client.br$getFont().br$getFontHeight() + 1;
		int width = 0;
		int y = pos.y();
		for (InfoLine line : lines) {
			int cursor = context.br$drawString(line.label() + " ", pos.x(), y, labelColor.get(), shadow.get());
			int end = context.br$drawString(line.value(), cursor, y, textColor.get(), shadow.get());
			width = Math.max(width, end - pos.x());
			y += lineHeight;
		}
		int height = lines.size() * lineHeight - 1;

		if (width + 1 != getContentWidth() || height != getContentHeight()) {
			setContentWidth(width + 1);
			setContentHeight(height);
			onBoundsUpdate();
		}
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(showCoordinates);
		options.add(showFacing);
		options.add(showBiome);
		options.add(showFps);
		options.add(showTime);
		options.add(showDay);
		options.add(showPlaytime);
		options.add(labelColor);
		return options;
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}
}
