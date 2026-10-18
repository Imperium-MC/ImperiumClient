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

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.bridge.events.types.WorldLoadEvent;
import io.github.axolotlclient.modules.screenshotUtils.ScreenshotUtils;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

	@Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$getWindowTitle(CallbackInfoReturnable<String> cir) {
		if (AxolotlClient.config().customWindowTitle.get()) {
			cir.setReturnValue("AxolotlClient " + SharedConstants.getCurrentVersion().name());
		}
	}

	@Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/main/GameConfig$GameData;launchVersion:Ljava/lang/String;", opcode = Opcodes.GETFIELD))
	private String axolotlclient$noVersionType(GameConfig.GameData instance) {
		return AxolotlClientCommon.VERSION;
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void axolotlclient$onWorldLoad(ClientLevel world, CallbackInfo ci) {
		io.github.axolotlclient.bridge.events.Events.WORLD_LOAD_EVENT.invoker().accept(new WorldLoadEvent(world));
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void ready(GameConfig gameConfig, CallbackInfo ci) {
		io.github.axolotlclient.bridge.events.Events.CLIENT_READY.invoker().run();
	}

	@Inject(method = "handleGlobalKeyPress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyScreenshot:Lnet/minecraft/client/KeyMapping;", opcode = Opcodes.GETFIELD), cancellable = true)
	private void actionForScreenshotCropKey(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> cir) {
		var mapping = (KeyMapping) ScreenshotUtils.getInstance().screenshotCropBinding;
		if (mapping.matches(key)) {
			mapping.br$click();
			cir.setReturnValue(true);
		}
	}
}
