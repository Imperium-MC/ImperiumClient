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
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.util.PerformanceStats;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code text_render_distance} (performance category, M3): floating name tags — player names,
 * custom-named entities and armour-stand holograms — are skipped once the entity is farther
 * from the camera than the limit. The entity itself keeps rendering; only its text is
 * dropped. The distance comes straight off the render state's {@code distanceToCameraSq},
 * the same value vanilla's own visibility logic uses.
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagDistanceMixin {

	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At("HEAD"), cancellable = true)
	private void axolotlclient$skipDistantNameTag(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
												  CameraRenderState camera, int offset, CallbackInfo ci) {
		if (AxolotlClient.config() == null) {
			return;
		}
		PerformanceStats.nameTagDisplaySeen();
		int limit = AxolotlClient.config().textRenderDistance.get();
		if (limit <= 0) {
			return;
		}
		double limitSq = (double) limit * limit;
		if (state.distanceToCameraSq > limitSq) {
			PerformanceStats.nameTagSkipped();
			ci.cancel();
		}
	}
}
