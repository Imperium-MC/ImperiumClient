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

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.axolotlclient.modules.legions.ExplosionBoxes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creeper half of the explosion-box module (Alpine {@code factions/explosion_boxes}
 * wave 3): {@code CreeperRenderer} does not override {@code submit}, so the hook sits on
 * the shared {@code LivingEntityRenderer.submit} and filters by render-state type. At the
 * HEAD the pose stack still carries only the dispatcher's translation (the entity's
 * camera-relative position), and the state's x/y/z are the interpolated world position —
 * exactly what {@link ExplosionBoxes#submitCreeperBox} needs for the fill quad and the
 * linger tracker.
 *
 * <p>The instanceof guard makes the per-entity cost one reference check for everything
 * that is not a creeper; the actual option checks live in the module.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CreeperBoxRendererMixin {

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "HEAD"))
	private void axolotlclient$creeperExplosionBox(LivingEntityRenderState state, PoseStack poseStack,
												   SubmitNodeCollector submitNodeCollector, CameraRenderState camera,
												   CallbackInfo ci) {
		if (state instanceof CreeperRenderState creeper) {
			ExplosionBoxes.getInstance().submitCreeperBox(creeper, poseStack, submitNodeCollector);
		}
	}
}
