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

import java.time.LocalDateTime
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class IRLTimeHud : SimpleTextHudEntry(true) {
    // https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
    private var formatter: DateTimeFormatter? = null
    private var error = false

    private val format: StringOption = StringOption(
        "dateformat",
        "HH:mm:ss",
        { value: String -> updateDateTimeFormatter(value) }
    )

    override fun getId(): AxoIdentifier = ID

    private fun updateDateTimeFormatter(value: String) {
        try {
            formatter = DateTimeFormatter.ofPattern(value)
            error = false
        } catch (e: Exception) {
            error = true
            formatter = null
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(format)
        return options
    }

    override fun getValue(): String {
        if (error) {
            return "Error Compiling!"
        }
        if (formatter == null) {
            updateDateTimeFormatter(format.get())
            return getPlaceholderValue()
        }
        return formatter!!.format(ZonedDateTime.now())
    }

    override fun getPlaceholderValue(): String {
        if (error) {
            return "Error Compiling!"
        }
        if (formatter == null) {
            updateDateTimeFormatter(format.get())
            return getPlaceholderValue()
        }
        return formatter!!.format(LocalDateTime.of(2020, Month.AUGUST, 22, 14, 28, 32, 1595135))
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "irltimehud")
    }
}
