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

@JvmRecord
data class PlayerData(
    val name: String, val bedwars: Bedwars, val skywars: Skywars, val duels: DuelsData,
    val rank: String, val rankFormatted: String, val level: Double, val karma: Int
) {
    fun formattedName(): String {
        val builder = StringBuilder()
        builder.append(rankFormatted)
        if (rankFormatted.length > 2) {
            builder.append(" ")
        }
        return builder.append(name).append("§r").toString()
    }

    @JvmRecord
    data class Bedwars(
        val level: Int, val all: GameData, val core: CombinedGameData, val solo: GameData?, val doubles: GameData?,
        val trios: GameData?, val fours: GameData?, val fourVFour: GameData?, val dreams: CombinedGameData?,
        val castle: GameData?, val doublesLucky: GameData?, val foursLucky: GameData?, val doublesUltimate: GameData?,
        val foursUltimate: GameData?, val doublesArmed: GameData?, val foursArmed: GameData?, val doublesRush: GameData?,
        val foursRush: GameData?, val doublesSwap: GameData?, val foursSwap: GameData?
    ) {
        @JvmRecord
        data class GameData(
            override val kills: Int, override val deaths: Int, override val wins: Int, override val losses: Int,
            val winstreak: Int, override val finalKills: Int, override val finalDeaths: Int,
            override val bedsBroken: Int, override val bedsLost: Int
        ) : BedwarsGameData

        @JvmRecord
        data class CombinedGameData(
            override val kills: Int, override val deaths: Int, override val wins: Int, override val losses: Int,
            override val finalKills: Int, override val finalDeaths: Int,
            override val bedsBroken: Int, override val bedsLost: Int
        ) : BedwarsGameData

        interface BedwarsGameData : WLR, KDR {
            val bedsBroken: Int

            val bedsLost: Int

            val finalDeaths: Int

            val finalKills: Int

            fun fkdr(): Float = finalKills.toFloat() / finalDeaths

            fun bblr(): Float = bedsBroken.toFloat() / bedsLost
        }
    }

    @JvmRecord
    data class Skywars(
        val level: String, val exp: Int, val all: GameData, val core: GameData, val solo: ModeData,
        val team: ModeData, val mega: MegaModeData, val ranked: GameData, val winstreak: Int
    ) {
        @JvmRecord
        data class ModeData(val normal: GameData, val insane: GameData)

        @JvmRecord
        data class MegaModeData(val normal: GameData, val doubles: GameData)

        @JvmRecord
        data class GameData(override val kills: Int, override val deaths: Int, override val wins: Int, override val losses: Int) : KDR, WLR
    }

    @JvmRecord
    data class DuelsData(val modes: Map<String, DuelsGameData>) {
        @JvmRecord
        data class DuelsGameData(override val kills: Int, override val deaths: Int, override val wins: Int, override val losses: Int, val winstreak: Int) :
            KDR, WLR
    }

    interface KDR {
        val kills: Int

        val deaths: Int

        fun kdr(): Float = kills.toFloat() / deaths
    }

    interface WLR {
        val wins: Int

        val losses: Int

        fun wlr(): Float = wins.toFloat() / losses
    }
}
