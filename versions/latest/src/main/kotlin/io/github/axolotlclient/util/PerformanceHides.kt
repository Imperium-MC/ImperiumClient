/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClient
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper
import net.minecraft.world.item.Items

/**
 * Guards behind the `performance` category's cheap render-hide toggles (M3). Called
 * from `LevelExtractorMixin` at the top of `isEntityVisible`, so a hidden entity
 * is never extracted into a render state and costs nothing beyond the check itself.
 *
 * The player's own view is never affected: the camera entity and anything the local
 * player rides (vanilla's own `hasIndirectPassenger` exemption) always render.
 */
object PerformanceHides {

    /**
     * Whether the given entity should be skipped during this frame's visibility pass,
     * measured from the camera position (`camX`/`camZ`, the same coordinates
     * vanilla passes to `isEntityVisible`).
     */
    @JvmStatic
    fun shouldSkipEntity(entity: Entity, camX: Double, camZ: Double): Boolean {
        if (AxolotlClient.config() == null) {
            return false
        }
        val mc = Minecraft.getInstance()
        if (neverHide(entity, mc)) {
            return false
        }
        if (AxolotlClient.config().hideLightningBolts.get() && entity is LightningBolt) {
            return true
        }
        if (AxolotlClient.config().hideHoppers.get() && isHopper(entity)) {
            return true
        }
        if (AxolotlClient.config().hideDistantEntities.get()) {
            val limit = AxolotlClient.config().entityRenderDistance.get()
            val dx = entity.x - camX
            val dz = entity.z - camZ
            if (dx * dx + dz * dz > limit * limit) {
                return true
            }
        }
        return false
    }

    /**
     * Entities that must keep rendering regardless of the toggles: whatever the camera is
     * attached to (the player, a spectated entity) and whatever the local player is riding
     * (mirroring vanilla's `hasIndirectPassenger` exemption in the same pass).
     */
    private fun neverHide(entity: Entity, mc: Minecraft): Boolean {
        if (entity === mc.cameraEntity) {
            return true
        }
        val player = mc.player ?: return false
        return entity.hasIndirectPassenger(player)
    }

    /**
     * Hoppers as entities: dropped hopper items and hopper minecarts. Placed hopper blocks
     * have no per-frame renderer in 26.3 (their model is baked into the chunk mesh), so the
     * block form belongs to the deferred chunk-level work instead.
     */
    private fun isHopper(entity: Entity): Boolean {
        if (entity is MinecartHopper) {
            return true
        }
        return entity is ItemEntity && (entity as ItemEntity).item.`is`(Items.HOPPER)
    }
}
