/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.util.AxoText
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Supplier

object CommonUtil {
    @JvmField
    var lastgame: String? = null

    @JvmField
    var game: String? = null

    @JvmStatic
    fun toRoman(number: Int): String =
        if (number > 0) {
            "I".repeat(number).replace("IIIII", "V").replace("IIII", "IV")
                .replace("VV", "X").replace("VIV", "IX").replace("XXXXX", "L").replace("XXXX", "XL")
                .replace("LL", "C").replace("LXL", "XC").replace("CCCCC", "D").replace("CCCC", "CD")
                .replace("DD", "M").replace("DCD", "CM")
        } else {
            ""
        }

    @JvmStatic
    fun <T> make(factory: Supplier<T>): T = factory.get()

    @JvmStatic
    fun <T> make(obj: T, initializer: Consumer<T>): T = obj.also(initializer::accept)

    @JvmStatic
    fun <T> memoize(delegate: Supplier<T>): Supplier<T> = object : Supplier<T> {
        @Volatile
        var initialized = false

        var value: T? = null

        override fun get(): T {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        val t = delegate.get()
                        value = t
                        initialized = true
                        return t
                    }
                }
            }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
    }

    @JvmStatic
    fun getCurrentServerAddress(): String? {
        val minecraft = AxoMinecraftClient.getInstance()

        if (minecraft.`br$isLocalServer`()) {
            return null
        }

        return minecraft.`br$getServerAddress`()
    }

    @JvmStatic
    fun getGame(): String {
        val sidebar = AxoMinecraftClient.getInstance().`br$getSidebar`()

        val address = AxoMinecraftClient.getInstance().`br$getServerAddress`()

        game = if (sidebar.isEmpty()) {
            ""
        } else if (address != null && address.lowercase().contains(sidebar[0].lowercase())) {
            val last = sidebar[sidebar.size - 1]
            when {
                last.lowercase(Locale.ROOT).contains(address.lowercase(Locale.ROOT)) || last.contains("Playtime") ->
                    "In Lobby"
                last.contains("--------") -> "Playing Bridge Practice"
                else -> "Playing $last"
            }
        } else {
            "Playing " + sidebar[0]
        }

        if (lastgame != game && game?.isEmpty() == true) {
            game = lastgame
        } else {
            lastgame = game
        }

        if (game == null) {
            game = ""
        }

        return AxoText.strip(game!!)
    }
}
