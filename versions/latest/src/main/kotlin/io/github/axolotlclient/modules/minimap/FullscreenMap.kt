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

package io.github.axolotlclient.modules.minimap

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.keybinds.KeyBinds
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

/**
 * Fullscreen map view (Alpine "minimap" large view parity): the M key opens
 * [FullscreenMapScreen], a large pannable chunk-colour map that reuses the minimap's
 * terrain sampler ([MinimapTexture], a second instance parameterized for the bigger
 * grid — the sampler itself is not duplicated) at a coarser zoom, with waypoint dots + labels
 * and the player marker.
 *
 * The keybind follows the `KeyBinds` machinery (registered under the mod's category,
 * so it appears in vanilla controls and is remappable there); it only opens the map while a
 * world is joined and no other screen is up, matching how the menu keybind behaves.
 */
class FullscreenMap private constructor() : AbstractModule() {

	/** Blocks per map texel while the fullscreen view is open (coarser than the HUD map). */
	@JvmField
	val zoom = IntegerOption("fullscreenmap.zoom", 4, 1, 16)

	/** Draw waypoint dots from the shared WaypointManager. */
	@JvmField
	val showWaypoints = BooleanOption("fullscreenmap.show_waypoints", true)

	/** Draw each visible waypoint's name (and distance) next to its dot. */
	@JvmField
	val waypointLabels = BooleanOption("fullscreenmap.waypoint_labels", true)

	@JvmField
	val enabled = BooleanOption("enabled", true)

	private val category = OptionCategory.create("fullscreenmap")

	/**
	 * The fullscreen map's own sampler instance, kept for the session (not per screen) so
	 * re-opening the map shows the previous content instantly instead of resampling from
	 * scratch, and so no NativeImage is allocated per open.
	 */
	val mapTexture = MinimapTexture()

	override fun init() {
		category.add(enabled, zoom, showWaypoints, waypointLabels)
		AxolotlClientCommon.getInstance().getConfig().addCategory(category)

		KeyBinds.getInstance().registerWithSimpleAction(
			KeyMapping("key.fullscreenmap.open", InputConstants.KEY_M, KeyBinds.CATEGORY_AXOLOTLCLIENT)
		) { open() }
	}

	companion object {
		private val instance = FullscreenMap()

		@JvmStatic
		fun getInstance(): FullscreenMap = instance

		/** Opens the fullscreen map if a world is joined and no other screen is open. */
		@JvmStatic
		fun open() {
			val client = Minecraft.getInstance()
			if (client.player == null || client.level == null || client.gui.screen() != null) {
				return
			}
			if (!getInstance().enabled.get()) {
				return
			}
			client.gui.setScreen(FullscreenMapScreen())
		}
	}
}
