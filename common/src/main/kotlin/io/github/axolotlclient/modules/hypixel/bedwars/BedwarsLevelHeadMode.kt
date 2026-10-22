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

import java.util.function.Function

enum class BedwarsLevelHeadMode(private val titleSupplier: Function<BedwarsPlayerStats, String>) {
    FINAL_KILLS(Function { stats -> "§7Final Kills (Total): §f" + stats.getFinalKills() }),
    FINAL_DEATHS(Function { stats -> "§7Final Deaths (Total): §f" + stats.getFinalDeaths() }),
    BEDS_BROKEN(Function { stats -> "§7Beds Broken (Total): §f" + stats.getBedsBroken() }),
    GAME_KILLS_GAME_DEATHS(Function { stats -> "§7Kills (Game): §f" + stats.getGameKills() + " §7Deaths (Game): §f" + stats.getGameDeaths() }),
    KILLS(Function { stats -> "§7Kills (Total): §f" + stats.getKills() }),
    DEATHS(Function { stats -> "§7Deaths (Total): §f" + stats.getDeaths() }),
    GAME_FINAL_KILLS(Function { stats -> "§7Final Kills (Game): §f" + stats.getGameFinalKills() }),
    GAME_BEDS_BROKEN(Function { stats -> "§7Beds Broken (Game): §f" + stats.getGameBedsBroken() }),
    GAME_DEATHS(Function { stats -> "§7Deaths (Game): §f" + stats.getGameDeaths() }),
    GAME_KILLS(Function { stats -> "§7Kills (Game): §f" + stats.getGameKills() }),
    LOSSES(Function { stats -> "§7Losses: §f" + stats.getLosses() }),
    WINS(Function { stats -> "§7Wins: §f" + stats.getWins() }),
    STARS(Function { stats -> "§7Stars: §f" + stats.getStars() }),
    FKDR(Function { stats -> "§7FKDR: §f" + stats.getFKDR() }),
    BBLR(Function { stats -> "§7BBLR: §f" + stats.getFKDR() });

    fun apply(stats: BedwarsPlayerStats): String = titleSupplier.apply(stats)

    companion object {
        private val modes = entries.associateBy { it.name }

        @JvmStatic
        fun get(mode: String): BedwarsLevelHeadMode? = modes[mode]
    }
}
