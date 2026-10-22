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

package io.github.axolotlclient.mixin

import com.llamalad7.mixinextras.expression.Expression
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent
import io.github.axolotlclient.modules.hud.HudEditScreen
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.PotionsHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.ActionBarHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.CrosshairHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.HotbarHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.PlayerTabOverlayHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.ScoreboardHud
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.TitleHud
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod
import io.github.axolotlclient.modules.waypoints.WaypointTracers
import io.github.axolotlclient.util.FarmingOverlay
import io.github.axolotlclient.util.LowHpTint
import io.github.axolotlclient.util.OverlayOpacity
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.components.PlayerTabOverlay
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Hud::class)
abstract class InGameHudMixin {

    @Shadow
    @Final
    private val minecraft: Minecraft? = null

    @Inject(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )]
    )
    private fun onHudRender(guiGraphicsExtractor: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        if (AxoMinecraftClient.getInstance().`br$getScreen`() !is HudEditScreen) {
            HudManager.getInstance().render(guiGraphicsExtractor, deltaTracker.getGameTimeDeltaTicks())
        }
    }

    /**
     * farming: drawn right after the crosshair so the growth stage sits next to it whether
     * the vanilla or the custom crosshair is active (the call itself always executes even if
     * extractCrosshair's body was cancelled by the custom crosshair).
     */
    @Inject(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER
        )]
    )
    private fun `axolotlclient$farmingOverlay`(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        FarmingOverlay.render(graphics)
    }

    /**
     * waypoints tracers: world-space markers projected onto the screen after the crosshair (same
     * slot as the farming overlay — the camera holds this frame's pose, and the tracers sit under
     * the chat/panels that the HUD extracts later).
     */
    @Inject(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER
        )]
    )
    private fun `axolotlclient$waypointTracers`(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        WaypointTracers.render(graphics)
    }

    /**
     * low_hp_tint: drawn with the camera overlays (vignette/spyglass/pumpkin section), before
     * the vanilla vignette itself.
     */
    @Inject(method = ["extractCameraOverlays"], at = [At("HEAD")])
    private fun `axolotlclient$lowHpTint`(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        LowHpTint.render(graphics, minecraft!!.getCameraEntity()!!)
    }

    @Inject(method = ["extractEffects"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$renderStatusEffect`(graphics: GuiGraphicsExtractor, tracker: DeltaTracker, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(PotionsHud.ID) as PotionsHud?
        // potion_status/display_vanilla (Alpine parity): keep the vanilla effect HUD drawn
        // alongside the custom one instead of cancelling it.
        if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled() && !hud.displayVanilla.get()) {
            ci.cancel()
        }
    }

    @Inject(method = ["extractCrosshair"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$renderCrosshair`(graphics: GuiGraphicsExtractor, tracker: DeltaTracker, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(CrosshairHud.ID) as CrosshairHud?
        if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
            if (minecraft!!.gui.hud.getDebugOverlay().showDebugScreen() && !hud!!.overridesF3()) {
                return
            }
            hud!!.renderCrosshair(graphics)
            ci.cancel()
        }
    }

    @Inject(method = ["displayScoreboardSidebar"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$renderScoreboard`(graphics: GuiGraphicsExtractor, objective: Objective, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(ScoreboardHud.ID) as ScoreboardHud?
        val event = ScoreboardRenderEvent(objective)
        Events.SCOREBOARD_RENDER_EVENT.invoker().accept(event)
        // Server-HUD-safety (Alpine parity M2/M6): only suppress the vanilla sidebar while the
        // custom entry actually renders it — otherwise a server sidebar either double-renders
        // or disappears entirely (global HUD toggle off / hide-in-F3). Mirrors the BossBarHud
        // null-safe pattern plus the hudsEnabled/isHidden guards the other vanilla cancels use.
        // With the entry's server_hud_safety option off, suppression is unconditional while
        // the entry is enabled (aggressive takeover, Alpine's old behaviour).
        if (event.isCancelled || (hud != null && HudManager.getInstance().hudsEnabled()
                && hud.isEnabled() && (hud.serverHudSafety.get() || !hud.isHidden))
        ) {
            ci.cancel()
        }
    }

    // Movable titles (Alpine "titles", M2): vanilla still owns title state — these
    // three setters forward into TitleHud — but the vanilla extractTitle draw is
    // cancelled when the entry is enabled, and HudManager renders it at the
    // user's anchor with the entry's scale/color options instead.
    @Inject(method = ["setTitle"], at = [At("TAIL")])
    fun `axolotlclient$forwardTitle`(title: Component, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(TitleHud.ID) as TitleHud?
        hud?.setTitle(title)
    }

    @Inject(method = ["setSubtitle"], at = [At("TAIL")])
    fun `axolotlclient$forwardSubtitle`(subtitle: Component, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(TitleHud.ID) as TitleHud?
        hud?.setSubtitle(subtitle)
    }

    @Inject(method = ["setTimes"], at = [At("TAIL")])
    fun `axolotlclient$forwardTitleTimes`(fadeIn: Int, stay: Int, fadeOut: Int, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(TitleHud.ID) as TitleHud?
        hud?.setTimes(fadeIn, stay, fadeOut)
    }

    @Inject(method = ["clearTitles"], at = [At("TAIL")])
    fun `axolotlclient$forwardClearTitles`(ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(TitleHud.ID) as TitleHud?
        hud?.clearTitles()
    }

    @Inject(method = ["extractTitle"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$renderTitle`(graphics: GuiGraphicsExtractor, tracker: DeltaTracker, ci: CallbackInfo) {
        val hud = HudManager.getInstance().get(TitleHud.ID) as TitleHud?
        if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
            ci.cancel()
        }
    }

    @WrapOperation(
        method = ["extractOverlayMessage"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
        )]
    )
    fun `axolotlclient$getActionBar`(
        instance: GuiGraphicsExtractor, font: Font, text: Component, x: Int, y: Int, width: Int,
        color: Int, original: Operation<Int>
    ) {
        val hud = HudManager.getInstance().get(ActionBarHud.ID) as ActionBarHud?
        if (HudManager.getInstance().hudsEnabled() && hud != null && hud.isEnabled()) {
            instance.pose().popMatrix()
            hud.render(instance, text, color)
            instance.pose().pushMatrix()
        } else {
            original.call(instance, font, text, x, y, width, color)
        }
    }

    @WrapOperation(
        method = ["extractHearts"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud\$HeartType;IIZZZ)V"
        )]
    )
    fun `axolotlclient$displayHardcoreHearts`(
        instance: Hud, graphics: GuiGraphicsExtractor, type: Hud.HeartType, x: Int, y: Int,
        hardcore: Boolean, blinking: Boolean, half: Boolean, original: Operation<Void>
    ) {
        //noinspection OptionalGetWithoutIsPresent
        val hardcoreMod = BedwarsMod.getInstance().isEnabled && BedwarsMod.getInstance().inGame() &&
            BedwarsMod.getInstance().hardcoreHearts.get() &&
            !BedwarsMod.getInstance().game.get().getSelf().isBed()
        original.call(instance, graphics, type, x, y, hardcoreMod || hardcore, blinking, half)
    }

    @Expression("? == 0")
    @ModifyExpressionValue(method = ["extractPlayerHealth"], at = [At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 1)])
    fun `axolotlclient$dontHunger`(original: Boolean): Boolean {
        if (original && BedwarsMod.getInstance().isEnabled &&
            BedwarsMod.getInstance().inGame() &&
            !BedwarsMod.getInstance().showHunger.get()
        ) {
            return false
        }
        return original
    }

    @Inject(method = ["extractVignette"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$removeVignette`(graphics: GuiGraphicsExtractor, entity: Entity, ci: CallbackInfo) {
        if (AxolotlClient.config().removeVignette.get()) {
            ci.cancel()
        }
    }

    // pumpkin_opacity (Alpine grab-bag, wave 3): the equipment camera-overlay branch of
    // extractCameraOverlays (carved pumpkin & friends) — the first extractTextureOverlay
    // call site — draws with ARGB.white(alpha); scaling that alpha scales the overlay.
    @WrapOperation(
        method = ["extractCameraOverlays"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",
            ordinal = 0
        )]
    )
    private fun `axolotlclient$pumpkinOverlayOpacity`(
        instance: Hud, graphics: GuiGraphicsExtractor, texture: Identifier,
        alpha: Float, original: Operation<Void>
    ) {
        original.call(
            instance, graphics, texture,
            OverlayOpacity.scaleAlpha(alpha, AxolotlClient.config().pumpkinOpacity.get())
        )
    }

    // frost_opacity: the powder-snow branch — the second extractTextureOverlay call site,
    // whose alpha is the freeze-percentage ramp.
    @WrapOperation(
        method = ["extractCameraOverlays"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",
            ordinal = 1
        )]
    )
    private fun `axolotlclient$frostOverlayOpacity`(
        instance: Hud, graphics: GuiGraphicsExtractor, texture: Identifier,
        alpha: Float, original: Operation<Void>
    ) {
        original.call(
            instance, graphics, texture,
            OverlayOpacity.scaleAlpha(alpha, AxolotlClient.config().frostOpacity.get())
        )
    }

    // spyglass_opacity: the scope itself is drawn with the colorless 10-arg blit, so the
    // wrap re-submits through the 11-arg overload with a white color whose alpha is the
    // configured percent; at 100% it keeps the vanilla call verbatim.
    @WrapOperation(
        method = ["extractSpyglassOverlay"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
        )]
    )
    private fun `axolotlclient$spyglassScopeOpacity`(
        instance: GuiGraphicsExtractor, pipeline: RenderPipeline,
        texture: Identifier, x: Int, y: Int, u: Float, v: Float, width: Int,
        height: Int, uWidth: Int, uHeight: Int, original: Operation<Void>
    ) {
        val factor = OverlayOpacity.factor(AxolotlClient.config().spyglassOpacity.get())
        if (factor >= 1.0f) {
            original.call(instance, pipeline, texture, x, y, u, v, width, height, uWidth, uHeight)
        } else {
            instance.blit(pipeline, texture, x, y, u, v, width, height, uWidth, uHeight, ARGB.white(factor))
        }
    }

    // spyglass_opacity, mask half: the opaque black bars around the scope draw through
    // fill(color); scaling that color's alpha makes the whole overlay translucent.
    @WrapOperation(
        method = ["extractSpyglassOverlay"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;IIIII)V"
        )]
    )
    private fun `axolotlclient$spyglassMaskOpacity`(
        instance: GuiGraphicsExtractor, pipeline: RenderPipeline, x1: Int,
        y1: Int, x2: Int, y2: Int, color: Int, original: Operation<Void>
    ) {
        original.call(
            instance, pipeline, x1, y1, x2, y2,
            OverlayOpacity.scaleArgb(color, AxolotlClient.config().spyglassOpacity.get())
        )
    }

    @WrapOperation(
        method = ["extractPlayerHealth"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIII)V"
        )]
    )
    private fun `axolotlclient$dontShowArmor`(
        graphics: GuiGraphicsExtractor, player: Player, y: Int, uncappedMaxHealth: Int,
        cappedMaxHealth: Int, x: Int, original: Operation<Void>
    ) {
        if (BedwarsMod.getInstance().isEnabled && BedwarsMod.getInstance().inGame() &&
            !BedwarsMod.getInstance().displayArmor.get()
        ) {
            return
        }
        original.call(graphics, player, y, uncappedMaxHealth, cappedMaxHealth, x)
    }

    @WrapWithCondition(
        method = ["extractChat"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V"
        )]
    )
    private fun hideChat(
        instance: ChatComponent, graphics: GuiGraphicsExtractor, font: Font, ticks: Int, mouseX: Int,
        mouseY: Int, displayMode: ChatComponent.DisplayMode, changeCursorOnInsertions: Boolean
    ): Boolean {
        return !AxolotlClient.config().hideChat.get()
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )]
    )
    private fun customHotbar(instance: Hud, graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, original: Operation<Void>) {
        val hud = HudManager.getInstance().get(HotbarHud.ID) as HotbarHud
        if (!hud.isHidden) {
            graphics.`br$pushMatrix`()
            if (hud.isEnabled) {
                graphics.`br$translateMatrix`(-graphics.guiWidth() / 2f + 182 / 2f, -graphics.guiHeight() + 22f)
                graphics.`br$translateMatrix`(hud.getRawTrueX().toFloat(), hud.getRawTrueY().toFloat())
            }
            original.call(instance, graphics, deltaTracker)
            graphics.`br$popMatrix`()
        }
    }

    @WrapOperation(
        method = ["extractTabList"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"
        )]
    )
    private fun translateTabOverlay(
        instance: PlayerTabOverlay, graphics: GuiGraphicsExtractor, screenWidth: Int,
        scoreboard: Scoreboard, displayObjective: Objective, original: Operation<Void>
    ) {
        val hud = HudManager.getInstance().get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud?
        if (hud != null && !hud.isHidden) {
            graphics.`br$pushMatrix`()
            if (hud.isEnabled) {
                graphics.`br$translateMatrix`(-graphics.`br$guiWidth`() / 2f, -9f)
                graphics.`br$translateMatrix`(hud.getRawTrueX() + hud.getTrueWidth() / 2f, hud.getRawTrueY().toFloat())
            }
            original.call(instance, graphics, screenWidth, scoreboard, displayObjective)
            graphics.`br$popMatrix`()
        }
    }
}
