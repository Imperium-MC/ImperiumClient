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

class SessionStatistics {
    @JvmField
    var gamesPlayed = 0
    @JvmField
    var wins = 0
    @JvmField
    var losses = 0
    @JvmField
    var kills = 0
    @JvmField
    var deaths = 0
    @JvmField
    var finalKills = 0
    @JvmField
    var finalDeaths = 0
    @JvmField
    var bedsBroken = 0
    @JvmField
    var bedsLost = 0
    @JvmField
    var winstreak = 0

    fun getGamesPlayed(): Int = gamesPlayed

    fun getWins(): Int = wins

    fun getLosses(): Int = losses

    fun getKills(): Int = kills

    fun getDeaths(): Int = deaths

    fun getFinalKills(): Int = finalKills

    fun getFinalDeaths(): Int = finalDeaths

    fun getBedsBroken(): Int = bedsBroken

    fun getBedsLost(): Int = bedsLost

    fun getWinstreak(): Int = winstreak

    fun win() {
        wins++
        winstreak += 1
    }

    fun loose() {
        losses++
        winstreak = 0
    }

    fun gamePlayed() {
        gamesPlayed++
    }

    fun addDeath(finalDeath: Boolean) {
        if (finalDeath) finalDeaths++ else deaths++
    }

    fun addKill(finalKill: Boolean) {
        if (finalKill) finalKills++ else kills++
    }

    fun addBrokenBed() {
        bedsBroken++
    }

    fun bedLost() {
        bedsLost++
    }

    fun getFkdr(): Float = finalKills.toFloat() / finalDeaths

    fun getKdr(): Float = kills.toFloat() / deaths
}
