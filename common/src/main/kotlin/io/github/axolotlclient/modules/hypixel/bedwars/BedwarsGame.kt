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

import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.UUID

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.scores.AxoTeam
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hypixel.bedwars.upgrades.BedwarsTeamUpgrades
import io.github.axolotlclient.util.ClientColors

/**
 * @author DarkKronicle
 */

// TODO: localize messages, maybe add regexes for text recognition to HypixelMessages?
//	Currently this only works if Hypixel's language is set to English
class BedwarsGame(private val mod: BedwarsMod) {
    private val players: MutableMap<String, BedwarsPlayer> = HashMap()
    private val playersById: MutableMap<UUID, BedwarsPlayer> = HashMap()
    val playersByTeam: MutableMap<BedwarsTeam, MutableList<AxoPlayerListEntry>> = HashMap()
    private val mc: AxoMinecraftClient = AxoMinecraftClient.getInstance()
    val upgrades: BedwarsTeamUpgrades = BedwarsTeamUpgrades()
    private var won: BedwarsTeam? = null
    private var wonTick = -1
    private var seconds = 0
    var topBarText: AxoText = AxoText.literal("")
        private set
    var bottomBarText: AxoText = AxoText.literal("")
        private set
    private var me: BedwarsPlayer? = null
    var isStarted: Boolean = false
        private set
    private var lastKill: BedwarsPlayer? = null
    private var lastKiller: BedwarsPlayer? = null

    val self: BedwarsPlayer?
        get() = me

    fun onStart() {
        players.clear()
        playersById.clear()
        playersByTeam.clear()
        for (player in mc.`br$getOnlinePlayers`()) {
            val name = Platform.getTabNameFor(player).replace("§.".toRegex(), "")
            if (name.isEmpty() || name[1] != ' ') {
                continue
            }
            val team = BedwarsTeam.fromPrefix(name[0]).orElse(null) ?: continue
            playersByTeam.compute(team) { _, entries ->
                if (entries == null) {
                    arrayListOf(player)
                } else {
                    entries.add(player)
                    entries
                }
            }
        }
        for (teamPlayerList in playersByTeam.entries) {
            teamPlayerList.value.sortWith(compareBy { it.`br$getName`() })
            val value = teamPlayerList.value
            for (i in value.indices) {
                val e = value[i]
                val p = BedwarsPlayer(teamPlayerList.key, e, i + 1)
                if (mc.`br$getPlayer`()?.`br$getUuid`() == e.`br$getId`()) {
                    me = p
                }
                players[e.`br$getName`()] = p
                playersById[e.`br$getId`()] = p
            }
        }
        this.isStarted = true

        BedwarsMod.GAME_START_EVENT.invoker().accept(this)
    }

    private fun calculateTopBarText(): AxoText {
        var topBar = getFormattedTime()
        if (me!!.stats != null) {
            topBar += "\n" +
                "K: " + me!!.stats!!.getGameKills() +
                " D: " + me!!.stats!!.getGameDeaths() +
                " B: " + me!!.stats!!.getGameBedsBroken()
        }
        return AxoText.literal(topBar)
    }

    private fun calculateBottomBarText(): AxoText =
        AxoText.literal("")
            .`br$append`("Last Kill: ", AxoText.Color.AQUA)
            .`br$append`(if (lastKill == null) "N/A" else lastKill!!.getColoredName())
            .`br$append`(" Last Killed By: ", AxoText.Color.AQUA)
            .`br$append`(if (lastKiller == null) "N/A" else lastKiller!!.getColoredName())

    fun getFormattedTime(): String {
        val minute = seconds / 60
        val second = seconds % 60
        var time = "$minute:"
        time += if (second < 10) {
            "0$second"
        } else {
            second.toString()
        }
        return time
    }

    /*
	 * make sure to call `refreshPlayerNumbers` after calling this method!
	 */
    private fun addPlayer(entry: AxoPlayerListEntry): Optional<BedwarsPlayer> {
        val listName = Platform.getTabNameFor(entry).replace("§.".toRegex(), "")
        if (listName.isEmpty() || listName[1] != ' ') {
            return Optional.empty()
        }
        val team = BedwarsTeam.fromPrefix(listName[0]).orElse(null) ?: return Optional.empty()
        playersByTeam.compute(team) { _, entries ->
            if (entries == null) {
                arrayListOf(entry)
            } else {
                entries.add(entry)
                entries
            }
        }
        playersByTeam[team]!!.sortWith(compareBy { it.`br$getName`() })
        return Optional.of(BedwarsPlayer(team, entry, 0))
    }

    private fun refreshPlayerNumbers(team: BedwarsTeam, players: Collection<BedwarsPlayer>) {
        val teamPlayers = playersByTeam[team] ?: return
        for (p in players) {
            if (p.team == team) {
                p.number = teamPlayers.indexOf(p.profile) + 1
            }
        }
    }

    fun getPlayer(uuid: UUID): Optional<BedwarsPlayer> =
        Optional.ofNullable(
            playersById.computeIfAbsent(uuid) {
                mc.`br$getOnlinePlayer`(uuid).flatMap { entry ->
                    val p = addPlayer(entry)
                    p.ifPresent { bedwarsPlayer ->
                        players[bedwarsPlayer.profile.`br$getName`()] = bedwarsPlayer
                        refreshPlayerNumbers(bedwarsPlayer.team, this.players.values)
                        BedwarsMod.PLAYER_ADD.invoker().accept(bedwarsPlayer)
                    }
                    p
                }.orElse(null)
            }
        )

    fun getPlayer(name: String): Optional<BedwarsPlayer> =
        Optional.ofNullable(
            players.computeIfAbsent(name) {
                mc.`br$getOnlinePlayer`(name).flatMap { entry ->
                    val p = addPlayer(entry)
                    p.ifPresent { bedwarsPlayer ->
                        playersById[bedwarsPlayer.profile.`br$getId`()] = bedwarsPlayer
                        refreshPlayerNumbers(bedwarsPlayer.team, playersById.values)
                        BedwarsMod.PLAYER_ADD.invoker().accept(bedwarsPlayer)
                    }
                    p
                }.orElse(null)
            }
        )

    private fun debug(message: String) {
        AxoMinecraftClient.getInstance().`br$sendToClient`(AxoText.literal("§b§lINFO:§8 $message"))
    }

    private fun died(event: ReceiveChatMessageEvent, player: BedwarsPlayer, killer: BedwarsPlayer?,
                     type: BedwarsDeathType, finalDeath: Boolean) {
        player.died()
        if (killer != null) {
            killer.killed(finalDeath)
        }
        if (mod.overrideMessages.get()) {
            event.newMessage = AxoText.literal(formatDeath(player, killer, type, finalDeath))
        }
        if (me == killer) {
            lastKill = player
            mod.sessionStats.addKill(finalDeath)
        } else if (me == player) {
            lastKiller = killer
            mod.sessionStats.addDeath(finalDeath)
        }
    }

    private fun formatDisconnect(disconnected: BedwarsPlayer): String =
        getPlayerFormatted(disconnected) + " §7§o/disconnected/"

    private fun formatReconnect(reconnected: BedwarsPlayer): String =
        getPlayerFormatted(reconnected) + " §7§o/reconnected/"

    private fun formatEliminated(team: BedwarsTeam): String {
        val message = StringBuilder(
            "§6§l§oTEAM ELIMINATED §8§l> " + team.getColorSection() + team.getName() + " Team §7/eliminated/ "
        )
        for (p in players.values.stream()
            .filter { b -> b.team == team }
            .sorted(java.util.Comparator.comparingInt { it.number })
            .toList()) {
            val stats = p.stats ?: continue
            message.append("\n")
                .append("§b")
                .append(stats.getStars())
                .append(" ")
                .append(p.getColoredName())
                .append("§7 Beds: §f")
                .append(stats.getBedsBroken())
                .append("§7 Finals: §f")
                .append(stats.getFinalKills())
                .append("§7 FKDR: §f")
                .append("%.2f".format(stats.getFKDR()))
                .append("§7 BBLR: §f")
                .append("%.2f".format(stats.getBBLR()))
        }
        return message.toString()
    }

    private fun formatBed(team: BedwarsTeam, breaker: BedwarsPlayer?): String {
        val playerFormatted = getPlayerFormatted(breaker!!)
        return "§6§l§oBED BROKEN §8§l> " + team.getColorSection() + team.getName() + " Bed §7/broken/ $playerFormatted" +
            if (breaker.stats == null || breaker.team != me!!.team) "" else
                " §6" + breaker.stats!!.getBedsBroken()
    }

    private fun formatDeath(player: BedwarsPlayer, killer: BedwarsPlayer?, type: BedwarsDeathType,
                            finalDeath: Boolean): String {
        var inner = type.inner.get()
        inner = if (finalDeath) {
            "§6§l/" + inner.uppercase(Locale.ROOT) + "/"
        } else {
            "§7/$inner/"
        }
        val playerFormatted = getPlayerFormatted(player)
        if (killer == null) {
            return "$playerFormatted $inner"
        }
        var killerFormatted = getPlayerFormatted(killer)
        if (finalDeath && killer.stats != null && killer.team == me!!.team) {
            killerFormatted += " §6" + killer.stats!!.getFinalKills()
        }
        return "$playerFormatted $inner $killerFormatted"
    }

    private fun getPlayerFormatted(player: BedwarsPlayer): String =
        player.getColoredTeamNumber() + " " + player.profile.`br$getName`()

    fun isTeamEliminated(team: BedwarsTeam): Boolean =
        players.values.filter { b -> b.team == team }.all { it.isFinalKilled() }

    fun onChatMessage(rawMessage: String, event: ReceiveChatMessageEvent) {
        try {
            if (mod.removeAnnoyingMessages.get() && BedwarsMessages.matched(
                    BedwarsMessages.ANNOYING_MESSAGES,
                    rawMessage
                ).isPresent
            ) {
                event.isCancelled = true
                return
            }
            if (BedwarsDeathType.getDeath(rawMessage) { type, m -> died(m, rawMessage, event, type) }) {
                return
            }
            if (BedwarsMessages.matched(BedwarsMessages.BED_DESTROY, rawMessage) { m ->
                    val player =
                        BedwarsMessages.matched(BedwarsMessages.BED_BREAK, rawMessage)
                            .flatMap { m1 -> getPlayer(m1.group(1)) }
                    if (player.isEmpty) {
                        AxolotlClientCommon.getInstance().logger.warn("Unknown bed break message: $rawMessage")
                        //Notifications.getInstance().addStatus("bedwars.unknown_bed_break", "bedwars.unknown_message");
                        return@matched
                    }
                    val team = BedwarsTeam.fromName(m.group(1)).orElse(me!!.team)
                    bedDestroyed(event, team, player.get())
                }) {
                return
            }
            if (BedwarsMessages.matched(BedwarsMessages.DISCONNECT, rawMessage) { m ->
                    getPlayer(m.group(1)).ifPresent { p -> disconnected(event, p) }
                }) {
                return
            }
            if (BedwarsMessages.matched(BedwarsMessages.RECONNECT, rawMessage) { m ->
                    getPlayer(m.group(1)).ifPresent { p -> reconnected(event, p) }
                }) {
                return
            }
            if (BedwarsMessages.matched(BedwarsMessages.GAME_END, rawMessage) { _ ->
                    this.won = players.values.stream().filter { p -> !p.isFinalKilled() }.findFirst()
                        .map { it.team }.orElse(null)
                    this.wonTick = Platform.tickCount() + 10
                }) {
                return
            }
            if (BedwarsMessages.matched(BedwarsMessages.TEAM_ELIMINATED, rawMessage) { m ->
                    BedwarsTeam.fromName(m.group(1)).ifPresent { t -> teamEliminated(event, t) }
                }) {
                return
            }
            upgrades.onMessage(rawMessage)
        } catch (e: Exception) {
            debug("Error: $e")
        }
    }

    private fun died(m: java.util.regex.Matcher, rawMessage: String, event: ReceiveChatMessageEvent, type: BedwarsDeathType) {
        val killed = getPlayer(m.group(1)).orElse(null)
        var killer: BedwarsPlayer? = null
        if (type != BedwarsDeathType.SELF_UNKNOWN && type != BedwarsDeathType.SELF_VOID) {
            killer = getPlayer(m.group(2)).orElse(null)
        }
        if (killed == null) {
            debug("Player " + m.group(1) + " was not found")
            return
        }
        died(event, killed, killer, type, BedwarsMessages.matched(BedwarsMessages.FINAL_KILL, rawMessage).isPresent)
    }

    private fun gameEnd(win: BedwarsTeam) {
        if (me == null) {
            BedwarsMod.getInstance().gameEnd()
            return
        }

        if (me!!.team == win) {
            mod.sessionStats.win()
        }

        BedwarsMod.getInstance().gameEnd()
    }

    private fun teamEliminated(event: ReceiveChatMessageEvent, team: BedwarsTeam) {
        // Make sure everyone is dead, just in case
        players.values.filter { b -> b.team == team }.forEach { b ->
            b.bed = false
            b.died()
        }
        if (mod.overrideMessages.get()) {
            event.newMessage = AxoText.literal(formatEliminated(team))
        }
        if (me!!.team == team) {
            mod.sessionStats.loose()
            mod.gameEnd()
        }
    }

    private fun bedDestroyed(event: ReceiveChatMessageEvent, team: BedwarsTeam, breaker: BedwarsPlayer?) {
        players.values.filter { b -> b.team == team }.forEach { it.bed = false }
        if (breaker != null && breaker.stats != null) {
            breaker.stats!!.addBed()
        }
        if (mod.overrideMessages.get()) {
            event.newMessage = AxoText.literal(formatBed(team, breaker))
        }
        if (me == breaker) {
            mod.sessionStats.addBrokenBed()
        } else if (me!!.team == team) {
            mod.sessionStats.bedLost()
        }
    }

    private fun disconnected(event: ReceiveChatMessageEvent, player: BedwarsPlayer) {
        player.disconnected()
        if (mod.overrideMessages.get()) {
            event.newMessage = AxoText.literal(formatDisconnect(player))
        }
    }

    private fun reconnected(event: ReceiveChatMessageEvent, player: BedwarsPlayer) {
        player.reconnected()
        if (mod.overrideMessages.get()) {
            event.newMessage = AxoText.literal(formatReconnect(player))
        }
    }

    fun onScoreboardRender(event: ScoreboardRenderEvent) {
        val scoreboard = event.objective.`br$getScoreboard`()
        val scores = scoreboard.`br$getScores`(event.objective)
        val filteredScores = scores.filter { score -> !score.`br$isHidden`() }
            .toMutableList() // explicitly declare the type since we require mutability
        Collections.reverse(filteredScores)
        if (filteredScores.size < 3) {
            return
        }
        val score = filteredScores[2]
        val owner = score.`br$getOwner`()!!
        val team = scoreboard.`br$getTeamOfMember`(owner)!!
        var timer = AxoText.strip(AxoTeam.`br$getMemberDisplayName`(team, owner)!!)
        if (!timer.contains(":")) {
            return
        }
        if (timer.contains(owner)) {
            timer = timer.replace(owner, "")
        }
        val seconds: Int
        try {
            seconds = timer.split(":")[1].substring(0, 2).toInt()
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("couldn't parse timer '$timer': ", e)
            return
        }
        val target = (60 - seconds) % 60
        if (this.seconds % 60 != target) {
            // Update seconds
            while (this.seconds % 60 != target) {
                updateClock()
            }
            if (me != null) {
                topBarText = calculateTopBarText()
                bottomBarText = calculateBottomBarText()
            }
        }
    }

    private fun updateClock() {
        this.seconds++
    }

    fun tick() {
        val currentTick = Platform.tickCount()
        if (won != null && currentTick >= wonTick) {
            gameEnd(won!!)
        }
        players.values.forEach { it.tick(currentTick) }
    }

    fun updateEntries(entries: List<AxoPlayerListEntry>) {
        // Update latencies and other information for entries
        entries.forEach { entry ->
            getPlayer(entry.`br$getName`()).ifPresent { player -> player.updateListEntry(entry) }
        }
    }

    fun getTabPlayerList(original: List<AxoPlayerListEntry>): List<AxoPlayerListEntry> {
        updateEntries(original)
        return players.values.filter { b -> !b.isFinalKilled() }
            .sortedWith { b1, b2 ->
                if (b1.team == b2.team) {
                    Integer.compare(b1.number, b2.number)
                } else {
                    Integer.compare(b1.team.ordinal, b2.team.ordinal)
                }
            }
            .map { it.profile }
    }

    fun getLevelHead(entity: io.github.axolotlclient.bridge.entity.AxoPlayer): String? {
        val player = getPlayer(entity.`br$getUuid`()).orElse(null) ?: return null
        val stats = player.stats ?: return null
        val mode = mod.bedwarsLevelHeadMode.get()
        return mode.apply(stats)
    }

    fun renderCustomScoreboardObjective(context: AxoRenderContext, playerName: String, objectiveValue: Int, y: Int, endX: Int) {
        val bedwarsPlayer = getPlayer(playerName).orElse(null) ?: return

        val render: String
        val color: Int
        if (!bedwarsPlayer.alive) {
            if (bedwarsPlayer.disconnected) {
                return
            }
            val tickTillLive = maxOf(0, bedwarsPlayer.tickAlive - Platform.tickCount())
            val secondsTillLive = tickTillLive / 20f
            render = "%.1f".format(secondsTillLive) + "s"
            color = Color(200, 200, 200).toInt()
        } else {
            color = ClientColors.blend(
                Color(255, 255, 255), Color(215, 0, 64),
                (1 - (objectiveValue / 20f))
            ).toInt()
            render = objectiveValue.toString()
        }

        // Health
        context.`br$drawString`(
            render,
            (endX - context.`br$getFont`().`br$getWidth`(render)),
            y,
            color,
            true
        )
    }
}
