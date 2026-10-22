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

import java.util.regex.Matcher
import java.util.regex.Pattern

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption

/**
 * @author DarkKronicle
 */
enum class BedwarsDeathType(val inner: StringOption, val patterns: Array<Pattern>) {
    COMBAT(createOption("combat", "rekt"), BedwarsMessages.COMBAT_KILL),
    VOID(createOption("void", "yeeted into void"), BedwarsMessages.VOID_KILL),
    PROJECTILE(createOption("projectile", "shot"), BedwarsMessages.PROJECTILE_KILL),
    FALL(createOption("fall", "fall"), BedwarsMessages.FALL_KILL),
    GOLEM(createOption("golem", "golem moment"), BedwarsMessages.GOLEM_KILL),
    SELF_VOID(createOption("self_void", "voided"), arrayOf<Pattern>(BedwarsMessages.SELF_VOID)),
    SELF_UNKNOWN(createOption("self_unknown", "died"), arrayOf<Pattern>(BedwarsMessages.SELF_UNKNOWN));

    fun interface BedwarsDeathMatch {
        fun onMatch(type: BedwarsDeathType, matcher: Matcher)
    }

    companion object {
        @JvmStatic
        val options: OptionCategory = OptionCategory.create("bedwars.deathType")

        init {
            entries.map { it.inner }.forEach { options.add(it) }
        }

        @JvmStatic
        fun getDeath(rawMessage: String, ifPresent: BedwarsDeathMatch): Boolean {
            for (type in entries) {
                if (BedwarsMessages.matched(type.patterns, rawMessage) { m -> ifPresent.onMatch(type, m) }) {
                    return true
                }
            }
            return false
        }

    }
}

private fun createOption(type: String, def: String): StringOption =
    StringOption("bedwars.deathType.$type", def)
