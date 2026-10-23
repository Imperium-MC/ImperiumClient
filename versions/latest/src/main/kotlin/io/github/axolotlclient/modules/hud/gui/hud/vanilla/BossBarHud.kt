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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla

import java.util.HashMap
import java.util.UUID

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.mixin.BossBarHudAccessor
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DrawPosition
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.util.Util
import net.minecraft.world.BossEvent
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class BossBarHud : TextHudEntry(184, 80, false) {

    private val placeholder: BossEvent = CustomBossBar(
        Component.literal("Boss bar"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS
    )
    private val placeholder2: BossEvent = Util.make {
        val boss: BossEvent = CustomBossBar(
            Component.literal("More boss bars..."), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS
        )
        boss.setProgress(0.45f)
        boss
    }
    private val text = BooleanOption("text", true)

    // HEALTH PERCENT (Alpine parity M2): append the live progress as a percentage after
    // the boss name — the bar alone gives no precision and Alpine shows the number.
    private val healthPercent = BooleanOption("healthpercent", false)
    private val bar = BooleanOption("bar", true)
    private var bossBars: Map<UUID, LerpingBossEvent>? = HashMap()
    private val mc: Minecraft = super.client as Minecraft

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        setBossBars()
        if (bossBars == null || this.bossBars!!.isEmpty()) {
            return
        }
        val scaledPos: DrawPosition = contentPos
        var by = 12
        for (bossBar in bossBars!!.values) {
            renderBossBar(graphics as GuiGraphicsExtractor, scaledPos.x() + 1, by + scaledPos.y(), bossBar)
            by += 19
            if (by > getContentHeight()) {
                break
            }
        }
    }

    fun setBossBars() {
        bossBars = (mc.gui.hud.getBossOverlay() as BossBarHudAccessor).`axolotlclient$getBossBars`()
        if (bossBars != null) {
            if (bossBars!!.isEmpty()) {
                // Just leave it alone, it's not rendering anyway
                return
            }
            val h = min(bossBars!!.size * 19, (AxoWindow.getWindow()!!.`br$getScaledHeight`() / 3.0).toInt())
            if (h != getContentHeight()) {
                // Update height
                setContentHeight(min(bossBars!!.size * 19, (AxoWindow.getWindow()!!.`br$getScaledHeight`() / 3.0).toInt()))
                onBoundsUpdate()
            }
        }
    }

    private fun renderBossBar(graphics: GuiGraphicsExtractor, x: Int, y: Int, bossBar: BossEvent) {
        if (bar.get()) {
            this.drawBar(graphics, x, y, bossBar, 182, BAR_BACKGROUND_SPRITES, OVERLAY_BACKGROUND_SPRITES)
            val i = Mth.lerpDiscrete(bossBar.progress, 0, 182)
            if (i > 0) {
                this.drawBar(graphics, x, y, bossBar, i, BAR_PROGRESS_SPRITES, OVERLAY_PROGRESS_SPRITES)
            }
        }
        if (text.get()) {
            var bossText: Component = bossBar.name
            if (healthPercent.get()) {
                bossText = Component.literal(bossText.string + " (" + (bossBar.progress * 100).roundToInt() + "%)")
            }
            val textX = x + (getContentWidth() / 2f) - (mc.font.width(bossText) / 2f)
            val textY = y - 9f
            graphics.text(mc.font, bossText, textX.toInt(), textY.toInt(), textColor.get().toInt(), shadow.get())
        }
    }

    private fun drawBar(
        graphics: GuiGraphicsExtractor, x: Int, y: Int, bar: BossEvent, width: Int,
        textures: Array<Identifier>, alternativeTextures: Array<Identifier>
    ) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, textures[bar.color.ordinal], 182, 5, 0, 0, x, y, width, 5)
        if (bar.overlay != BossEvent.BossBarOverlay.PROGRESS) {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED, alternativeTextures[bar.overlay.ordinal - 1], 182, 5, 0,
                0, x, y, width, 5
            )
        }
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val height = 2 * 19
        if (height != getContentHeight()) {
            setContentHeight(height)
            onBoundsUpdate()
        }
        val pos: DrawPosition = contentPos
        renderBossBar(graphics as GuiGraphicsExtractor, pos.x() + 1, pos.y() + 12, placeholder)
        renderBossBar(graphics as GuiGraphicsExtractor, pos.x() + 1, pos.y() + 31, placeholder2)
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(text)
        options.add(healthPercent)
        options.add(bar)
        return options
    }

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_MIDDLE

    override fun getDefaultX(): Double = 0.5

    class CustomBossBar(name: Component, color: BossEvent.BossBarColor, style: BossEvent.BossBarOverlay) : BossEvent(
        Mth.createInsecureUUID(source), name, color, style
    )

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("kronhud", "bossbarhud")
        private val BAR_BACKGROUND_SPRITES = arrayOf(
            Identifier.withDefaultNamespace("boss_bar/pink_background"),
            Identifier.withDefaultNamespace("boss_bar/blue_background"),
            Identifier.withDefaultNamespace("boss_bar/red_background"),
            Identifier.withDefaultNamespace("boss_bar/green_background"),
            Identifier.withDefaultNamespace("boss_bar/yellow_background"),
            Identifier.withDefaultNamespace("boss_bar/purple_background"),
            Identifier.withDefaultNamespace("boss_bar/white_background")
        )
        private val BAR_PROGRESS_SPRITES = arrayOf(
            Identifier.withDefaultNamespace("boss_bar/pink_progress"),
            Identifier.withDefaultNamespace("boss_bar/blue_progress"),
            Identifier.withDefaultNamespace("boss_bar/red_progress"),
            Identifier.withDefaultNamespace("boss_bar/green_progress"),
            Identifier.withDefaultNamespace("boss_bar/yellow_progress"),
            Identifier.withDefaultNamespace("boss_bar/purple_progress"),
            Identifier.withDefaultNamespace("boss_bar/white_progress")
        )
        private val OVERLAY_BACKGROUND_SPRITES = arrayOf(
            Identifier.withDefaultNamespace("boss_bar/notched_6_background"),
            Identifier.withDefaultNamespace("boss_bar/notched_10_background"),
            Identifier.withDefaultNamespace("boss_bar/notched_12_background"),
            Identifier.withDefaultNamespace("boss_bar/notched_20_background")
        )
        private val OVERLAY_PROGRESS_SPRITES = arrayOf(
            Identifier.withDefaultNamespace("boss_bar/notched_6_progress"),
            Identifier.withDefaultNamespace("boss_bar/notched_10_progress"),
            Identifier.withDefaultNamespace("boss_bar/notched_12_progress"),
            Identifier.withDefaultNamespace("boss_bar/notched_20_progress")
        )

        // TODO custom color
        private val source = RandomSource.create()
    }
}
