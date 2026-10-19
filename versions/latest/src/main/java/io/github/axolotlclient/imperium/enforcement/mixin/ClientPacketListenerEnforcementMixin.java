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

package io.github.axolotlclient.imperium.enforcement.mixin;

import io.github.axolotlclient.imperium.enforcement.EnforcementService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inbound state the movement envelope needs to stay in sync with the server's own grace windows
 * ({@code CheatPreventionListener}: teleport 2000 ms, velocity 1500 ms). These hooks only feed
 * timestamps/baselines into {@link EnforcementService} — they observe, they never modify.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEnforcementMixin {

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void imperium$onServerPositionSync(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		EnforcementService.instance().onServerPositionSync();
	}

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
	private void imperium$onEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player != null) {
			EnforcementService.instance().onEntityMotion(packet.id(), player.getId());
		}
	}
}
