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

/**
 * Port of
 * <a href="https://github.com/Plancke/hypixel-php/blob/c6d346c0366180bcd0a3f1b769f08500a31d283f/src/util/games/skywars/ExpCalculator.php">Plancke's Exp calculator</a>
 * to Kotlin from PHP
 * <p>License: MIT</p>
 */
object ExpCalculator {
    private val EASY_LEVEL_EXP = intArrayOf(
        0, // Level 1
        20, //20
        50, //70
        80, //150
        100, //250
        250, //500
        500, //1000
        1000, //2000
        1500, //3500
        2500, //6000
        4000, //10000
        5000 //15000
    )
    private const val EXP_PER_LEVEL = 10000

    @JvmStatic
    fun getProgressCurrentLevel(exp: Int): Float {
        val level = getLevelForExp(exp)
        val levelExp = getTotalExpForLevel(level)
        return (exp - levelExp).toFloat()
    }

    @JvmStatic
    fun getLevelForExp(exp: Int): Float {
        val easyLevelsCount = EASY_LEVEL_EXP.size

        var easyLevelExp = 0
        for (i in 1..easyLevelsCount) {
            val expPerLevel = getExpForLevel(i)
            easyLevelExp += expPerLevel
            if (exp < easyLevelExp) {
                return (i - 1).toFloat() //57965
            }
        }
        val extraLevels = (exp - easyLevelExp) / EXP_PER_LEVEL
        return (easyLevelsCount + extraLevels).toFloat()
    }

    @JvmStatic
    fun getTotalExpForLevel(level: Float): Int {
        val easyLevelsCount = EASY_LEVEL_EXP.size

        var totalExp = 0
        val easyLevels = Math.min(level, easyLevelsCount.toFloat())
        for (i in 0 until easyLevels.toInt()) {
            totalExp += EASY_LEVEL_EXP[i]
        }

        if (level > easyLevelsCount) {
            val extraLevels = level - easyLevelsCount
            totalExp += (extraLevels * EXP_PER_LEVEL).toInt()
        }
        return totalExp
    }

    @JvmStatic
    fun getExpForLevel(level: Int): Int =
        if (level <= EASY_LEVEL_EXP.size) {
            EASY_LEVEL_EXP[level - 1]
        } else {
            EXP_PER_LEVEL
        }
}
