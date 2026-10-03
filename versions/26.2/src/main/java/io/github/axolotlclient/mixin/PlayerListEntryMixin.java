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

import com.mojang.authlib.GameProfile;
import io.github.axolotlclient.modules.hypixel.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {

	@Shadow
	@Final
	private GameProfile profile;

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void hideSkins(CallbackInfoReturnable<PlayerSkin> cir) {
		if (profile.equals(Minecraft.getInstance().player.getGameProfile())
			&& NickHider.getInstance().hideOwnSkin.get()) {
			cir.setReturnValue(DefaultPlayerSkin.get(profile.id()));
		} else if (!profile.equals(Minecraft.getInstance().player.getGameProfile())
			&& NickHider.getInstance().hideOtherSkins.get()) {
			cir.setReturnValue(DefaultPlayerSkin.get(profile.id()));
		}
	}
}
