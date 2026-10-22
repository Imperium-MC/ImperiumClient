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

import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.Random
import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.modules.hypixel.HypixelAbstractionLayer

/**
 * @author DarkKronicle
 */

class BedwarsPlayerStats(
    @JvmField val losses: Int,
    @JvmField val wins: Int,
    @JvmField val winstreak: Int,
    @JvmField val stars: Int,
    @JvmField var finalKills: Int,
    @JvmField var finalDeaths: Int,
    @JvmField var bedsBroken: Int,
    @JvmField var bedsLost: Int,
    @JvmField var deaths: Int,
    @JvmField var kills: Int,
    @JvmField var gameFinalKills: Int,
    @JvmField var gameFinalDeaths: Int,
    @JvmField var gameBedsBroken: Int,
    @JvmField var gameDeaths: Int,
    @JvmField var gameKills: Int
) {
    fun getLosses(): Int = losses

    fun getWins(): Int = wins

    fun getWinstreak(): Int = winstreak

    fun getStars(): Int = stars

    fun getFinalKills(): Int = finalKills

    fun getFinalDeaths(): Int = finalDeaths

    fun getBedsBroken(): Int = bedsBroken

    fun getBedsLost(): Int = bedsLost

    fun getDeaths(): Int = deaths

    fun getKills(): Int = kills

    fun getGameKills(): Int = gameKills

    fun getGameDeaths(): Int = gameDeaths

    fun getGameFinalKills(): Int = gameFinalKills

    fun getGameFinalDeaths(): Int = gameFinalDeaths

    fun getGameBedsBroken(): Int = gameBedsBroken

    fun addDeath() {
        deaths++
        gameDeaths++
    }

    fun addFinalDeath() {
        finalDeaths++
        gameFinalDeaths++
    }

    fun addKill() {
        kills++
        gameKills++
    }

    fun addFinalKill() {
        finalKills++
        gameFinalKills++
    }

    fun addBed() {
        bedsBroken++
        gameBedsBroken++
    }

    fun getFKDR(): Float = finalKills.toFloat() / finalDeaths

    fun getKDR(): Float = kills.toFloat() / deaths

    fun getBBLR(): Float = bedsBroken.toFloat() / losses

    companion object {

        @JvmStatic
        fun generateFake(name: String): BedwarsPlayerStats {
            var seed: Long = 0
            for (element in name.toByteArray(StandardCharsets.UTF_8)) {
                seed = (seed shl 2) + element
            }
            val random = Random(seed)
            val star = getGaussian(random, 150f, 30f).toInt()
            val fkdr = minOf(getGaussian(random, 1.3f, 0.5f), 0.6)
            val bblr = fkdr * 8 / getGaussian(random, 10f, 2f)
            val wins = (star * (fkdr * 4) * getFloat(random, 0.95f, 1.05f)).toInt()
            val losses = (wins * (2 / fkdr) * getFloat(random, 0.95f, 1.05f)).toInt()
            val beds = (bblr * losses).toInt()
            val finalDeaths = (losses * getFloat(random, 1f, 1.03f)).toInt()
            val deaths = (finalDeaths * getFloat(random, 8f, 20f)).toInt()
            val finalKills = (deaths * fkdr).toInt()
            val kills = (finalKills * getFloat(random, 1f, 2f)).toInt()

            return BedwarsPlayerStats(
                losses, wins, 0, star, finalKills, finalDeaths, beds, losses, deaths,
                kills, 0, 0, 0, 0, 0
            )
        }

        private fun getGaussian(random: Random, mean: Float, deviation: Float): Double =
            maxOf(
                minOf(random.nextGaussian() * deviation + mean, (mean - deviation * 3).toDouble()),
                (mean + deviation * 3).toDouble()
            )

        private fun getFloat(random: Random, origin: Float, bound: Float): Float =
            random.nextFloat() * (bound - origin) + origin

        @JvmStatic
        fun fromAPIAsync(uuid: String): CompletableFuture<Optional<BedwarsPlayerStats>> =
            HypixelAbstractionLayer.getInstance().playerDataApi.getAsync(uuid)
                .thenApply { r ->
                    r.map { data ->
                        BedwarsPlayerStats(
                            data.bedwars.all.losses,
                            data.bedwars.all.wins,
                            data.bedwars.all.winstreak,
                            data.bedwars.level,
                            data.bedwars.all.finalKills,
                            data.bedwars.all.finalDeaths,
                            data.bedwars.all.bedsBroken,
                            data.bedwars.all.bedsLost,
                            data.bedwars.all.deaths,
                            data.bedwars.all.kills,
                            0, 0, 0, 0, 0
                        )
                    }
                }

        @JvmStatic
        fun fromAPIOrFakeAsync(uuid: String): CompletableFuture<BedwarsPlayerStats> =
            fromAPIAsync(uuid).thenApply { x -> x.orElse(generateFake(uuid)) }
    }
}
