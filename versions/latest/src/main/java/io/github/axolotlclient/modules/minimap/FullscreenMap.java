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

package io.github.axolotlclient.modules.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.keybinds.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Fullscreen map view (Alpine "minimap" large view parity): the M key opens
 * {@link FullscreenMapScreen}, a large pannable chunk-colour map that reuses the minimap's
 * terrain sampler ({@link MinimapTexture}, a second instance parameterized for the bigger
 * grid — the sampler itself is not duplicated) at a coarser zoom, with waypoint dots + labels
 * and the player marker.
 *
 * <p>The keybind follows the {@code KeyBinds} machinery (registered under the mod's category,
 * so it appears in vanilla controls and is remappable there); it only opens the map while a
 * world is joined and no other screen is up, matching how the menu keybind behaves.</p>
 */
public class FullscreenMap extends AbstractModule {

	private static final FullscreenMap instance = new FullscreenMap();

	/** Blocks per map texel while the fullscreen view is open (coarser than the HUD map). */
	public final IntegerOption zoom = new IntegerOption("fullscreenmap.zoom", 4, 1, 16);
	/** Draw waypoint dots from the shared WaypointManager. */
	public final BooleanOption showWaypoints = new BooleanOption("fullscreenmap.show_waypoints", true);
	/** Draw each visible waypoint's name (and distance) next to its dot. */
	public final BooleanOption waypointLabels = new BooleanOption("fullscreenmap.waypoint_labels", true);
	public final BooleanOption enabled = new BooleanOption("enabled", true);

	private final OptionCategory category = OptionCategory.create("fullscreenmap");

	/**
	 * The fullscreen map's own sampler instance, kept for the session (not per screen) so
	 * re-opening the map shows the previous content instantly instead of resampling from
	 * scratch, and so no NativeImage is allocated per open.
	 */
	private final MinimapTexture texture = new MinimapTexture();

	private FullscreenMap() {
	}

	public static FullscreenMap getInstance() {
		return instance;
	}

	public MinimapTexture getMapTexture() {
		return texture;
	}

	@Override
	public void init() {
		category.add(enabled, zoom, showWaypoints, waypointLabels);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);

		KeyBinds.getInstance().registerWithSimpleAction(
			new KeyMapping("key.fullscreenmap.open", InputConstants.KEY_M, KeyBinds.CATEGORY_AXOLOTLCLIENT),
			FullscreenMap::open);
	}

	/** Opens the fullscreen map if a world is joined and no other screen is open. */
	public static void open() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gui.screen() != null) {
			return;
		}
		if (!getInstance().enabled.get()) {
			return;
		}
		client.gui.setScreen(new FullscreenMapScreen());
	}
}
