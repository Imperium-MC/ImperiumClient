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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.DrawUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Util
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import kotlin.math.max
import kotlin.math.min

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ScoreboardHud : TextHudEntry(200, 146, true) {

    private val scoreboardBackgroundColor = ColorOption("backgroundcolor", Color(0x4C000000))
    private val topColor = ColorOption("topbackgroundcolor", Color(0x66000000))
    private val topPadding = IntegerOption("toppadding", 0, 0, 4)
    private val scores = BooleanOption("scores", true)
    private val scoreColor = ColorOption("scorecolor", Color(0xFFFF5555.toInt()))
    private val textAlpha = IntegerOption("text_alpha", 255, 0, 255)

    /**
     * Alpine "server_hud_safety" (M6): when on, the vanilla sidebar is only suppressed
     * while this entry actually renders it (global HUDs on, entry enabled and not
     * hidden in F1) so a server sidebar can never double-render or vanish entirely.
     * Turning it off takes over aggressively: vanilla is suppressed whenever the entry
     * is enabled, even while the custom render is paused. Read by InGameHudMixin.
     */
    @JvmField
    val serverHudSafety = BooleanOption("server_hud_safety", true)

    private val mc: Minecraft = super.client as Minecraft

    override fun render(graphics: AxoRenderContext, delta: Float) {
        graphics.`br$pushMatrix`()
        scale(graphics)
        renderComponent(graphics, delta)
        graphics.`br$popMatrix`()
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val scoreboard = this.mc.level!!.scoreboard
        var objective: Objective? = null
        val playerTeam = scoreboard.getPlayersTeam(mc.player!!.scoreboardName)
        if (playerTeam != null) {
            val teamColor = playerTeam.color
            if (teamColor.isPresent) {
                objective = scoreboard.getDisplayObjective(teamColor.get().displaySlot())
            }
        }

        val objective2 = objective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (objective2 != null) {
            this.displayScoreboardSidebar(graphics as GuiGraphicsExtractor, objective2, false)
        }
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        displayScoreboardSidebar(graphics as GuiGraphicsExtractor, placeholder, true)
    }

    // Abusing this could break some stuff/could allow for unfair advantages. The goal is not to do this, so it won't
    // show any more information than it would have in vanilla.
    private fun displayScoreboardSidebar(graphics: GuiGraphicsExtractor, objective: Objective, placeholder: Boolean) {
        val font = mc.font
        val scoreboard = objective.scoreboard
        val numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)

        data class DisplayEntry(val name: Component, val score: Component, val scoreWidth: Int)

        val entries: Array<DisplayEntry> = scoreboard.listPlayerScores(objective).stream()
            .filter { entry -> !entry.isHidden }
            .sorted(
                java.util.Comparator.comparing(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER)
            )
            .limit(15L)
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner)
                val value = entry.formatValue(numberFormat)
                DisplayEntry(PlayerTeam.formatNameForTeam(team, entry.ownerName()), value, font.width(value))
            }.toList().toTypedArray()
        val title = objective.displayName
        val titleWidth = font.width(title) + 2
        var maxWidth = titleWidth
        val textOffset = font.width(": ")

        for (lv in entries) {
            maxWidth = max(maxWidth, font.width(lv.name) + (if (lv.scoreWidth > 0 && scores.get()) textOffset + lv.scoreWidth else 0))
        }

        maxWidth += 3
        val entryCount = entries.size
        val mainHeight = entryCount * font.lineHeight

        val newHeight = mainHeight + 10 + topPadding.get() * 2

        var updated = false
        if (newHeight + 1 != getContentHeight()) {
            setContentHeight(newHeight + 1)
            updated = true
        }
        if (maxWidth + 1 != getContentWidth()) {
            setContentWidth(maxWidth + 1)
            updated = true
        }
        if (updated) {
            onBoundsUpdate()
        }

        val bounds: Rectangle = contentBounds

        val yEnd = bounds.y() + bounds.height()
        val textX = bounds.x() + 2
        val xEnd = bounds.x() + bounds.width() - 1
        val titleEnd = yEnd - mainHeight
        val bgBounds = getBounds()
        val maxRounding = min(
            min(font.lineHeight + topPadding.get() * 2 + backgroundPadding!!.get(), titleEnd - 1 - bgBounds.y()),
            xEnd - textX - 3
        ) / 2f
        val rounding = min(maxRounding, backgroundRounding!!.get().toFloat())
        if (!placeholder) {
            if (background!!.get()) {
                if (roundBackground!!.get()) {
                    graphics.`axolotlclient_rendering$roundedRectVarying`(
                        bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), titleEnd - 1,
                        topColor.get().toInt(), rounding, 0f, 0f, rounding
                    )
                    graphics.`axolotlclient_rendering$roundedRectVarying`(
                        bgBounds.x(), titleEnd - 1, bgBounds.xEnd(), bgBounds.yEnd(),
                        scoreboardBackgroundColor.get().toInt(), 0f, rounding, rounding, 0f
                    )
                } else {
                    graphics.fill(bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), titleEnd - 1, topColor.get().toInt())
                    graphics.fill(bgBounds.x(), titleEnd - 1, bgBounds.xEnd(), bgBounds.yEnd(), scoreboardBackgroundColor.get().toInt())
                }
            }
        } else {
            graphics.`br$fillRect`(
                bgBounds.x() + 1, bgBounds.y() + 1, bgBounds.width() - 2, titleEnd - 1 - bgBounds.y() - 1,
                ClientColors.DARK_GRAY.withAlpha(100)
            )
        }
        graphics.text(
            font, title, textX + maxWidth / 2 - titleWidth / 2, titleEnd - font.lineHeight - topPadding.get(),
            ARGB.color(textAlpha.get(), -1), shadow.get()
        )

        for (v in entries.indices) {
            val entry = entries[v]
            val y = yEnd - (entryCount - v) * font.lineHeight
            graphics.text(font, entry.name, textX, y, ARGB.color(textAlpha.get(), -1), shadow.get())
            if (scores.get()) {
                graphics.text(
                    font, entry.score, xEnd - entry.scoreWidth, y, scoreColor.get().toInt(),
                    shadow.get()
                )
            }
        }

        if (!placeholder) {
            val outlineCol = outlineColor
            if (outline!!.get() && outlineCol != null && outlineCol.get().alpha > 0) {
                if (roundBackground!!.get()) {
                    graphics.`axolotlclient_rendering$outlineRoundedRect`(
                        bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), bgBounds.yEnd(), outlineCol.get().toInt(),
                        rounding, 0.5f
                    )
                } else {
                    DrawUtil.outlineRect(graphics, bgBounds, outlineCol.get())
                }
            }
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        super.backgroundColor?.let { options[options.indexOf(it)] = scoreboardBackgroundColor }
        options.add(hide)
        options.add(topColor)
        options.add(scores)
        options.add(scoreColor)
        options.add(topPadding)
        options.add(serverHudSafety)
        options.remove(textColor as Option<*>)
        options.add(textAlpha)
        return options
    }

    override fun getId(): Identifier = ID

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.MIDDLE_RIGHT

    override fun getDefaultX(): Double = 1.0

    override fun getDefaultY(): Double = 0.5

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("kronhud", "scoreboardhud")

        @JvmField
        val placeholder: Objective = Util.make {
            val placeScore = Scoreboard()
            val objective: Objective = placeScore.addObjective(
                "placeholder", ObjectiveCriteria.DUMMY, Component.literal("Scoreboard"),
                ObjectiveCriteria.RenderType.INTEGER, false, StyledFormat.SIDEBAR_DEFAULT
            )
            val dark = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("DarkKronicle"), objective)
            dark.set(8780)

            val moeh = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("moehreag"), objective)
            moeh.set(743)

            val kode = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("TheKodeToad"), objective)
            kode.set(2948)

            placeScore.setDisplayObjective(DisplaySlot.SIDEBAR, objective)
            objective
        }
    }
}
