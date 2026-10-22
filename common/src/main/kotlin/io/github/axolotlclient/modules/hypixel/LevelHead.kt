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

package io.github.axolotlclient.modules.hypixel

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.api.API
import java.util.function.Function
import java.util.function.Supplier

import io.github.axolotlclient.util.CachedAPI
import io.github.axolotlclient.util.ClientColors

class LevelHead : AbstractHypixelMod {
    @JvmField
    val enabled = BooleanOption("enabled", "levelhead.enabled.tooltip", false)
    @JvmField
    val background = BooleanOption("background", false)
    @JvmField
    val textColor = ColorOption("textcolor", ClientColors.GOLD)
    @JvmField
    val mode = EnumOption("levelHeadMode", Mode::class.java, Mode.NETWORK)
    private val category: OptionCategory = OptionCategory.create("levelhead")

    override fun init() {
        category.add(enabled)
        category.add(textColor)
        category.add(background)
        category.add(mode)
    }

    override fun getCategory(): OptionCategory = category

    fun getDisplayString(uuid: String): String = getDisplayString(mode.get(), API.sanitizeUUID(uuid))

    enum class Mode(@JvmField val api: CachedAPI<String, Int>) {
        NETWORK(HypixelAbstractionLayer.getInstance().networkLevelApi),
        BEDWARS(HypixelAbstractionLayer.getInstance().bedwarsLevelApi),
        SKYWARS(HypixelAbstractionLayer.getInstance().skywarsExpApi);
    }

    companion object {
        private val Instance = LevelHead()

        @JvmStatic
        fun getInstance(): LevelHead = Instance

        @JvmStatic
        fun getDisplayString(mode: Mode, uuid: String): String =
            "Level: " + mode.api.getAsyncNow(uuid).map(
                Supplier { "..." }, Supplier { "???" },
                Function { value: Int ->
                    if (mode == Mode.BEDWARS) {
                        "$value☆"
                    } else {
                        value.toString()
                    }
                }
            )
    }
}
