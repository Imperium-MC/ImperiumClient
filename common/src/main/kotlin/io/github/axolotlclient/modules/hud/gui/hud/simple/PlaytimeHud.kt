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

import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * Session playtime (Alpine "playtime" module, parity spec M4): wall-clock time spent
 * in the current world session, formatted HH:MM:SS by default.
 *
 * <p>The timer starts when a world is joined (`Events.WORLD_LOAD_EVENT`, which
 * fires from `Minecraft.setLevel`, so singleplayer world switches re-start it)
 * and resets on disconnect — reconnecting or loading another world begins a fresh
 * count, matching Alpine's per-session semantics.</p>
 */
class PlaytimeHud : SimpleTextHudEntry(true) {
    private val format: EnumOption<TimeFormat> = EnumOption("format", TimeFormat::class.java, TimeFormat.HH_MM_SS)

    private var sessionStart = -1L

    override fun init() {
        Events.WORLD_LOAD_EVENT.register(Consumer { sessionStart = Platform.getMeasuringTimeMs() })
        Events.DISCONNECT.register(Runnable { sessionStart = -1 })
    }

    override fun getValue(): String {
        if (client.`br$getWorld`() == null || sessionStart < 0) {
            return getPlaceholderValue()
        }

        return format.get().formatted(Math.max(0, Platform.getMeasuringTimeMs() - sessionStart))
    }

    override fun getPlaceholderValue(): String = format.get().placeholder()

    override fun getLabel(): String = AxoI18n.translate("playtimehud.playtime")

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(format)
        return options
    }

    enum class TimeFormat(private val padHours: Boolean, private val placeholder: String) {
        HH_MM_SS(true, "00:07:24"),
        H_MM_SS(false, "0:07:24"),
        MM_SS(false, "07:24");

        fun placeholder(): String = placeholder

        fun formatted(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (this == MM_SS) {
                return String.format(java.util.Locale.ROOT, "%02d:%02d", hours * 60 + minutes, seconds)
            }
            return String.format(java.util.Locale.ROOT, (if (padHours) "%02d" else "%d") + ":%02d:%02d", hours, minutes, seconds)
        }

        override fun toString(): String = "playtimehud.format." + name.lowercase(java.util.Locale.ROOT)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("imperiumclient", "playtimehud")
    }
}
