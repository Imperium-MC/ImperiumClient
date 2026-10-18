/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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
import io.github.axolotlclient.modules.hypixel.LevelHead;
import io.github.axolotlclient.util.BadgeFeatureRenderer;
import io.github.axolotlclient.util.duck.SubmitNodeCollectorExtension;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin implements SubmitNodeCollectorExtension {

	@Shadow
	@Final
	public SimpleFeatureRenderPhase nameTags;

	@Shadow
	@Final
	public TranslucentFeatureRenderPhase seeThroughNameTags;

	@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIILnet/minecraft/client/gui/Font$DisplayMode;)V"), index = 6)
	private int bgColor(int color) {
		if (AxolotlClient.config().nametagBackground.get()) {
			return color;
		} else {
			return 0;
		}
	}

	@Override
	public void axolotlclient$submitLevelHead(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera) {
		if (nameTagAttachment != null) {
			Minecraft minecraft = Minecraft.getInstance();
			poseStack.pushPose();
			poseStack.translate(nameTagAttachment.x, nameTagAttachment.y + 0.5, nameTagAttachment.z);
			poseStack.mulPose(camera.orientation);
			poseStack.scale(0.025F, -0.025F, 0.025F);
			Matrix4f pose = new Matrix4f(poseStack.last().pose());
			float x = -minecraft.font.width(name) / 2.0F;
			int backgroundColor = LevelHead.getInstance().background.get() ? ARGB.color(minecraft.gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F), -16777216) : 0;
			if (seeThrough) {
				this.nameTags
					.submit(new NameTagFeatureRenderer.Submit(pose, x, offset, name, LightCoordsUtil.lightCoordsWithEmission(lightCoords, 2), -1, 0, Font.DisplayMode.NORMAL));
				this.seeThroughNameTags
					.submit(new NameTagFeatureRenderer.Submit(pose, x, offset, name, lightCoords, -2130706433, backgroundColor, Font.DisplayMode.SEE_THROUGH));
			} else {
				this.nameTags.submit(new NameTagFeatureRenderer.Submit(pose, x, offset, name, lightCoords, -2130706433, backgroundColor, Font.DisplayMode.NORMAL));
			}

			poseStack.popPose();
		}
	}

	@Override
	public void axolotlclient$submitBadge(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera) {
		if (nameTagAttachment != null && seeThrough) {
			Minecraft mc = Minecraft.getInstance();
			poseStack.pushPose();
			poseStack.translate(nameTagAttachment.x, nameTagAttachment.y + 0.5, nameTagAttachment.z);
			poseStack.mulPose(camera.orientation);
			poseStack.scale(0.025F, -0.025F, 0.025F);
			Matrix4f pose = new Matrix4f(poseStack.last().pose());
			float nameStartX = -mc.font.width(name) / 2.0F;
			if (AxolotlClient.config().customBadge.get()) {

				var badgeText = Component.literal(AxolotlClient.config().badgeText.get());
				var x = nameStartX - (mc.font.width(badgeText) + 4);
				nameTags.submit(new NameTagFeatureRenderer.Submit(pose, x, offset, badgeText, LightCoordsUtil.lightCoordsWithEmission(lightCoords, 2), -1, 0, Font.DisplayMode.NORMAL));
			} else {
				var x = nameStartX - 10;
				((FabricOrderedSubmitNodeCollector) this).submitCustom(SubmitRenderPhases.NAME_TAGS, new BadgeFeatureRenderer.Submit(pose, x, offset));
			}
			poseStack.popPose();
		}
	}
}
