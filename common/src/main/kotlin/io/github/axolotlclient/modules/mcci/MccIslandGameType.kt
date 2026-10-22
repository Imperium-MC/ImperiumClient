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

package io.github.axolotlclient.modules.mcci


enum class MccIslandGameType(
    @get:JvmName("getName") val prettyName: String,
    val serverType: String,
    val gameName: String
) {
    LOBBY("Lobby", "lobby"),
    PARKOUR_WARRIOR("Parkour Warrior", "parkour-warrior", "parkour_warrior"),
    TGTTOS("To Get To The Other Side", "tgttos", "tgttos"),
    HOLE_IN_THE_WALL("Hole In The Wall", "hole-in-the-wall", "hole_in_the_wall"),
    ROCKET_SPLEEF_RUSH("Rocket Spleef Rush", "rocket-spleef", "rocket_spleef"),
    DYNABALL("Dynaball", "dynaball", "dynaball"),
    SKY_BATTLE("Sky Battle", "sky-battle", "sky_battle"),
    BATTLE_BOX("Battle Box", "battle-box", "battle_box");

    constructor(name: String, serverType: String) : this(name, serverType, "")

    companion object {
        private val byServerType = entries.associateBy { it.serverType }

        @JvmStatic
        fun fromLocation(data: MccIslandLocationData): MccIslandGameType =
            entries.first { data.server.startsWith(it.serverType) }

        @JvmStatic
        fun getServerType(type: String): MccIslandGameType? = byServerType[type]
    }
}
