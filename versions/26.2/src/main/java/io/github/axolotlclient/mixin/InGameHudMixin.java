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

import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.PotionsHud;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.*;
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void onHudRender(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!(AxoMinecraftClient.getInstance().br$getScreen() instanceof HudEditScreen)) {
			HudManager.getInstance().render(guiGraphicsExtractor, deltaTracker.getGameTimeDeltaTicks());
		}
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	public void axolotlclient$renderStatusEffect(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		PotionsHud hud = (PotionsHud) HudManager.getInstance().get(PotionsHud.ID);
		if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	public void axolotlclient$renderCrosshair(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		CrosshairHud hud = (CrosshairHud) HudManager.getInstance().get(CrosshairHud.ID);
		if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
			if (minecraft.gui.hud.getDebugOverlay().showDebugScreen() && !hud.overridesF3()) {
				return;
			}
			hud.renderCrosshair(graphics);
			ci.cancel();
		}
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	public void axolotlclient$renderScoreboard(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		ScoreboardHud hud = (ScoreboardHud) HudManager.getInstance().get(ScoreboardHud.ID);
		ScoreboardRenderEvent event = new ScoreboardRenderEvent(objective);
		Events.SCOREBOARD_RENDER_EVENT.invoker().accept(event);
		if (event.isCancelled() || hud.isEnabled()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
	public void axolotlclient$getActionBar(GuiGraphicsExtractor instance, Font font, Component text, int x, int y, int width, int color, Operation<Integer> original) {
		ActionBarHud hud = (ActionBarHud) HudManager.getInstance().get(ActionBarHud.ID);
		if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
			instance.pose().popMatrix();
			hud.render(instance, text, color);
			instance.pose().pushMatrix();
		} else {
			original.call(instance, font, text, x, y, width, color);
		}
	}

	@WrapOperation(method = "extractHearts", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V"))
	public void axolotlclient$displayHardcoreHearts(Hud instance, GuiGraphicsExtractor graphics, Hud.HeartType type, int x, int y, boolean hardcore, boolean blinking, boolean half, Operation<Void> original) {
		//noinspection OptionalGetWithoutIsPresent
		boolean hardcoreMod = BedwarsMod.getInstance().isEnabled() && BedwarsMod.getInstance().inGame() &&
			BedwarsMod.getInstance().hardcoreHearts.get() &&
			!BedwarsMod.getInstance().getGame().get().getSelf().isBed();
		original.call(instance, graphics, type, x, y, hardcoreMod || hardcore, blinking, half);
	}

	@Expression("? == 0")
	@ModifyExpressionValue(method = "extractPlayerHealth", at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 1))
	public boolean axolotlclient$dontHunger(boolean original) {
		if (original && BedwarsMod.getInstance().isEnabled() &&
			BedwarsMod.getInstance().inGame() &&
			!BedwarsMod.getInstance().showHunger.get()) {
			return false;
		}
		return original;
	}

	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$removeVignette(GuiGraphicsExtractor graphics, Entity entity, CallbackInfo ci) {
		if (AxolotlClient.config().removeVignette.get()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIII)V"))
	private void axolotlclient$dontShowArmor(GuiGraphicsExtractor graphics, Player player, int y, int uncappedMaxHealth, int cappedMaxHealth, int x, Operation<Void> original) {
		if (BedwarsMod.getInstance().isEnabled() && BedwarsMod.getInstance().inGame() &&
			!BedwarsMod.getInstance().displayArmor.get()) {
			return;
		}
		original.call(graphics, player, y, uncappedMaxHealth, cappedMaxHealth, x);
	}

	@WrapWithCondition(method = "extractChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
	private boolean hideChat(ChatComponent instance, GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions) {
		return !AxolotlClient.config().hideChat.get();
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void customHotbar(Hud instance, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HotbarHud hud = (HotbarHud) HudManager.getInstance().get(HotbarHud.ID);
		if (!hud.isHidden()) {
			graphics.br$pushMatrix();
			if (hud.isEnabled()) {
				graphics.br$translateMatrix(-graphics.guiWidth() / 2f + 182 / 2f, -graphics.guiHeight() + 22);
				graphics.br$translateMatrix(hud.getRawTrueX(), hud.getRawTrueY());
			}
			original.call(instance, graphics, deltaTracker);
			graphics.br$popMatrix();
		}
	}

	@WrapOperation(method = "extractTabList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"))
	private void translateTabOverlay(PlayerTabOverlay instance, GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective displayObjective, Operation<Void> original) {
		var hud = (PlayerTabOverlayHud) HudManager.getInstance().get(PlayerTabOverlayHud.ID);
		if (!hud.isHidden()) {
			graphics.br$pushMatrix();
			if (hud.isEnabled()) {
				graphics.br$translateMatrix(-graphics.br$guiWidth() / 2f, -9);
				graphics.br$translateMatrix(hud.getRawTrueX() + hud.getTrueWidth()/2f, hud.getRawTrueY());
			}
			original.call(instance, graphics, screenWidth, scoreboard, displayObjective);
			graphics.br$popMatrix();
		}
	}
}
