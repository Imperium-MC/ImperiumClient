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

import com.llamalad7.mixinextras.expression.Definition
import com.llamalad7.mixinextras.expression.Expression
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.sugar.Share
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.api.requests.UserRequest
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.PlayerTabOverlayHud
import io.github.axolotlclient.modules.hypixel.NickHider
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsGame
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsPlayer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerTabOverlay
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.Slice
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.UUID
import kotlin.math.min

@Mixin(PlayerTabOverlay::class)
abstract class PlayerListHudMixin {

    @Shadow
    private var header: Component? = null

    @Shadow
    private var footer: Component? = null

    @Shadow
    @Final
    private val minecraft: Minecraft? = null

    @WrapMethod(method = ["getNameForDisplay"])
    private fun nickHider(playerInfo: PlayerInfo, original: Operation<Component>): Component {
        val orig = original.call(playerInfo)
        val player = minecraft!!.player ?: return orig
        if (playerInfo.profile == player.gameProfile && NickHider.getInstance().hideOwnName.get()) {
            return NickHider.getInstance()
                .editComponent(orig, playerInfo.profile.name, NickHider.getInstance().hiddenNameSelf.get()) as Component
        } else if (playerInfo.profile != player.gameProfile && NickHider.getInstance().hideOtherNames.get()) {
            return NickHider.getInstance()
                .editComponent(orig, playerInfo.profile.name, NickHider.getInstance().hiddenNameOthers.get()) as Component
        }
        return orig
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I",
            ordinal = 0
        )]
    )
    private fun `axolotlclient$moveName`(
        instance: Font, text: FormattedText, original: Operation<Int>,
        @Local(name = ["info"]) info: PlayerInfo
    ): Int {
        var width = original.call(instance, text)
        if (AxolotlClient.config().showBadges.get()) {
            if (AxolotlClient.config().tabBadgeMode.get() == AxolotlClientConfigCommon.TabBadgeMode.BEFORE_NAME_ALIGNED ||
                UserRequest.getOnline(info.profile.id().toString())
            ) {
                width += 9
            }
        }
        if ((HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).numericalPing.get()) {
            width += instance.width(PlayerTabOverlayHud.numericPingText(info.latency)) - 10
        }
        return width
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )]
    )
    fun `axolotlclient$moveName2`(
        instance: GuiGraphicsExtractor, font: Font, component: Component, xIn: Int, y: Int, color: Int,
        original: Operation<Int>, @Local(name = ["info"]) info: PlayerInfo
    ) {
        var x = xIn
        if (AxolotlClient.config().showBadges.get() &&
            (AxolotlClient.config().tabBadgeMode.get() == AxolotlClientConfigCommon.TabBadgeMode.BEFORE_NAME ||
                AxolotlClient.config().tabBadgeMode.get() == AxolotlClientConfigCommon.TabBadgeMode.BEFORE_NAME_ALIGNED)
        ) {
            if (UserRequest.getOnline(info.profile.id().toString())) {
                instance.blit(
                    RenderPipelines.GUI_TEXTURED, AxolotlClientCommon.BADGE_PATH as Identifier,
                    x, y, 0f, 0f, 8, 8, 8, 8
                )
                x += 9
            } else if (AxolotlClient.config().tabBadgeMode.get() == AxolotlClientConfigCommon.TabBadgeMode.BEFORE_NAME_ALIGNED) {
                x += 9
            }
        }
        original.call(instance, font, component, x, y, color)
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"
        )]
    )
    private fun badgeBeforePing(
        instance: PlayerTabOverlay, graphics: GuiGraphicsExtractor, slotWidth: Int, xo: Int, yo: Int,
        info: PlayerInfo, original: Operation<Void>
    ) {
        if (AxolotlClient.config().showBadges.get() && AxolotlClient.config().tabBadgeMode.get() == AxolotlClientConfigCommon.TabBadgeMode.BEFORE_PING
            && UserRequest.getOnline(info.profile.id().toString())
        ) {
            val pingWidth = if ((HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).numericalPing.get()) {
                graphics.`br$getFont`().`br$getWidth`(PlayerTabOverlayHud.numericPingText(info.latency)) + 1
            } else 11
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, AxolotlClientCommon.BADGE_PATH as Identifier,
                xo + slotWidth - pingWidth - 9, yo, 0f, 0f, 8, 8, 8, 8
            )
        }
        original.call(instance, graphics, slotWidth, xo, yo, info)
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"
        )]
    )
    private fun `axolotlclient$highlightSelfRow`(
        instance: PlayerTabOverlay, graphics: GuiGraphicsExtractor, slotWidth: Int, xo: Int, yo: Int,
        info: PlayerInfo, original: Operation<Void>
    ) {
        val tablist = HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud?
        val self = minecraft!!.player != null && info.profile == minecraft.player!!.gameProfile
        if (self) {
            if (tablist != null && tablist.highlightSelf.get()) {
                graphics.fill(xo, yo, xo + slotWidth, yo + 8, tablist.getSelfHighlightColor())
            }
        } else if (tablist != null && tablist.highlightGroupMembers.get() &&
            io.github.axolotlclient.imperium.LegionStateRegistry.isMember(info.profile.name)
        ) {
            // Alpine "highlight_group_members" (M6): legion peers get their own row
            // highlight; self keeps precedence so the two never stack.
            graphics.fill(xo, yo, xo + slotWidth, yo + 8, tablist.getGroupHighlightColor())
        }
        original.call(instance, graphics, slotWidth, xo, yo, info)
    }

    @Inject(method = ["extractPingIcon"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$numericalPing`(graphics: GuiGraphicsExtractor, width: Int, x: Int, y: Int, entry: PlayerInfo, ci: CallbackInfo) {
        if (BedwarsMod.getInstance().isEnabled && BedwarsMod.getInstance().customTabList.get()
            && BedwarsMod.getInstance().blockLatencyIcon() &&
            (BedwarsMod.getInstance().isWaiting() || BedwarsMod.getInstance().inGame())
        ) {
            ci.cancel()
        } else if ((HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud)
                .renderNumericPing(graphics, width, x, y, entry)
        ) {
            ci.cancel()
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z")]
    )
    private fun `showPlayerHeads$1`(instance: ClientPacketListener, original: Operation<Boolean>): Boolean {
        if ((HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).showPlayerHeads.get()) {
            return original.call(instance)
        }
        return false
    }

    @Definition(id = "header", field = ["Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"])
    @Expression("this.header != null")
    @WrapOperation(method = ["extractRenderState"], at = [At("MIXINEXTRAS:EXPRESSION")])
    private fun checkHeaderOption(left: Any, right: Any, original: Operation<Boolean>): Boolean {
        return original.call(left, right) &&
            (HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).showHeader.get()
    }

    @Definition(id = "footer", field = ["Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"])
    @Expression("this.footer != null")
    @WrapOperation(method = ["extractRenderState"], at = [At("MIXINEXTRAS:EXPRESSION")])
    private fun checkFooterOption(left: Any, right: Any, original: Operation<Boolean>): Boolean {
        return original.call(left, right) &&
            (HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).showFooter.get()
    }

    @ModifyArg(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"
        )],
        index = 5
    )
    private fun `axolotlclient$renderHatLayer`(drawHat: Boolean): Boolean {
        return drawHat || (HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud).alwaysShowHeadLayer.get()
    }

    @Inject(
        method = ["extractTablistScore"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )],
        cancellable = true
    )
    private fun `axolotlclient$renderCustomScoreboardObjective`(
        objective: Objective, y: Int, entry: PlayerTabOverlay.ScoreDisplayEntry, startX: Int, endX: Int,
        uuid: UUID, graphics: GuiGraphicsExtractor, ci: CallbackInfo
    ) {
        if (!BedwarsMod.getInstance().isEnabled) {
            return
        }
        if (!BedwarsMod.getInstance().customTabList.get()) {
            return
        }

        val game = BedwarsMod.getInstance().game.orElse(null) ?: return

        game.renderCustomScoreboardObjective(graphics, entry.name().string, entry.score(), y, endX)

        ci.cancel()
    }

    @ModifyVariable(method = ["extractRenderState"], at = [At("STORE")], name = ["spacerWidth"])
    fun `axolotlclient$changeWidth`(value: Int): Int {
        var v = value
        if (!BedwarsMod.getInstance().isEnabled) {
            return v
        }
        if (!BedwarsMod.getInstance().customTabList.get()) {
            return v
        }
        if (BedwarsMod.getInstance().blockLatencyIcon() &&
            (BedwarsMod.getInstance().isWaiting() || BedwarsMod.getInstance().inGame())
        ) {
            v -= 9
        }
        if (BedwarsMod.getInstance().isWaiting()) {
            v += 20
        }
        return v
    }

    @Inject(method = ["getNameForDisplay"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$getPlayerName`(entry: PlayerInfo, cir: CallbackInfoReturnable<Component>) {
        if (!BedwarsMod.getInstance().isEnabled) {
            return
        }
        if (!BedwarsMod.getInstance().customTabList.get()) {
            return
        }
        val game = BedwarsMod.getInstance().game.orElse(null) ?: return
        if (!game.isStarted) {
            return
        }
        val player = game.getPlayer(entry.profile.id()).orElse(null) ?: return
        cir.setReturnValue(Component.literal(player.getTabListDisplay()))
    }

    @Suppress("UNCHECKED_CAST")
    @ModifyVariable(method = ["extractRenderState"], at = [At("STORE")], name = ["playerInfos"])
    fun `axolotlclient$overrideSortedPlayers`(original: MutableList<PlayerInfo>): List<PlayerInfo> {
        if (!BedwarsMod.getInstance().inGame()) {
            return original
        }
        @Suppress("UNCHECKED_CAST")
        val players = BedwarsMod.getInstance().game.orElseThrow().getTabPlayerList(
            java.util.Collections.unmodifiableList(original) as List<io.github.axolotlclient.bridge.AxoPlayerListEntry>
        ) ?: return original
        return players as List<PlayerInfo>
    }

    @Inject(method = ["setHeader"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$changeHeader`(header: Component, ci: CallbackInfo) {
        if (!BedwarsMod.getInstance().inGame()) {
            return
        }
        if (!BedwarsMod.getInstance().customTabHeader.get()) {
            return
        }
        this.header = BedwarsMod.getInstance().game.orElseThrow().topBarText as Component
        ci.cancel()
    }

    @Inject(method = ["setFooter"], at = [At("HEAD")], cancellable = true)
    fun `axolotlclient$changeFooter`(footer: Component, ci: CallbackInfo) {
        if (!BedwarsMod.getInstance().inGame()) {
            return
        }
        if (!BedwarsMod.getInstance().customTabFooter.get()) {
            return
        }
        this.footer = BedwarsMod.getInstance().game.orElseThrow().bottomBarText as Component
        ci.cancel()
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", ordinal = 1)]
    )
    private fun captureFooterLines(
        instance: Font, input: FormattedText, maxWidth: Int, original: Operation<List<FormattedCharSequence>>,
        @Share("footerLines") footerLines: LocalRef<List<FormattedCharSequence>?>
    ): List<FormattedCharSequence> {
        val lines = original.call(instance, input, maxWidth)
        footerLines.set(lines)
        return lines
    }

    @Definition(id = "headerLines", local = [Local(type = List::class, name = ["headerLines"])])
    @Expression("headerLines != null")
    @Inject(method = ["extractRenderState"], at = [At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0)])
    private fun renderBackgroundWithOptions(
        g: GuiGraphicsExtractor, screenWidth: Int, scoreboard: Scoreboard, displayObjective: Objective,
        ci: CallbackInfo,
        @Local(name = ["maxLineWidth"]) maxLineWidth: Int, @Local(name = ["yyo"]) q: Int,
        @Local(name = ["headerLines"]) headerLines: List<FormattedCharSequence>?,
        @Share("footerLines") footerLines: LocalRef<List<FormattedCharSequence>?>,
        @Local(name = ["rows"]) rows: Int
    ) {
        val tablist = HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud
        if (!tablist.isEnabled() || tablist.backgroundDisabled()) {
            return
        }
        var x = screenWidth / 2 - maxLineWidth / 2 - 1
        var y = q - 1
        var x2 = screenWidth / 2 + maxLineWidth / 2 + 1
        val headerLineCount = headerLines?.size ?: 0
        val footerLineCount = footerLines.get()?.size ?: 0
        var y2 = q + (headerLineCount + rows + footerLineCount) * g.`br$getFont`().`br$getFontHeight`() +
            (if (footerLineCount > 0) 1 else 0) + (if (headerLineCount > 0) 1 else 0)
        val padding = tablist.getBackgroundPadding()
        x -= padding
        x2 += padding
        y -= padding
        y2 += padding
        if (y < 0) {
            y2 -= y
            y = 0
        }
        if (tablist.hasRoundBackground()) {
            val rounding = min(
                tablist.getBackgroundRounding().toFloat(),
                min(x2 - x, y2 - y) / 2f
            )
            g.`br$fillRectRound`(
                x, y, x2 - x, y2 - y,
                if (tablist.customBackgroundColor.get()) tablist.getBackgroundColor().toInt() else Int.MIN_VALUE,
                rounding
            )

            if (tablist.hasOutline()) {
                g.`br$outlineRectRound`(
                    x, y, x2 - x, y2 - y,
                    tablist.getOutlineColor(), rounding
                )
            }
        } else {
            g.`br$fillRect`(
                x, y, x2 - x, y2 - y,
                if (tablist.customBackgroundColor.get()) tablist.getBackgroundColor().toInt() else Int.MIN_VALUE
            )
            if (tablist.hasOutline()) {
                g.`br$outlineRect`(
                    x, y, x2 - x, y2 - y,
                    tablist.getOutlineColor()
                )
            }
        }
    }

    @Unique
    private fun applyBackgroundOptions(
        instance: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int,
        original: Operation<Void>
    ) {
        val tablist = HudManagerCommon.getInstance()!!.get(PlayerTabOverlayHud.ID) as PlayerTabOverlayHud
        if (!tablist.isEnabled()) {
            original.call(instance, x1, y1, x2, y2, color)
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")],
        slice = [Slice(to = At(value = "CONSTANT", args = ["intValue=553648127"]))]
    )
    private fun `modifyBackground$headerAndMain`(
        instance: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int,
        original: Operation<Void>
    ) {
        applyBackgroundOptions(instance, x1, y1, x2, y2, color, original)
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")],
        slice = [Slice(from = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"
        ))]
    )
    private fun `modifyBackground$footer`(
        instance: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int,
        original: Operation<Void>
    ) {
        applyBackgroundOptions(instance, x1, y1, x2, y2, color, original)
    }
}
