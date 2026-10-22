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

import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * Stopwatch (Alpine "stopwatch" module, parity spec M4): a simple HH:MM:SS stopwatch
 * with two unbound-by-default keybinds — start/stop toggle and reset — mirroring
 * Alpine's `start_stop` and `reset` bindings. Leaving a world resets it,
 * so a stale timer never carries into the next session.
 */
class StopwatchHud : SimpleTextHudEntry(true) {
    /** Wall-clock start of the current run; negative when the stopwatch is stopped.  */
    private var startMs = -1L

    /** Time accumulated over previous runs of this session, kept when merely stopped.  */
    private var accumulatedMs = 0L

    override fun init() {
        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "toggle_stopwatch").`br$registerOnConsumeClick`(Runnable { toggle() })
        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "reset_stopwatch").`br$registerOnConsumeClick`(Runnable { reset() })
        Events.DISCONNECT.register(Runnable { reset() })
    }

    private fun toggle() {
        val now = Platform.getMeasuringTimeMs()
        if (startMs < 0) {
            startMs = now
        } else {
            accumulatedMs += Math.max(0, now - startMs)
            startMs = -1
        }
    }

    private fun reset() {
        startMs = -1
        accumulatedMs = 0
    }

    private fun elapsed(): Long {
        val now = Platform.getMeasuringTimeMs()
        return if (startMs < 0) accumulatedMs else accumulatedMs + Math.max(0, now - startMs)
    }

    override fun getValue(): String = PlaytimeHud.TimeFormat.HH_MM_SS.formatted(elapsed())

    override fun getPlaceholderValue(): String = PlaytimeHud.TimeFormat.HH_MM_SS.placeholder()

    override fun getLabel(): String = AxoI18n.translate("stopwatchhud.stopwatch")

    override fun getId(): AxoIdentifier = ID

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("imperiumclient", "stopwatchhud")
    }
}
