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

import java.util.function.Consumer
import java.util.Arrays
import java.util.HashMap

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent

/**
 * Based on <a href="https://github.com/DragonEggBedrockBreaking/AutoGG/blob/trunk/src/main/java/uk/debb/autogg/mixin/MixinChatHud.java">DragonEggBedrockBreaking's AutoGG Mod</a>
 *
 * <p>License: MPL-2.0</p>
 */
class AutoGG : AbstractHypixelMod {
    @JvmField
    val gg = BooleanOption("printGG", false)
    @JvmField
    val ggString = StringOption("ggString", "gg")
    @JvmField
    val gf = BooleanOption("printGF", false)
    @JvmField
    val gfString = StringOption("gfString", "gf")
    @JvmField
    val glhf = BooleanOption("printGLHF", false)
    @JvmField
    val glhfString = StringOption("glhfString", "glhf")
    private val category = OptionCategory.create("autogg")
    private val client = AxoMinecraftClient.getInstance()
    private val onHypixel = BooleanOption("onHypixel", false)
    private val onBWP = BooleanOption("onBWP", false)
    private val onPVPL = BooleanOption("onPVPL", false)
    private val onMMC = BooleanOption("onMMC", false)
    private val ggStrings = HashMap<String, List<String>>()
    private val gfStrings = HashMap<String, List<String>>()
    private val glhfStrings = HashMap<String, List<String>>()
    private val serverMap = HashMap<String, BooleanOption>()
    private var lastTime = 0L

    override fun init() {
        populateGGStrings()
        populateGFStrings()
        populateGLHFStrings()
        serverMap["hypixel.net"] = onHypixel

        serverMap["bedwarspractice.club"] = onBWP

        serverMap["pvp.land"] = onPVPL

        serverMap["minemen.club"] = onMMC

        category.add(gg)
        category.add(ggString)
        category.add(gf)
        category.add(gfString)
        category.add(glhf)
        category.add(glhfString)
        category.add(onHypixel)
        category.add(onBWP)
        category.add(onPVPL)
        category.add(onMMC)

        Events.RECEIVE_CHAT_MESSAGE.register(Consumer { event -> onMessage(event) })
    }

    override fun getCategory(): OptionCategory = category

    private fun populateGGStrings() {
        ggStrings["hypixel.net"] = addToList(
            "1st Killer -",
            "1st Place -",
            "Winner:",
            " - Damage Dealt -",
            "Winning Team -",
            "1st -",
            "Winners:",
            "Winner:",
            "Winning Team:",
            " won the game!",
            "Top Seeker:",
            "1st Place:",
            "Last team standing!",
            "Winner #1 (",
            "Top Survivors",
            "Winners -",
            "Sumo Duel -",
            "Most Wool Placed -",
            "Your Overall Winstreak:"
        )

        ggStrings["bedwarspractice.club"] = addToList(
            "Winners -",
            "Game Won!",
            "Game Lost!",
            "The winning team is"
        )

        ggStrings["pvp.land"] = addToList(
            "The match has ended!",
            "Match Results",
            "Winner:",
            "Loser:"
        )

        ggStrings["minemen.club"] = addToList("Match Results")
    }

    private fun populateGFStrings() {
        gfStrings["hypixel.net"] = addToList(
            "SkyWars Experience (Kill)",
            "coins! (Final Kill)"
        )

        gfStrings["bedwarspractice.club"] = addToList(client.`br$getSession`().username + " FINAL KILL!")

        gfStrings["pvp.land"] = addToList("slain by " + client.`br$getSession`().username)

        gfStrings["minemen.club"] = addToList("killed by " + client.`br$getSession`().username + "!")
    }

    private fun populateGLHFStrings() {
        glhfStrings["hypixel.net"] = addToList("The game starts in 1 second!")

        glhfStrings["bedwarspractice.club"] = addToList("Game starting in 1 seconds!", "Game has started!")

        glhfStrings["minemen.club"] = addToList("1...")
    }

    private fun addToList(vararg strings: String): List<String> = Arrays.stream(strings).toList()

    private fun onMessage(event: ReceiveChatMessageEvent) {
        val message = event.originalMessage
        if (!client.`br$isLocalServer`()) {
            serverMap.keys.forEach { s ->
                if (serverMap[s]!!.get() && client.`br$getServerAddress`()?.contains(s) == true) {
                    if (gf.get()) {
                        processChat(message, gfStrings[s], gfString.get())
                    }
                    if (gg.get()) {
                        processChat(message, ggStrings[s], ggString.get())
                    }
                    if (glhf.get()) {
                        processChat(message, glhfStrings[s], glhfString.get())
                    }
                }
            }
        }
    }

    private fun processChat(messageReceived: String, options: List<String>?, messageToSend: String) {
        if (System.currentTimeMillis() - this.lastTime > 3000 && options != null) {
            for (s in options) {
                if (messageReceived.contains(s)) {
                    AxoMinecraftClient.getInstance().`br$sendToServer`(messageToSend)
                    this.lastTime = System.currentTimeMillis()
                    return
                }
            }
        }
    }

    companion object {
        private val Instance = AutoGG()

        @JvmStatic
        fun getInstance(): AutoGG = Instance
    }
}
