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
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.PotionsHud;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.*;
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod;
import io.github.axolotlclient.modules.waypoints.WaypointTracers;
import io.github.axolotlclient.util.FarmingOverlay;
import io.github.axolotlclient.util.LowHpTint;
import io.github.axolotlclient.util.OverlayOpacity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
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

	/**
	 * farming: drawn right after the crosshair so the growth stage sits next to it whether
	 * the vanilla or the custom crosshair is active (the call itself always executes even if
	 * extractCrosshair's body was cancelled by the custom crosshair).
	 */
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
	private void axolotlclient$farmingOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		FarmingOverlay.render(graphics);
	}

	/**
	 * waypoints tracers: world-space markers projected onto the screen after the crosshair (same
	 * slot as the farming overlay — the camera holds this frame's pose, and the tracers sit under
	 * the chat/panels that the HUD extracts later).
	 */
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
	private void axolotlclient$waypointTracers(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		WaypointTracers.render(graphics);
	}

	/**
	 * low_hp_tint: drawn with the camera overlays (vignette/spyglass/pumpkin section), before
	 * the vanilla vignette itself.
	 */
	@Inject(method = "extractCameraOverlays", at = @At("HEAD"))
	private void axolotlclient$lowHpTint(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		LowHpTint.render(graphics, minecraft.getCameraEntity());
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	public void axolotlclient$renderStatusEffect(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		PotionsHud hud = (PotionsHud) HudManager.getInstance().get(PotionsHud.ID);
		// potion_status/display_vanilla (Alpine parity): keep the vanilla effect HUD drawn
		// alongside the custom one instead of cancelling it.
		if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled() && !hud.displayVanilla.get()) {
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
		// Server-HUD-safety (Alpine parity M2/M6): only suppress the vanilla sidebar while the
		// custom entry actually renders it — otherwise a server sidebar either double-renders
		// or disappears entirely (global HUD toggle off / hide-in-F3). Mirrors the BossBarHud
		// null-safe pattern plus the hudsEnabled/isHidden guards the other vanilla cancels use.
		// With the entry's server_hud_safety option off, suppression is unconditional while
		// the entry is enabled (aggressive takeover, Alpine's old behaviour).
		if (event.isCancelled() || (hud != null && HudManager.getInstance().hudsEnabled()
			&& hud.isEnabled() && (hud.serverHudSafety.get() || !hud.isHidden()))) {
			ci.cancel();
		}
	}

	// Movable titles (Alpine "titles", M2): vanilla still owns title state — these
	// three setters forward into TitleHud — but the vanilla extractTitle draw is
	// cancelled when the entry is enabled, and HudManager renders it at the
	// user's anchor with the entry's scale/color options instead.
	@Inject(method = "setTitle", at = @At("TAIL"))
	public void axolotlclient$forwardTitle(Component title, CallbackInfo ci) {
		TitleHud hud = (TitleHud) HudManager.getInstance().get(TitleHud.ID);
		if (hud != null) {
			hud.setTitle(title);
		}
	}

	@Inject(method = "setSubtitle", at = @At("TAIL"))
	public void axolotlclient$forwardSubtitle(Component subtitle, CallbackInfo ci) {
		TitleHud hud = (TitleHud) HudManager.getInstance().get(TitleHud.ID);
		if (hud != null) {
			hud.setSubtitle(subtitle);
		}
	}

	@Inject(method = "setTimes", at = @At("TAIL"))
	public void axolotlclient$forwardTitleTimes(int fadeIn, int stay, int fadeOut, CallbackInfo ci) {
		TitleHud hud = (TitleHud) HudManager.getInstance().get(TitleHud.ID);
		if (hud != null) {
			hud.setTimes(fadeIn, stay, fadeOut);
		}
	}

	@Inject(method = "clearTitles", at = @At("TAIL"))
	public void axolotlclient$forwardClearTitles(CallbackInfo ci) {
		TitleHud hud = (TitleHud) HudManager.getInstance().get(TitleHud.ID);
		if (hud != null) {
			hud.clearTitles();
		}
	}

	@Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
	public void axolotlclient$renderTitle(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		TitleHud hud = (TitleHud) HudManager.getInstance().get(TitleHud.ID);
		if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
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

	// pumpkin_opacity (Alpine grab-bag, wave 3): the equipment camera-overlay branch of
	// extractCameraOverlays (carved pumpkin & friends) — the first extractTextureOverlay
	// call site — draws with ARGB.white(alpha); scaling that alpha scales the overlay.
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0))
	private void axolotlclient$pumpkinOverlayOpacity(Hud instance, GuiGraphicsExtractor graphics, Identifier texture,
													 float alpha, Operation<Void> original) {
		original.call(instance, graphics, texture,
			OverlayOpacity.scaleAlpha(alpha, AxolotlClient.config().pumpkinOpacity.get()));
	}

	// frost_opacity: the powder-snow branch — the second extractTextureOverlay call site,
	// whose alpha is the freeze-percentage ramp.
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 1))
	private void axolotlclient$frostOverlayOpacity(Hud instance, GuiGraphicsExtractor graphics, Identifier texture,
												   float alpha, Operation<Void> original) {
		original.call(instance, graphics, texture,
			OverlayOpacity.scaleAlpha(alpha, AxolotlClient.config().frostOpacity.get()));
	}

	// spyglass_opacity: the scope itself is drawn with the colorless 10-arg blit, so the
	// wrap re-submits through the 11-arg overload with a white color whose alpha is the
	// configured percent; at 100% it keeps the vanilla call verbatim.
	@WrapOperation(method = "extractSpyglassOverlay", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void axolotlclient$spyglassScopeOpacity(GuiGraphicsExtractor instance, RenderPipeline pipeline,
													Identifier texture, int x, int y, float u, float v, int width,
													int height, int uWidth, int uHeight, Operation<Void> original) {
		float factor = OverlayOpacity.factor(AxolotlClient.config().spyglassOpacity.get());
		if (factor >= 1.0F) {
			original.call(instance, pipeline, texture, x, y, u, v, width, height, uWidth, uHeight);
		} else {
			instance.blit(pipeline, texture, x, y, u, v, width, height, uWidth, uHeight, ARGB.white(factor));
		}
	}

	// spyglass_opacity, mask half: the opaque black bars around the scope draw through
	// fill(color); scaling that color's alpha makes the whole overlay translucent.
	@WrapOperation(method = "extractSpyglassOverlay", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;IIIII)V"))
	private void axolotlclient$spyglassMaskOpacity(GuiGraphicsExtractor instance, RenderPipeline pipeline, int x1,
												   int y1, int x2, int y2, int color, Operation<Void> original) {
		original.call(instance, pipeline, x1, y1, x2, y2,
			OverlayOpacity.scaleArgb(color, AxolotlClient.config().spyglassOpacity.get()));
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
