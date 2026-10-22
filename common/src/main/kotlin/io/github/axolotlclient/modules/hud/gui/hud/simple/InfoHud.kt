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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import java.util.ArrayList
import java.util.Locale
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.hud.CoordsHud
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ClientColors

/**
 * Configurable multi-line info HUD (Alpine "info_hud" module, parity spec M2).
 *
 * <p>Alpine's info HUD is a registry of line sources the user toggles individually.
 * There is no list-shaped option type in AxolotlClientConfig, so each line source
 * (coordinates, facing, biome, fps, world time, day, session playtime) is one
 * [BooleanOption] — the set of enabled options is the registry. Data reading
 * reuses the existing modules' logic where it is shared code: cardinal direction via
 * [CoordsHud.getDirection]/[CoordsHud.getWordedDirection], fps via
 * `AxoMinecraftClient.getCurrentFps()`, day counter and playtime formatting
 * identical to [DayCounterHud] and [PlaytimeHud].</p>
 */
class InfoHud : TextHudEntry(60, 13, true) {
    private val showCoordinates = BooleanOption("infohud.show_coordinates", true)
    private val showFacing = BooleanOption("infohud.show_facing", true)
    private val showBiome = BooleanOption("infohud.show_biome", false)
    private val showFps = BooleanOption("infohud.show_fps", true)
    private val showTime = BooleanOption("infohud.show_time", false)
    private val showDay = BooleanOption("infohud.show_day", false)
    private val showPlaytime = BooleanOption("infohud.show_playtime", false)
    private val labelColor = ColorOption("infohud.label_color", ClientColors.SELECTOR_BLUE)

    // Alpine profile settings (M6): a title line above the rows, a header colour for it,
    // and a brace style for how each row's label is set off from its value. The row label
    // colour stays the existing labelColor option (the profile's primary_color) and the
    // value colour the inherited textColor option (the profile's secondary_color).
    private val displayTitle = BooleanOption("infohud.display_title", false)
    private val headerColor = ColorOption("infohud.header_color", ClientColors.SELECTOR_BLUE)
    private val braceStyle: EnumOption<BraceStyle> =
        EnumOption("infohud.brace_style", BraceStyle::class.java, BraceStyle.COLON)

    private var sessionStart = -1L

    override fun init() {
        // Same session semantics as PlaytimeHud: timer starts on world join, resets on disconnect.
        Events.WORLD_LOAD_EVENT.register(Consumer { sessionStart = Platform.getMeasuringTimeMs() })
        Events.DISCONNECT.register(Runnable { sessionStart = -1 })
    }

    private data class InfoLine(val label: String, val value: String)

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        drawLines(context, collectLines(true))
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        drawLines(context, collectLines(false))
    }

    private fun collectLines(live: Boolean): List<InfoLine> {
        val player = client.`br$getPlayer`()
        val world = client.`br$getWorld`()
        val inGame = live && player != null && world != null
        val lines = ArrayList<InfoLine>()

        if (showCoordinates.get()) {
            var value = PLACEHOLDER_COORDS
            if (inGame) {
                val pos = player!!.`br$getPos`()
                value = Math.floor(pos.x).toInt().toString() + " " + Math.floor(pos.y).toInt() + " " +
                    Math.floor(pos.z).toInt()
            }
            lines.add(InfoLine(AxoI18n.translate("infohud.line.coordinates"), value))
        }

        if (showFacing.get()) {
            var value = "N (0\u00B0)"
            if (inGame) {
                val yaw = player!!.`br$getYaw`()
                // Same convention as CoordsHud/CompassHud: bearing = (yaw + 180) % 360, N = 0.
                val bearing = Math.floorMod(Math.floor(yaw + 180.0).toInt(), 360)
                value = CoordsHud.getWordedDirection(CoordsHud.getDirection((yaw + 180).toDouble())) +
                    " (" + bearing + "\u00B0)"
            }
            lines.add(InfoLine(AxoI18n.translate("infohud.line.facing"), value))
        }

        if (showBiome.get()) {
            var value = PLACEHOLDER_BIOME
            if (inGame) {
                value = world!!.`br$getBiomeName`(player!!.`br$getPos`())
            }
            lines.add(InfoLine(AxoI18n.translate("infohud.line.biome"), value))
        }

        if (showFps.get()) {
            var value = PLACEHOLDER_FPS
            if (live) {
                value = AxoMinecraftClient.getCurrentFps().toString()
            }
            lines.add(InfoLine(AxoI18n.translate("infohud.line.fps"), value))
        }

        if (showTime.get() || showDay.get()) {
            val gameTime = if (inGame) world!!.`br$getTimeOfDay`() else 0
            if (showTime.get()) {
                // 0 ticks = 06:00 in vanilla; 1000 ticks per in-game hour.
                val ticks = Math.floorMod(gameTime, 24000)
                val hours = ((ticks / 1000 + 6) % 24).toInt()
                val minutes = (ticks % 1000 * 60 / 1000).toInt()
                lines.add(
                    InfoLine(
                        AxoI18n.translate("infohud.line.time"),
                        String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
                    )
                )
            }
            if (showDay.get()) {
                lines.add(
                    InfoLine(
                        AxoI18n.translate("infohud.line.day"),
                        AxoI18n.translate("daycounterhud.days", gameTime / 24000)
                    )
                )
            }
        }

        if (showPlaytime.get()) {
            var value = PLACEHOLDER_PLAYTIME
            if (sessionStart >= 0) {
                value = PlaytimeHud.TimeFormat.HH_MM_SS.formatted(Math.max(0, Platform.getMeasuringTimeMs() - sessionStart))
            }
            lines.add(InfoLine(AxoI18n.translate("infohud.line.playtime"), value))
        }

        return lines
    }

    private fun drawLines(context: AxoRenderContext, lines: List<InfoLine>) {
        if (lines.isEmpty()) {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
            return
        }

        val pos = contentPos
        val lineHeight = client.`br$getFont`().`br$getFontHeight`() + 1
        var width = 0
        var y = pos.y()
        if (displayTitle.get()) {
            val title = AxoI18n.translate("infohud.title")
            val end = context.`br$drawString`(title, pos.x(), y, headerColor.get(), shadow.get())
            width = maxOf(width, end - pos.x())
            y += lineHeight
        }
        for (line in lines) {
            val cursor = context.`br$drawString`(braceStyle.get().wrap(line.label), pos.x(), y, labelColor.get(), shadow.get())
            val end = context.`br$drawString`(line.value, cursor, y, textColor.get(), shadow.get())
            width = maxOf(width, end - pos.x())
            y += lineHeight
        }
        val height = y - pos.y() - 1

        if (width + 1 != getContentWidth() || height != getContentHeight()) {
            setContentWidth(width + 1)
            setContentHeight(height)
            onBoundsUpdate()
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(showCoordinates)
        options.add(showFacing)
        options.add(showBiome)
        options.add(showFps)
        options.add(showTime)
        options.add(showDay)
        options.add(showPlaytime)
        options.add(labelColor)
        options.add(displayTitle)
        options.add(headerColor)
        options.add(braceStyle)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    /** How a row's label is separated from its value (Alpine "brace_style").  */
    enum class BraceStyle {
        COLON {
            override fun wrap(label: String): String = "$label: "
        },
        BRACKETS {
            override fun wrap(label: String): String = "[$label] "
        },
        DASH {
            override fun wrap(label: String): String = "$label - "
        },
        NONE {
            override fun wrap(label: String): String = "$label "
        };

        abstract fun wrap(label: String): String

        override fun toString(): String = "infohud.brace_style." + name.lowercase(Locale.ROOT)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "infohud")

        private const val PLACEHOLDER_COORDS = "109 180 -5098"
        private const val PLACEHOLDER_BIOME = "Plains"
        private const val PLACEHOLDER_FPS = "60"
        private const val PLACEHOLDER_TIME = "06:00"
        private const val PLACEHOLDER_PLAYTIME = "00:07:24"
    }
}
