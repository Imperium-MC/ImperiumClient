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
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.Items;

/**
 * Guards behind the {@code performance} category's cheap render-hide toggles (M3). Called
 * from {@code LevelExtractorMixin} at the top of {@code isEntityVisible}, so a hidden entity
 * is never extracted into a render state and costs nothing beyond the check itself.
 *
 * <p>The player's own view is never affected: the camera entity and anything the local
 * player rides (vanilla's own {@code hasIndirectPassenger} exemption) always render.
 */
public final class PerformanceHides {

	private PerformanceHides() {
	}

	/**
	 * Whether the given entity should be skipped during this frame's visibility pass,
	 * measured from the camera position ({@code camX}/{@code camZ}, the same coordinates
	 * vanilla passes to {@code isEntityVisible}).
	 */
	public static boolean shouldSkipEntity(Entity entity, double camX, double camZ) {
		if (AxolotlClient.config() == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (neverHide(entity, mc)) {
			return false;
		}
		if (AxolotlClient.config().hideLightningBolts.get() && entity instanceof LightningBolt) {
			return true;
		}
		if (AxolotlClient.config().hideHoppers.get() && isHopper(entity)) {
			return true;
		}
		if (AxolotlClient.config().hideDistantEntities.get()) {
			double limit = AxolotlClient.config().entityRenderDistance.get();
			double dx = entity.getX() - camX;
			double dz = entity.getZ() - camZ;
			if (dx * dx + dz * dz > limit * limit) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Entities that must keep rendering regardless of the toggles: whatever the camera is
	 * attached to (the player, a spectated entity) and whatever the local player is riding
	 * (mirroring vanilla's {@code hasIndirectPassenger} exemption in the same pass).
	 */
	private static boolean neverHide(Entity entity, Minecraft mc) {
		if (entity == mc.getCameraEntity()) {
			return true;
		}
		return mc.player != null && entity.hasIndirectPassenger(mc.player);
	}

	/**
	 * Hoppers as entities: dropped hopper items and hopper minecarts. Placed hopper blocks
	 * have no per-frame renderer in 26.3 (their model is baked into the chunk mesh), so the
	 * block form belongs to the deferred chunk-level work instead.
	 */
	private static boolean isHopper(Entity entity) {
		if (entity instanceof MinecartHopper) {
			return true;
		}
		return entity instanceof ItemEntity item && item.getItem().is(Items.HOPPER);
	}
}
