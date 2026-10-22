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
import java.util.Locale

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.math.Vec3
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class SpeedHud : SimpleTextHudEntry(true) {
    private val horizontal = BooleanOption("horizontal", true)
    private val unit: EnumOption<SpeedUnit> = EnumOption("speedhud.unit", SpeedUnit::class.java, SpeedUnit.BPS)

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(horizontal)
        options.add(unit)
        return options
    }

    override fun getValue(): String {
        val player: AxoPlayer = client.`br$getPlayer`() ?: return getPlaceholderValue()

        val entity = player.`br$getVehicle`() ?: player

        var vec = entity.`br$getVelocity`()
        if (horizontal.get() || entity.`br$isOnGround`() && vec.y < 0) {
            vec = vec.y(0.0)
        }

        return FORMATTER.format(vec.len() * unit.get().perTickToUnit)
    }

    override fun getPlaceholderValue(): String = "4.35"

    override fun getLabel(): String = unit.get().label

    /** Display units (Alpine "unit", M6). Blocks are metres, so BPS and MPS differ only in label.  */
    enum class SpeedUnit(@JvmField val label: String, @JvmField val perTickToUnit: Double) {
        BPS("BPS", 20.0),
        MPS("m/s", 20.0),
        KMH("km/h", 72.0),
        MPH("mph", 44.7387);

        override fun toString(): String = "speedhud.unit." + name.lowercase(Locale.ROOT)
    }

    companion object {
        private val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "speedhud")
        private val FORMATTER: NumberFormat = DecimalFormat("#0.00")
    }
}
