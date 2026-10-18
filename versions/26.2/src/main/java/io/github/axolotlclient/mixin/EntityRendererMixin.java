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
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.api.requests.UserRequest;
import io.github.axolotlclient.modules.hypixel.LevelHead;
import io.github.axolotlclient.modules.hypixel.NickHider;
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod;
import io.github.axolotlclient.util.duck.SubmitNodeCollectorExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

	@WrapOperation(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private void axolotlclient$modifiyName(SubmitNodeCollector instance, PoseStack stack, Vec3 vec3, int offset, Component component, boolean b, int light, CameraRenderState cameraRenderState, Operation<Void> original, @Coerce Object entityState) {
		if (AxolotlClient.config() != null && entityState instanceof AvatarRenderState state) {
			var mc = Minecraft.getInstance();
			if (mc.player != null) {
				Level level = Minecraft.getInstance().level;
				if (level == null) return;
				Entity player = level.getEntity(state.id);
				if (player == null)
					return; // some mods seem to create players without adding them to a level for gui rendering. why?
				boolean self = player.getUUID() == mc.player.getUUID();
				if (self && NickHider.getInstance().hideOwnName.get()) {
					component = (Component) NickHider.getInstance().editComponent(component, player.getName().getString(), NickHider.getInstance().hiddenNameSelf.get());
				} else if (!self && NickHider.getInstance().hideOtherNames.get()) {
					component = (Component) NickHider.getInstance().editComponent(component, player.getName().getString(), NickHider.getInstance().hiddenNameOthers.get());
				}
			}
		}
		original.call(instance, stack, vec3, offset, component, b, light, cameraRenderState);
	}

	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V", ordinal = 1, shift = At.Shift.AFTER))
	public void axolotlclient$addBadges(EntityRenderState entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, int offset, CallbackInfo ci) {
		if (!entityRenderState.isDiscrete && entityRenderState instanceof AvatarRenderState state) {
			if (AxolotlClient.config().showBadges.get()) {
				Level level = Minecraft.getInstance().level;
				if (level == null) return;
				Entity entity = level.getEntity(state.id);
				if (entity instanceof Player player && UserRequest.getOnline(player.getStringUUID())) {
					((SubmitNodeCollectorExtension) submitNodeCollector).axolotlclient$submitBadge(poseStack, state.nameTagAttachment, offset, state.nameTag, !state.isDiscrete, state.lightCoords, camera);
				}
			}
		}
	}

	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void addLevel(EntityRenderState entityState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, int offset, CallbackInfo ci) {
		if (Minecraft.getInstance().getCurrentServer() != null && Minecraft.getInstance().getCurrentServer().ip.endsWith("hypixel.net") && entityState instanceof AvatarRenderState state) {
			Level level = Minecraft.getInstance().level;
			if (level == null) return;
			var entity = level.getEntity(state.id);
			if (entity instanceof Player player) {
				if (BedwarsMod.getInstance().isEnabled() && BedwarsMod.getInstance().inGame() && BedwarsMod.getInstance().bedwarsLevelHead.get()) {
					String text = BedwarsMod.getInstance().getGame().get().getLevelHead(player);
					if (text != null) {
						var y = state.showExtraEars ? -20 : -10;

						if (LevelHead.getInstance().background.get()) {
							y -= 2;
						}

						((SubmitNodeCollectorExtension) submitNodeCollector).axolotlclient$submitLevelHead(poseStack, state.nameTagAttachment, y, Component.literal(text).withStyle(s -> s.withColor(LevelHead.getInstance().textColor.get().toInt())), !state.isDiscrete, state.lightCoords, camera);
					}
				} else if (LevelHead.getInstance().enabled.get()) {
					String text = LevelHead.getInstance().getDisplayString(player.getStringUUID());

					var y = state.showExtraEars ? -20 : -10;

					if (LevelHead.getInstance().background.get()) {
						y -= 2;
					}

					((SubmitNodeCollectorExtension) submitNodeCollector).axolotlclient$submitLevelHead(poseStack, state.nameTagAttachment, y, Component.literal(text).withStyle(s -> s.withColor(LevelHead.getInstance().textColor.get().toInt())), !state.isDiscrete, state.lightCoords, camera);
				}
			}
		}
	}
}
