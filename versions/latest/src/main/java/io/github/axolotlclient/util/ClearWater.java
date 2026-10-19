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

package io.github.axolotlclient.util;

import io.github.axolotlclient.AxolotlClient;
import net.minecraft.client.renderer.fog.FogData;

/**
 * clear_water (Alpine parity): lowers the underwater fog density by pushing the water fog
 * start/end distances out towards the render distance. Vanilla water fog is dense
 * (start -8, end ~96 blocks); with this enabled the fog only closes in near the render
 * distance edge, which makes water effectively transparent.
 *
 * <p>Applied at the tail of {@code WaterFogEnvironment.setupFog} — after the biome water-fog
 * attributes and the water-vision (respiration) shrink have been applied, before the fog
 * data is uploaded. The sky/cloud fog ends are re-synced to the new end so the horizon
 * does not stay fogged at the old distance.
 */
public class ClearWater {

	/**
	 * How far the water fog end is pushed out, as a fraction of the render distance in blocks.
	 */
	public static final float END_DISTANCE_FRACTION = 0.95F;

	/**
	 * Where the fog starts, as a fraction of the (already extended) end distance.
	 */
	public static final float START_DISTANCE_FRACTION = 0.75F;

	public static void apply(FogData data, float renderDistanceBlocks) {
		if (!AxolotlClient.config().clearWater.get()) {
			return;
		}
		data.environmentalEnd = Math.max(data.environmentalEnd, renderDistanceBlocks * END_DISTANCE_FRACTION);
		data.environmentalStart = data.environmentalEnd * START_DISTANCE_FRACTION;
		data.skyEnd = data.environmentalEnd;
		data.cloudEnd = data.environmentalEnd;
	}
}
