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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world-space hook for the explosion-box module (docs/legions-tooling.md): at the tail
 * of {@code TntRenderer.submit} the pose stack sits at the primed TNT's camera-relative
 * position, and the method only runs while the entity renders — i.e. for the whole fuse.
 * {@link ExplosionBoxes#submitBox} then draws the blast-radius cube through
 * {@code submitCustomGeometry} (the same submission route the block-outline fill uses).
 *
 * <p>Separate from {@code TntEntityRendererMixin} (TNT-time nametags) on purpose: this is
 * its own feature mixin so the two render features stay independently toggleable.</p>
 */
@Mixin(TntRenderer.class)
public abstract class ExplosionBoxRendererMixin extends EntityRenderer<PrimedTnt, TntRenderState> {

	protected ExplosionBoxRendererMixin(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "TAIL"))
	private void axolotlclient$explosionBox(TntRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
		ExplosionBoxes.getInstance().submitBox(poseStack, submitNodeCollector);
	}
}
