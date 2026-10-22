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

import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.bridge.util.AxoText.Color.AQUA
import io.github.axolotlclient.bridge.util.AxoText.Color.BLUE
import io.github.axolotlclient.bridge.util.AxoText.Color.DARK_AQUA
import io.github.axolotlclient.bridge.util.AxoText.Color.DARK_GREEN
import io.github.axolotlclient.bridge.util.AxoText.Color.DARK_PURPLE
import io.github.axolotlclient.bridge.util.AxoText.Color.DARK_RED
import io.github.axolotlclient.bridge.util.AxoText.Color.GOLD
import io.github.axolotlclient.bridge.util.AxoText.Color.GREEN
import io.github.axolotlclient.bridge.util.AxoText.Color.GRAY
import io.github.axolotlclient.bridge.util.AxoText.Color.LIGHT_PURPLE
import io.github.axolotlclient.bridge.util.AxoText.Color.RED
import io.github.axolotlclient.bridge.util.AxoText.Color.WHITE
import io.github.axolotlclient.bridge.util.AxoText.Color.YELLOW
import io.github.axolotlclient.bridge.util.AxoText.literal
import java.util.stream.IntStream

object BedwarsPrestige {
    private val RAINBOW = listOf(RED, GOLD, YELLOW, GREEN, AQUA, LIGHT_PURPLE, DARK_PURPLE)

    @JvmStatic
    fun format(level: Int): AxoText {
        val levelString = level.toString() + "☆"
        return when (level / 100) {
            0 -> literal(levelString).`br$color`(GRAY)
            1 -> literal(levelString).`br$color`(WHITE)
            2 -> literal(levelString).`br$color`(GOLD)
            3 -> literal(levelString).`br$color`(AQUA)
            4 -> literal(levelString).`br$color`(DARK_GREEN)
            5 -> literal(levelString).`br$color`(DARK_AQUA)
            6 -> literal(levelString).`br$color`(DARK_RED)
            7 -> literal(levelString).`br$color`(LIGHT_PURPLE)
            8 -> literal(levelString).`br$color`(BLUE)
            9 -> literal(levelString).`br$color`(DARK_PURPLE)
            else -> IntStream.range(0, levelString.length)
                .mapToObj { x -> literal(levelString.substring(x, x + 1)).`br$color`(RAINBOW[x % RAINBOW.size]) }
                .reduce(literal(""), AxoText.Mutable::`br$append`)
        }
    }
}
