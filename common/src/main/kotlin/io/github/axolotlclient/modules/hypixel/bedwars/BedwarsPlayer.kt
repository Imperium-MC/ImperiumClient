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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.hypixel.bedwars

import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.Platform

/**
 * @author DarkKronicle
 */

class BedwarsPlayer(val team: BedwarsTeam, var profile: AxoPlayerListEntry, var number: Int) {
    var alive: Boolean = true
        private set
    var disconnected: Boolean = false
        private set
    var bed: Boolean = true
    var stats: BedwarsPlayerStats? = null
        private set
    private var triedStats = false
    var tickAlive: Int = -1
        private set

    fun getColoredTeamNumber(format: String): String =
        team.getColorSection() + format + team.prefix + number

    fun getColoredTeamNumber(): String = team.getColorSection() + team.prefix + number

    fun getName(): String = profile.`br$getName`()

    fun getColoredName(): String = team.getColorSection() + getName()

    fun getTabListDisplay(): String {
        if (alive) {
            if (bed) {
                return team.getColorSection() + "§l" + team.prefix + number + " " + getColoredName()
            }
            return team.getColorSection() + "§l" + team.prefix + number + team.getColorSection() + "§o " + getName()
        }
        if (disconnected) {
            return team.getColorSection() + "§l§m" + team.prefix + number + "§7 §o§n" + getName()
        }
        return team.getColorSection() + "§l§m" + team.prefix + number + "§7 §m" + getName()
    }

    fun updateListEntry(entry: AxoPlayerListEntry) {
        this.profile = entry
    }

    fun isFinalKilled(): Boolean =
        tickAlive < 0 && !bed && !alive || (!bed && disconnected)

    fun tick(currentTick: Int) {
        if (stats == null && !triedStats) {
            triedStats = true
            BedwarsPlayerStats.fromAPIOrFakeAsync(UUIDHelper.toUndashed(profile.`br$getId`()))
                .thenAccept { stat -> stats = stat }
        }
        if (alive || tickAlive < 0) {
            return
        }
        if (currentTick >= tickAlive) {
            alive = true
            tickAlive = -1
        }
    }

    fun died() {
        if (!alive) {
            if (!bed) {
                tickAlive = -1
            }
            return
        }
        if (stats != null) {
            if (!bed) {
                stats!!.addFinalDeath()
            } else {
                stats!!.addDeath()
            }
        }
        alive = false
        if (!bed) {
            tickAlive = -1
            return
        }
        tickAlive = Platform.tickCount() + 20 * 5 // 5 second respawn
    }

    fun disconnected() {
        if (stats != null) {
            if (!bed) {
                stats!!.addFinalDeath()
            } else {
                stats!!.addDeath()
            }
        }
        disconnected = true
        tickAlive = -1
        alive = false
    }

    fun reconnected() {
        disconnected = false
        tickAlive = Platform.tickCount() + 20 * 10 // 10 second respawn
    }

    fun killed(finalKill: Boolean) {
        if (stats != null) {
            if (finalKill) {
                stats!!.addFinalKill()
            }
            stats!!.addKill()
        }
    }
}
