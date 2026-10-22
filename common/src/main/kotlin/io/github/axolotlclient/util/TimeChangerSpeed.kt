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

package io.github.axolotlclient.util

/**
 * Bookkeeping for the client-side time multiplier of the time changer.
 *
 * While active (time changer enabled with a multiplier other than `1.0`),
 * each passing client tick advances the visual world time by `multiplier`
 * ticks. Whole extra ticks are accumulated in [offset] (which may go
 * negative for multipliers below 1), fractions are carried over between ticks.
 */
object TimeChangerSpeed {

    private var active = false
    private var multiplier = 1.0f
    private var fractionalOffset = 0.0
    private var offsetValue = 0L
    private var lastExtraValue = 0L

    /**
     * Advance the accumulator by one client tick. Must be called once per world tick,
     * before the game time is advanced for that tick.
     *
     * @param timeChangerActive whether the time changer is currently enabled
     * @param timeMultiplier    the configured time multiplier
     */
    @JvmStatic
    fun tick(timeChangerActive: Boolean, timeMultiplier: Float) {
        lastExtraValue = 0
        if (!timeChangerActive || timeMultiplier == 1.0f) {
            active = false
            multiplier = 1.0f
            fractionalOffset = 0.0
            offsetValue = 0
            return
        }

        if (!active || timeMultiplier != multiplier) {
            // (Re-)activation or a changed multiplier: start over from the current world time.
            active = true
            multiplier = timeMultiplier
            fractionalOffset = 0.0
            offsetValue = 0
        }

        fractionalOffset += timeMultiplier - 1.0f
        while (fractionalOffset >= 1.0f) {
            offsetValue++
            lastExtraValue++
            fractionalOffset -= 1.0f
        }
        while (fractionalOffset <= -1.0f) {
            offsetValue--
            lastExtraValue--
            fractionalOffset += 1.0f
        }
    }

    /**
     * The accumulated whole extra ticks to add on top of the vanilla game time,
     * so that world time visually passes at the configured multiplier speed.
     * This is only applied where the server-provided game time enters the client
     * (the time packet); the per-tick advancement uses [lastExtra] instead.
     */
    @JvmStatic
    fun offset(): Long = offsetValue

    /**
     * The whole extra ticks gained during the most recent [tick] call.
     * Added to this tick's local game time advancement so that the local game time
     * (and with it the client clock feed) advances at the multiplier speed.
     */
    @JvmStatic
    fun lastExtra(): Long = lastExtraValue
}
