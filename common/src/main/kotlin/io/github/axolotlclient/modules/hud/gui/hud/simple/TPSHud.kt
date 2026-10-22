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

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class TPSHud : SimpleTextHudEntry(true) {
    private var lastTick = -1L
    private var lastUpdate = -1L
    private var tps = -1.0

    // ADAPTIVE COLOUR TIERS (Alpine parity M2): green when healthy, degrading through
    // yellow and orange to red as the server struggles. When disabled (or while the
    // first sample is still pending) the module's regular text colour option applies.
    // The tier thresholds follow the Alpine profile's four colour settings (M6).
    private val adaptiveColor = BooleanOption("adaptivecolor", true)
    private val highTpsColor = ColorOption("high_tps_color", Color(53, 219, 103).immutable())
    private val mediumTpsColor = ColorOption("medium_tps_color", Color(232, 197, 99).immutable())
    private val lowTpsColor = ColorOption("low_tps_color", Color(234, 164, 100).immutable())
    private val criticalTpsColor = ColorOption("critical_tps_color", Color(191, 34, 34).immutable())

    override fun init() {
        Events.UPDATE_TIME.register(
            Consumer { ticks: Long ->
                if (lastTick < 0) {
                    lastTick = ticks
                    lastUpdate = System.nanoTime()
                    return@Consumer
                }

                val time = System.nanoTime()
                // In nanoseconds, so 1000000000 in a second
                // Or 1000000 in a millisecond
                val elapsedMilli = (time - lastUpdate) / 1000000.0
                val passedTicks = (ticks - lastTick).toInt()
                if (passedTicks > 0) {
                    val mspt = elapsedMilli / passedTicks

                    tps = Math.min(1000 / mspt, 20.0)
                }

                lastTick = ticks
                lastUpdate = time
            }
        )
    }

    override fun getId(): AxoIdentifier = ID

    override fun getValue(): String {
        if (tps < 0) {
            return "NaN"
        }
        return FORMATTER.format(tps)
    }

    override fun getPlaceholderValue(): String = "20.00"

    override fun getLabel(): String = "TPS"

    override fun getTextColor(): Color {
        if (!adaptiveColor.get() || tps < 0) {
            return super.getTextColor()
        }
        if (tps >= 18.0) {
            return highTpsColor.get()
        }
        if (tps >= 15.0) {
            return mediumTpsColor.get()
        }
        if (tps >= 12.0) {
            return lowTpsColor.get()
        }
        return criticalTpsColor.get()
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(adaptiveColor)
        options.add(highTpsColor)
        options.add(mediumTpsColor)
        options.add(lowTpsColor)
        options.add(criticalTpsColor)
        return options
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "tpshud")
        private val FORMATTER: NumberFormat = DecimalFormat("#0.00")
    }
}
