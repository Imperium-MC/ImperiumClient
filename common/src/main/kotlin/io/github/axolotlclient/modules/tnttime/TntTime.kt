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

package io.github.axolotlclient.modules.tnttime

import java.text.DecimalFormat

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.util.options.ForceableBooleanOption

class TntTime : AbstractCommonModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    private val category = OptionCategory.create("tnttime")
    private val decimalPlaces = IntegerOption("decimalplaces", 2, 0, 6)
    private var format: DecimalFormat? = null
    private var decimals = 0

    // Fuse colour stages (Alpine profile M6): dynamic_color off pins the text to the
    // style's regular colour; on (default) the stage colours below apply.
    private val dynamicColor = BooleanOption("tnttime.dynamic_color", true)
    private val earlyFuseColor = ColorOption("tnttime.early_fuse_color", Color(255, 255, 255).immutable())
    private val warningFuseColor = ColorOption("tnttime.warning_fuse_color", Color(255, 255, 85).immutable())
    private val criticalFuseColor = ColorOption("tnttime.critical_fuse_color", Color(255, 85, 85).immutable())

    override fun init() {
        category.add(enabled, decimalPlaces, dynamicColor, earlyFuseColor, warningFuseColor, criticalFuseColor)
        AxolotlClientCommon.getInstance().getConfig().rendering.add(category)
    }

    override fun tick() {
        if (decimalPlaces.get() != decimals || format == null) {
            val string = StringBuilder("#0")
            if (decimalPlaces.get() > 0) {
                string.append(".")
                string.append("0".repeat(maxOf(0, decimalPlaces.get())))
            }
            format = DecimalFormat(string.toString())
            decimals = decimalPlaces.get()
        }
    }

    fun getFuseTime(time: Float): AxoText {
        val secs = time / 20f
        val text = AxoText.literal(format!!.format(secs).toString())
        if (dynamicColor.get()) {
            text.`br$color`(getCurrentColor(secs))
        }
        return text
    }

    /**
     * Fuse colour stages: the early colour while more than 5 seconds remain, the warning
     * colour from 5 down to 3, the critical colour for the last 3 seconds.
     */
    private fun getCurrentColor(seconds: Float): Color =
        when {
            seconds > 5.0 -> earlyFuseColor.get()
            seconds > 3.0 -> warningFuseColor.get()
            else -> criticalFuseColor.get()
        }

    companion object {
        private val instance = TntTime()

        @JvmStatic
        fun getInstance(): TntTime = instance
    }
}
