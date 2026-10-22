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

package io.github.axolotlclient.mixin

import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.PlayerDirectionChangeEvent
import io.github.axolotlclient.modules.freelook.Freelook
import io.github.axolotlclient.modules.hypixel.Skyblock
import io.github.axolotlclient.modules.snaplook.SnapLook
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Entity::class)
abstract class EntityMixin {

    @Shadow
    abstract fun getYRot(): Float

    @Shadow
    abstract fun getXRot(): Float

    @Inject(method = ["turn"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$interceptMovement`(cursorDeltaX: Double, cursorDeltaY: Double, callback: CallbackInfo) {
        // Freelook is evaluated first: while it actively overrides the camera,
        // SnapLook defers, so the two never fight over the same rotation.
        if (Freelook.getInstance().consumeRotation(cursorDeltaX, cursorDeltaY) ||
            SnapLook.getInstance().consumeRotation() ||
            Skyblock.getInstance().rotationLocked.get()
        ) {
            callback.cancel()
        }
    }

    @Inject(method = ["turn"], at = [At("HEAD")])
    private fun `axolotlclient$updateLookDirection`(mouseDeltaX: Double, mouseDeltaY: Double, ci: CallbackInfo) {
        if (mouseDeltaX == 0.0 && mouseDeltaY == 0.0) {
            return
        }

        val prevPitch = getXRot()
        val prevYaw = getYRot()
        var pitch = prevPitch + (mouseDeltaY * 0.15).toFloat()
        var yaw = prevYaw + (mouseDeltaX * 0.15).toFloat()
        pitch = Mth.clamp(pitch, -90.0f, 90.0f)
        Events.PLAYER_DIRECTION_CHANGE.invoker().accept(PlayerDirectionChangeEvent(prevPitch, prevYaw, pitch, yaw))
    }
}
