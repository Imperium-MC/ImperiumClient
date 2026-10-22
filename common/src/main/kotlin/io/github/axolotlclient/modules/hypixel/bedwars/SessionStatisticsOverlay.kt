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

package io.github.axolotlclient.modules.hypixel.bedwars

import java.text.DecimalFormat
import java.util.function.Function
import java.util.function.ToIntFunction

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.options.GenericOption
import kotlin.math.max

class SessionStatisticsOverlay(private val mod: BedwarsMod) : TextHudEntry(150, 200, true) {
    val entries: MutableList<SessionStatsEntry> = ArrayList(
        listOf(
            SessionStatsEntry.ofInt("games_played", ToIntFunction { s -> s.getGamesPlayed() }),
            SessionStatsEntry.ofInt("wins", ToIntFunction { s -> s.getWins() }),
            SessionStatsEntry.ofInt("losses", ToIntFunction { s -> s.getLosses() }),
            SessionStatsEntry.ofInt("winstreak", ToIntFunction { s -> s.getWinstreak() }),
            SessionStatsEntry.ofInt("beds_broken", ToIntFunction { s -> s.getBedsBroken() }),
            SessionStatsEntry.ofInt("beds_lost", ToIntFunction { s -> s.getBedsLost() }),
            SessionStatsEntry.ofInt("final_deaths", ToIntFunction { s -> s.getFinalDeaths() }),
            SessionStatsEntry.ofInt("final_kills", ToIntFunction { s -> s.getFinalKills() }),
            SessionStatsEntry.ofInt("deaths", ToIntFunction { s -> s.getDeaths() }),
            SessionStatsEntry.ofInt("kills", ToIntFunction { s -> s.getKills() }),
            SessionStatsEntry.ofFloat("kdr", Function { s -> s.getKdr() }),
            SessionStatsEntry.ofFloat("fkdr", Function { s -> s.getFkdr() })
        )
    )

    private val shown = BooleanOption("bedwars.session_stats.show_hud", true)
    private val entryConfig: GenericOption = object : GenericOption(
        "bedwars.session_stats.order", "bedwars.session_stats.order.label",
        GenericOption.ActionListener { PlatformDispatch.`bedwars$sessionstats$openEntryConfigScreen`() }
    ) {
        override fun toSerializedValue(): String = entries.joinToString(",") { e -> e.id }

        override fun fromSerializedValue(s: String?) {
            val order = s!!.split(",").toList()
            entries.sortBy { e -> order.indexOf(e.id) }
        }
    }
    private val valueColor = ColorOption("bedwars.session_stats.value_color", ClientColors.SELECTOR_BLUE)

    init {
        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "bedwars.session_stats.toggle_hud")
            .`br$registerOnConsumeClick`(Runnable {
                shown.toggle()
                AxolotlClientCommon.getInstance().saveConfig()
            })
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(options.indexOf(textColor) + 1, valueColor)
        options.add(shown)
        entries.forEach { e -> options.add(e.enabled) }
        options.add(entryConfig)
        return options
    }

    override fun postConfigLoad() {
        entries.forEach { e ->
            AxolotlClientCommon.getInstance().configManager.suppressName(e.enabled.getName())
        }
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (shown.get()) {
            super.render(ctx, delta)
        }
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        renderEntries(ctx, mod.sessionStats)
    }

    private fun renderEntries(ctx: AxoRenderContext, stats: SessionStatistics) {
        val startX = getContentX() + 2
        val width = getContentWidth() - 4
        var y = getContentY() + 2
        val lineHeight = ctx.`br$getFont`().`br$getFontHeight`() + 2
        var newWidth = 0
        for (e in entries) {
            if (!e.enabled.get()) continue
            newWidth = max(newWidth, renderLine(ctx, e.name, e.value.apply(stats), startX, y, width))
            y += lineHeight
        }
        y -= 2
        var updated = false
        if (newWidth != width) {
            setContentWidth(newWidth)
            updated = true
        }
        if (y - getContentY() != getContentHeight()) {
            setContentHeight(y - getContentY())
            updated = true
        }
        if (updated) {
            onBoundsUpdate()
        }
    }

    private fun renderLine(ctx: AxoRenderContext, left: AxoText, right: String, x: Int, y: Int, width: Int): Int {
        val leftWidth = ctx.`br$drawString`(left, x, y, textColor.get()) - x
        val rightWidth = ctx.`br$getFont`().`br$getWidth`(right)
        ctx.`br$drawString`(right, x + width - rightWidth, y, valueColor.get())
        return leftWidth + 10 + rightWidth
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        renderEntries(ctx, PLACEHOLDER_STATS)
    }

    override fun getId(): AxoIdentifier = ID

    @JvmRecord
    data class SessionStatsEntry(
        val id: String,
        val name: AxoText,
        val value: Function<SessionStatistics, String>,
        val enabled: BooleanOption
    ) {
        // Someone has to explain to me why there is a ToIntFunction and a ToDoubleFunction but no ToFloatFunction
        companion object {

            @JvmStatic
            fun ofInt(name: String, value: ToIntFunction<SessionStatistics>): SessionStatsEntry =
                SessionStatsEntry(
                    name, AxoText.translatable("bedwars.session_stats.$name"),
                    Function { s -> value.applyAsInt(s).toString() },
                    getOption(name)
                )

            @JvmStatic
            fun ofFloat(name: String, value: Function<SessionStatistics, Float>): SessionStatsEntry =
                SessionStatsEntry(
                    name, AxoText.translatable("bedwars.session_stats.$name"),
                    Function { s -> FORMAT.format(value.apply(s)) },
                    getOption(name)
                )

            private fun getOption(name: String): BooleanOption =
                BooleanOption("bedwars.session_stats.show_$name", true)
        }
    }

    companion object {
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "bedwars.session_statistics")
        private val FORMAT = DecimalFormat("0.##")
        private val PLACEHOLDER_STATS: SessionStatistics = CommonUtil.make(SessionStatistics()) { s ->
            s.wins = 50
            s.losses = 17
            s.bedsLost = 23
            s.bedsBroken = 72
            s.gamesPlayed = 67
            s.finalDeaths = 20
            s.finalKills = 65
            s.winstreak = 4
            s.deaths = 96
            s.kills = 203
        }
    }
}
