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
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.modules.blur.MotionBlur;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.CrosshairHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.Profiler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;


	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;postEffectId:Lnet/minecraft/resources/Identifier;", ordinal = 0, opcode = Opcodes.GETFIELD))
	public void axolotlclient$worldMotionBlur(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
		axolotlclient$motionBlur(tracker, renderLevel, null);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V"))
	public void axolotlclient$motionBlur(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
		if (ci != null && !MotionBlur.getInstance().inGuis.get()) {
			return;
		}

		Profiler.get().push("Motion Blur");

		if (MotionBlur.getInstance().enabled.get() && Minecraft.getInstance().isGameLoadFinished()) {
			MotionBlur blur = MotionBlur.getInstance();
			blur.render(resourcePool);
		}

		Profiler.get().pop();
	}

	@Inject(method = "bobView",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void axolotlclient$minimalViewBob(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci, @Local(name = "bob") LocalFloatRef bob,
	                                          @Local(name = "backwardsInterpolatedWalkDistance") LocalFloatRef backwardsInterpolatedWalkDistance) {
		if (AxolotlClient.config().minimalViewBob.get()) {
			backwardsInterpolatedWalkDistance.set(backwardsInterpolatedWalkDistance.get() / 2f);
			bob.set(bob.get() / 2);
		}
	}

	@Inject(method = "bobHurt", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;hurtDuration:I", opcode = Opcodes.GETFIELD),
		cancellable = true)
	private void axolotlclient$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (AxolotlClient.config().noHurtCam.get()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void renderDirectionCrosshair(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!this.minecraft.gui.hud.isHidden()) {
			var hud = (CrosshairHud) HudManager.getInstance().get(CrosshairHud.ID);
			if (hud.isEnabled()) {
				hud.renderDirectionCrosshair();
			}
		}
	}

	@WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DebugCrosshairRenderer;render(Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"))
	private void checkCustomCrosshair(DebugCrosshairRenderer instance, CameraRenderState cameraState, int guiScale, Operation<Void> original) {
		var hud = (CrosshairHud) HudManager.getInstance().get(CrosshairHud.ID);
		if (!hud.isEnabled() || !hud.overridesF3()) {
			original.call(instance, cameraState, guiScale);
		}
	}
}
