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

package io.github.axolotlclient.modules.hud.gui.hud;

import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.PlayerDirectionChangeEvent;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.util.PlayerHudEntityRenderState;
import io.github.axolotlclient.modules.hud.util.PlayerHudEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class PlayerHud extends PlayerHudCommon {

	private LivingEntityRenderState reusedPlayerRendererState = null;
	private PlayerHudEntityRenderer renderer;

	public PlayerHud() {
		super();
		Events.PLAYER_DIRECTION_CHANGE.register(this::onPlayerDirectionChange);
	}

	public void onPlayerDirectionChange(PlayerDirectionChangeEvent event) {
		yawOffset += (event.yaw() - event.prevYaw()) / 2;
	}

	@Override
	public void tick() {
		super.tick();
		var client = Minecraft.getInstance();
		if (client.player != null && client.player.isVisuallySwimming()) {
			float rawPitch = client.player.isInWater() ? client.player.getXRot() : 0.0F;
			float pitch = Mth.lerp(client.player.getSwimAmount(1), 0.0F, rawPitch);
			float height = client.player.getBbHeight();
			// sin = opposite / hypotenuse
			float offset = (float) (Math.sin(Math.toRadians(pitch)) * height) * 20;
			yOffset = -(offset);
			if (pitch > 0) {
				yOffset -= (float) (((1 / (1 + Math.exp(pitch / 4))) - .5) * 40);
			}
		} else if (client.player != null && client.player.isFallFlying()) {
			// Elytra!

			float j = (float) client.player.getFallFlyingTicks() + 1;
			float k = Mth.clamp(j * j / 100.0F, 0.0F, 1.0F);

			float pitch = k * (-90.0F - client.player.getXRot()) + 90;
			float height = client.player.getBbHeight();
			// sin = opposite / hypotenuse
			float offset = (float) (Math.sin(Math.toRadians(pitch)) * height) * 50;
			yOffset = -offset;
			if (pitch < 0) {
				yOffset -= (float) (((1 / (1 + Math.exp(-pitch / 4))) - .5) * 40);
			}
		} else {
			yOffset *= .8f;
		}
	}

	@Override
	protected void renderPlayer(AxoRenderContext ctx, boolean placeholder, double x, double y, float delta) {
		var client = Minecraft.getInstance();
		var graphics = (GuiGraphicsExtractor) ctx;
		if (client.player == null) {
			return;
		}

		float lerpY = (lastYOffset + ((yOffset - lastYOffset) * delta));

		float scale = getScale() * 40;

		Quaternionf quaternion = new Quaternionf().rotateZ((float) Math.PI);

		// Rotate to whatever is wanted. Also make sure to offset the yaw
		float deltaYaw = client.player.getYHeadRot();
		if (dynamicRotation.get()) {
			deltaYaw -= (lastYawOffset + ((yawOffset - lastYawOffset) * delta));
		}

		Quaternionf quaternionf2 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0, 1, 0), deltaYaw - 180 + rotation.get().floatValue());
		quaternion.mul(quaternionf2);

		renderEntityInInventory(graphics,
			(int) (x),
			(int) (y),
			(int) (x + getContentWidth() * getScale()),
			(int) (y + getContentHeight() * getScale()),
			scale / client.player.getScale(),
			new Vector3f(0, (client.player.getBbHeight() / 2f) - lerpY / 40, 0),
			quaternion,
			quaternionf2,
			client.player,
			delta);
	}

	@SuppressWarnings("unchecked")
	private void renderEntityInInventory(
		GuiGraphicsExtractor guiGraphicsExtractor,
		int i,
		int j,
		int k,
		int l,
		float f,
		Vector3f vector3f,
		Quaternionf quaternionf,
		@Nullable Quaternionf quaternionf2,
		LivingEntity livingEntity,
		float delta
	) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderDispatcher entityRenderDispatcher = mc.getEntityRenderDispatcher();
		if (renderer == null)
			renderer = new PlayerHudEntityRenderer(entityRenderDispatcher);
		EntityRenderer<@NotNull LivingEntity, @NotNull LivingEntityRenderState> entityRenderer = (EntityRenderer<@NotNull LivingEntity, @NotNull LivingEntityRenderState>) entityRenderDispatcher.getRenderer(livingEntity);
		if (reusedPlayerRendererState == null) {
			reusedPlayerRendererState = entityRenderer.createRenderState();
		}
		entityRenderer.extractRenderState(livingEntity, reusedPlayerRendererState, delta);
		reusedPlayerRendererState.nameTag = null;
		guiGraphicsExtractor.guiRenderState.addPicturesInPictureState(new PlayerHudEntityRenderState(reusedPlayerRendererState, vector3f, quaternionf, quaternionf2, i, j, k, l, f, guiGraphicsExtractor.scissorStack.peek(), renderer));
	}

	@Override
	protected boolean isPerformingAction() {
		// inspired by tr7zw's mod
		LocalPlayer player = Minecraft.getInstance().player;
		//noinspection DataFlowIssue
		return player.isCrouching() || player.isSprinting() || player.isFallFlying() || player.getAbilities().flying ||
			player.isUnderWater() || player.isVisuallySwimming() || player.isPassenger() || player.isUsingItem() ||
			player.isHandsBusy() || player.hurtTime > 0 || player.isOnFire();
	}
}
