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

import java.util.Optional
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.EventBus
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent
import io.github.axolotlclient.bridge.events.types.WorldLoadEvent
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.scores.AxoTeam
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hypixel.AbstractHypixelMod
import java.util.regex.Pattern

/**
 * @author DarkKronicle
 */

class BedwarsMod : AbstractHypixelMod {
    @JvmField
    val hardcoreHearts = BooleanOption(getTranslationKey("hardcoreHearts"), true)
    @JvmField
    val showHunger = BooleanOption(getTranslationKey("showHunger"), false)
    @JvmField
    val displayArmor = BooleanOption(getTranslationKey("displayArmor"), true)
    @JvmField
    val bedwarsLevelHead = BooleanOption(getTranslationKey("bedwarsLevelHead"), true)
    @JvmField
    val bedwarsLevelHeadMode = EnumOption(
        getTranslationKey("bedwarsLevelHeadMode"),
        BedwarsLevelHeadMode::class.java,
        BedwarsLevelHeadMode.GAME_KILLS_GAME_DEATHS
    )
    val upgradesOverlay: TeamUpgradesOverlay
    val resourceOverlay: ResourceOverlay
    val statsOverlay: StatsOverlay
    val sessionStatsOverlay: SessionStatisticsOverlay
    internal val removeAnnoyingMessages = BooleanOption(getTranslationKey("removeAnnoyingMessages"), true)
    internal val overrideMessages = BooleanOption(getTranslationKey("overrideMessages"), true)
    @JvmField
    val category: OptionCategory = OptionCategory.create("bedwars")

    override fun getCategory(): OptionCategory = category
    private val enabled = BooleanOption("enabled", "bedwars.enabled.tooltip", false)
    private val tabRenderLatencyIcon = BooleanOption(getTranslationKey("tabRenderLatencyIcon"), false)

    private val showChatTime = BooleanOption(getTranslationKey("showChatTime"), true)
    @JvmField
    val customTabList = BooleanOption(getTranslationKey("custom_tab_list"), true)
    @JvmField
    val customTabHeader = BooleanOption(getTranslationKey("custom_tab_header"), true)
    @JvmField
    val customTabFooter = BooleanOption(getTranslationKey("custom_tab_footer"), true)
    var sessionStats: SessionStatistics = SessionStatistics()
        private set
    var currentGame: BedwarsGame? = null
        private set
    private var targetTick = -1
    private var waiting = false

    init {
        upgradesOverlay = TeamUpgradesOverlay(this)
        resourceOverlay = ResourceOverlay(this)
        statsOverlay = StatsOverlay(this)
        sessionStatsOverlay = SessionStatisticsOverlay(this)
    }

    val game: Optional<BedwarsGame>
        get() = Optional.ofNullable(currentGame)

    override fun init() {
        category.add(
            enabled, hardcoreHearts, showHunger, displayArmor, bedwarsLevelHead, bedwarsLevelHeadMode,
            removeAnnoyingMessages, tabRenderLatencyIcon, showChatTime, overrideMessages, customTabList,
            customTabHeader, customTabFooter
        )
        category.add(upgradesOverlay.getAllOptions())
        category.add(resourceOverlay.getAllOptions())
        category.add(statsOverlay.getAllOptions())
        category.add(sessionStatsOverlay.getAllOptions())
        category.add(BedwarsDeathType.options)

        Events.RECEIVE_CHAT_MESSAGE.register(Consumer { this.onMessage(it) })
        Events.SCOREBOARD_RENDER_EVENT.register(Consumer { this.onScoreboardRender(it) })
        Events.WORLD_LOAD_EVENT.register(Consumer { this.onWorldLoad() })
        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "bedwars.reset_session_stats")
            .`br$registerOnConsumeClick`(Runnable { this.resetSessionStats() })
    }

    val isEnabled: Boolean
        get() = enabled.get()

    fun onWorldLoad() {
        if (currentGame != null) {
            gameEnd()
        }
    }

    fun isWaiting(): Boolean {
        if (inGame()) {
            waiting = false
        }
        return waiting
    }

    fun onMessage(event: ReceiveChatMessageEvent) {
        val rawMessage = event.formattedMessage.`br$getRawString`()
        if (currentGame != null) {
            currentGame!!.onChatMessage(rawMessage, event)

            val time = AxoText.empty().`br$append`(
                AxoText.literal(currentGame!!.getFormattedTime())
                    .`br$color`(AxoText.Color.GRAY)
                    .`br$append`(" ")
            )

            if (!event.isCancelled && showChatTime.get()) {
                // Add time to every message received in game
                if (event.newMessage != null) {
                    event.newMessage = time.`br$append`(event.newMessage!!)
                } else {
                    event.newMessage = time.`br$append`(event.formattedMessage)
                }
            }
        } else if (enabled.get() && targetTick < 0 && BedwarsMessages.matched(GAME_START, rawMessage).isPresent) {
            // Give time for Hypixel to sync
            targetTick = Platform.tickCount() + 10
        }
    }

    override fun tickable(): Boolean = true

    override fun tick() {
        if (currentGame != null) {
            waiting = false
            if (currentGame!!.isStarted) {
                // Trigger setting the header
                currentGame!!.tick()
                Platform.setTabListHeader(null)
            } else {
                if (checkReady()) {
                    currentGame!!.onStart()
                }
            }
        } else {
            if (targetTick > 0 && Platform.tickCount() > targetTick) {
                currentGame = BedwarsGame(this)
                targetTick = -1
            }
        }
    }

    private fun checkReady(): Boolean {
        for (player in AxoMinecraftClient.getInstance().`br$getOnlinePlayers`()) {
            val name = Platform.getTabNameFor(player).replace("§.".toRegex(), "")
            if (name.isNotEmpty() && name[1] == ' ') {
                return true
            }
        }
        return false
    }

    fun inGame(): Boolean = currentGame != null && currentGame!!.isStarted

    fun onScoreboardRender(event: ScoreboardRenderEvent) {
        if (inGame()) {
            waiting = false
            currentGame!!.onScoreboardRender(event)
            return
        }
        if (!AxoText.strip(event.objective.`br$getDisplayName`()!!).contains("BED WARS")) {
            return
        }
        val scoreboard = event.objective.`br$getScoreboard`()
        val scores = scoreboard.`br$getScores`(event.objective)
        val filteredScores = scores.filter { score -> !score.`br$isHidden`() }

        waiting = filteredScores.any { score ->
            val team = scoreboard.`br$getTeam`(score.`br$getOwner`()!!)!!
            val format =
                AxoText.strip(AxoTeam.`br$getMemberDisplayName`(team, score.`br$getOwner`()!!)!!).replace("[^A-z0-9 .:]".toRegex(), "")
            format.contains("Waiting...") || format.contains("Starting in")
        }
    }

    fun gameEnd() {
        if (currentGame != null) {
            sessionStats.gamePlayed()
            GAME_END_EVENT.invoker().run()
            currentGame = null
        }
    }

    fun blockLatencyIcon(): Boolean = !tabRenderLatencyIcon.get()

    private fun getTranslationKey(name: String): String = "bedwars.$name"

    fun resetSessionStats() {
        sessionStats = SessionStatistics()
    }

    companion object {
        @JvmField
        val GAME_START_EVENT: EventBus<Consumer<BedwarsGame>> = EventBus.broadcast1()
        @JvmField
        val GAME_END_EVENT: EventBus<Runnable> = EventBus.broadcast0()

        // Triggered when adding a player to our cached player lists after the game has started
        @JvmField
        val PLAYER_ADD: EventBus<Consumer<BedwarsPlayer>> = EventBus.broadcast1()
        private val GAME_START = arrayOf(
            Pattern.compile("^\\s*?Protect your bed and destroy the enemy beds\\.\\s*?$")
        )

        private val Instance = BedwarsMod()

        @JvmStatic
        fun getInstance(): BedwarsMod = Instance
    }
}
