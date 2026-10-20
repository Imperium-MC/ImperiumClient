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

package io.github.axolotlclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.AxolotlClient;
import net.minecraft.client.renderer.entity.TntRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code disable_tnt_flash} (performance category, M3): the primed TNT blink is the white
 * overlay {@code TntRenderer.submit} toggles via {@code isLit(fuseRemainingInTicks)}.
 * Forcing that check to {@code false} keeps the TNT fully visible (the block model is
 * submitted with {@code OverlayTexture.NO_OVERLAY}) while suppressing only the flash.
 */
@Mixin(TntRenderer.class)
public abstract class DisableTntFlashMixin {

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/TntRenderer;isLit(F)Z"))
	private boolean axolotlclient$disableTntFlash(float fuseRemainingInTicks, Operation<Boolean> original) {
		if (AxolotlClient.config().disableTntFlash.get()) {
			return false;
		}
		return original.call(fuseRemainingInTicks);
	}
}
