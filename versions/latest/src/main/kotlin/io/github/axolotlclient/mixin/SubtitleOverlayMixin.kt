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

package io.github.axolotlclient.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import io.github.axolotlclient.modules.hud.HudEditScreen
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.SubtitlesHudHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.SubtitleOverlay
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SubtitleOverlay::class)
abstract class SubtitleOverlayMixin {

    @Shadow
    @Final
    private val audibleSubtitles: MutableList<SubtitleOverlay.Subtitle>? = null

    @Inject(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", shift = At.Shift.AFTER)]
    )
    private fun subtitlesHudPosition(
        graphics: GuiGraphicsExtractor, ci: CallbackInfo,
        @Local(name = ["width"]) width: Int, @Local(name = ["position"]) position: Vec3
    ) {
        val subtitles = audibleSubtitles!!
        val subtitlesHud = HudManagerCommon.getInstance()!!.get(SubtitlesHudHud.ID) as SubtitlesHudHud
        if (subtitlesHud.isEnabled() && subtitles.isNotEmpty()) {
            val lineHeight = 9
            val h = subtitles.stream().filter { s -> s.getBestSubtitleCandidate(position) != null }.count()
                .toInt() * (lineHeight + 1) + 2
            val w = width + 4
            if (Minecraft.getInstance().gui.screen() !is HudEditScreen) {
                var updated = false
                if (h != subtitlesHud.getContentHeight()) {
                    subtitlesHud.setContentHeight(h)
                    updated = true
                }
                if (w != subtitlesHud.getContentWidth()) {
                    subtitlesHud.setContentWidth(w)
                    updated = true
                }
                if (updated) {
                    subtitlesHud.onBoundsUpdate()
                }
            }
            subtitlesHud.renderHud(graphics, 0f)
            subtitlesHud.scale(graphics)
            graphics.`br$translateMatrix`(subtitlesHud.getContentX().toFloat(), subtitlesHud.getContentY().toFloat())
            graphics.`br$translateMatrix`(
                -graphics.guiWidth() + w.toFloat(),
                -(graphics.guiHeight() - 35) + h - (lineHeight + 1) / 2f - 1
            )
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")]
    )
    private fun switchEntryBackground(
        instance: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, col: Int,
        original: Operation<Void>
    ) {
        val subtitlesHud = HudManagerCommon.getInstance()!!.get(SubtitlesHudHud.ID) as SubtitlesHudHud
        if (!subtitlesHud.isEnabled() || subtitlesHud.vanillaEntryBackground.get()) {
            original.call(instance, x0, y0, x1, y1, col)
        }
    }

    @WrapMethod(method = ["extractRenderState"])
    private fun wrapExtraction(graphics: GuiGraphicsExtractor, original: Operation<Void>) {
        graphics.`br$pushMatrix`()
        original.call(graphics)
        graphics.`br$popMatrix`()
    }
}
