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

package io.github.axolotlclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.events.Events;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

	@Inject(method = "handleSetTime", at = @At(value = "HEAD"))
	private void axolotlclient$onWorldUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
		Events.UPDATE_TIME.invoker().accept(packet.gameTime());
	}

	@WrapOperation(method = "handleSetTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;gameTime()J"))
	private long getTime(ClientboundSetTimePacket instance, Operation<Long> original) {
		if (AxolotlClient.config().timeChangerEnabled.get()) {
			instance.clockUpdates().clear();
			return AxolotlClient.config().customTime.get();
		}
		return original.call(instance);
	}

}
