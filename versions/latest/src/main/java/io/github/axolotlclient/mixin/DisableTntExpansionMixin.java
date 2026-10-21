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
import io.github.axolotlclient.util.PerformanceStats;
import net.minecraft.client.renderer.entity.TntRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code disable_tnt_expansion} (performance category, M3): during the last second of the
 * fuse, {@code TntRenderer.submit} scales the primed TNT by {@code 1 + getSwellAmount(fuse)},
 * making it pulse and grow before the explosion. Forcing the swell amount to zero keeps the
 * TNT at its normal size (cannon-minimal rendering); the entity stays fully visible and the
 * {@code disable_tnt_flash} blink toggle composes independently.
 */
@Mixin(TntRenderer.class)
public abstract class DisableTntExpansionMixin {

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/TntRenderer;getSwellAmount(F)F"))
	private float axolotlclient$disableTntExpansion(float fuseRemainingInTicks, Operation<Float> original) {
		if (AxolotlClient.config().disableTntExpansion.get()) {
			PerformanceStats.tntSwellSuppressed();
			return 0.0F;
		}
		return original.call(fuseRemainingInTicks);
	}
}
