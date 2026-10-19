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

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.util.ChunkBorders;
import io.github.axolotlclient.util.DrawUtil;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {

	/**
	 * chunk_borders: the frame's render-thread gizmo collector is active for the whole body
	 * of this method (opened at its head, closed by the caller after the frame renders), so
	 * emitting the chunk/region boundary lines at its tail feeds them into this frame's gizmo
	 * submission — the same rendering path the vanilla F3+G chunk border debug renderer uses.
	 */
	@Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("TAIL"))
	private void axolotlclient$chunkBorders(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
		ChunkBorders.emit();
	}

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V", ordinal = 1), index = 4)
	private int axolotlclient$customOutlineColor(int par5) {
		if (AxolotlClient.config().enableCustomOutlines.get()) {
			if (DrawUtil.shouldHideOutlineLines()) {
				// Fill mode: the outline lines become fully transparent, only the translucent
				// fill of the targeted block remains.
				return 0;
			}
			return AxolotlClient.config().outlineColor.get().toInt();
		}
		return par5;
	}

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V"), index = 5)
	private float outlineWidth(float width) {
		if (AxolotlClient.config().enableCustomOutlines.get()) {
			return width + AxolotlClient.config().outlineWidth.get() - 1f;
		}
		return width;
	}
}
