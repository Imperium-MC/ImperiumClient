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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.util.TimeChangerSpeed
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = ["handleSetTime"], at = [At("HEAD")])
    private fun `axolotlclient$onWorldUpdate`(packet: ClientboundSetTimePacket, ci: CallbackInfo) {
        Events.UPDATE_TIME.invoker().accept(packet.gameTime())
    }

    @WrapOperation(
        method = ["handleSetTime"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;gameTime()J")]
    )
    private fun getTime(instance: ClientboundSetTimePacket, original: Operation<Long>): Long {
        if (AxolotlClient.config().timeChangerEnabled.get()) {
            instance.clockUpdates().clear()
            if (AxolotlClient.config().timeMultiplier.get() != 1.0f) {
                // Time multiplier: let the real time flow, shifted by the extra ticks accumulated client-side.
                return original.call(instance) + TimeChangerSpeed.offset()
            }
            return AxolotlClient.config().customTime.get().toLong()
        }
        return original.call(instance)
    }
}
