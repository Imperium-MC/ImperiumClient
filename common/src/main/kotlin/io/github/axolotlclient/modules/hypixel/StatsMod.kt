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

import java.util.Arrays
import java.util.function.Consumer

import com.mojang.brigadier.Command
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.api.API
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.commands.AxoClientCmdSrcStack
import io.github.axolotlclient.bridge.commands.Commands
import io.github.axolotlclient.bridge.commands.PlayerArgument
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.bridge.util.AxoText.Color.GOLD
import io.github.axolotlclient.bridge.util.AxoText.Color.GREEN
import io.github.axolotlclient.bridge.util.AxoText.Color.RED
import io.github.axolotlclient.bridge.util.AxoText.literal
import io.github.axolotlclient.bridge.util.AxoText.translatable
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsPrestige

class StatsMod : AbstractHypixelMod {
    private fun register(commands: Commands) {
        val command = Commands.literal("playerstats")

        for (handler in HANDLERS) {
            command.then(
                Commands.literal(handler.name).then(
                    Commands.argument("player", PlayerArgument.player()).executes(
                        Command { c ->
                            if (!API.getInstance().apiOptions.enabled.get()) {
                                c.getSource().`br$sendError`(
                                    translatable("playerstats.error.api_disabled").`br$color`(RED)
                                )
                                return@Command -1
                            }

                            if (!API.getInstance().isAuthenticated()) {
                                c.getSource().`br$sendError`(
                                    translatable("playerstats.error.api_unauthenticated").`br$color`(RED)
                                )
                                return@Command -1
                            }

                            val res = PlayerArgument.get(c, "player")

                            res.uuid().whenCompleteAsync { s, ex ->
                                if (s.isEmpty) {
                                    c.getSource().`br$sendError`(
                                        translatable("playerstats.error.unknown_player").`br$color`(RED)
                                    )
                                } else {
                                    HypixelAbstractionLayer.getInstance().playerDataApi
                                        .getAsync(s.get())
                                        .whenCompleteAsync({ playerData, throwable ->
                                            if (playerData.isEmpty) {
                                                c.getSource().`br$sendError`(
                                                    translatable("playerstats.error.failed_data").`br$color`(RED)
                                                )
                                                return@whenCompleteAsync
                                            }

                                            handler.handler.accept(c.getSource(), s.get(), res.playerName(), playerData.get())
                                        }, AxoMinecraftClient.getInstance())
                                }
                            }

                            0
                        }
                    )
                )
            )
        }

        val node = commands.register(command)
        commands.register(Commands.literal("pstats").redirect(node))
    }

    override fun init() {
        Events.COMMAND_REGISTER.register(Consumer { commands -> register(commands) })
    }

    override fun getCategory(): OptionCategory? = null

    private fun interface Handler {
        fun accept(ctx: AxoClientCmdSrcStack, uuid: String, username: String, data: PlayerData)
    }

    private data class Entry(val name: String, val handler: Handler)

    companion object {
        private fun statText(key: String, vararg args: Any?): AxoText.Mutable =
            translatable(
                key,
                Arrays.stream(args).map { s ->
                    if (s is Float) {
                        literal(String.format("%.2f", s)).`br$color`(GREEN)
                    } else {
                        literal(s.toString()).`br$color`(GREEN)
                    }
                }.toArray()
            )

        private fun buildBedwarsGameMode(key: String, data: PlayerData.Bedwars.BedwarsGameData?): AxoText {
            if (data == null) return literal("")
            val text = statText(key)

            val hover = literal("")
            hover.`br$append`(statText("playerstats.bedwars.kdr", data.kills, data.deaths, data.kdr()))
            hover.`br$append`("\n")
            hover.`br$append`(statText("playerstats.bedwars.fkdr", data.finalKills, data.finalDeaths, data.fkdr()))
            hover.`br$append`("\n")
            hover.`br$append`(statText("playerstats.bedwars.beds", data.bedsBroken, data.bedsLost, data.bblr()))
            hover.`br$append`("\n")
            hover.`br$append`(statText("playerstats.bedwars.summary_short", data.wins, data.losses, data.wlr()))

            text.`br$withStyle` { s ->
                s.`br$color`(GOLD)
                    .`br$tooltip`(hover)
            }

            return text
        }

        private fun buildBedwarsGameModesLine(data: PlayerData.Bedwars): AxoText {
            val text = literal("")

            text.`br$append`(buildBedwarsGameMode("playerstats.bedwars.solo", data.solo))
            text.`br$append`(" | ")
            text.`br$append`(buildBedwarsGameMode("playerstats.bedwars.duos", data.doubles))
            text.`br$append`(" | ")
            text.`br$append`(buildBedwarsGameMode("playerstats.bedwars.fours", data.fours))
            text.`br$append`(" | ")
            text.`br$append`(buildBedwarsGameMode("playerstats.bedwars.core", data.core))
            text.`br$append`(" | ")
            text.`br$append`(buildBedwarsGameMode("playerstats.bedwars.dreams", data.dreams))

            return text
        }

        private fun buildSkywarsGameMode(key: String, data: PlayerData.Skywars.GameData): AxoText {
            val text = statText(key)

            val hover = literal("")

            hover.`br$append`(statText("playerstats.skywars.kdr", data.kills, data.deaths, data.kdr()))
            hover.`br$append`("\n")
            hover.`br$append`(statText("playerstats.skywars.summary", data.wins, data.losses, data.wlr()))

            text.`br$withStyle` { s ->
                s.`br$color`(GOLD)
                    .`br$tooltip`(hover)
            }

            return text
        }

        private fun buildSkywarsGameModesLine(data: PlayerData.Skywars): AxoText {
            val text = literal("")

            text.`br$append`(buildSkywarsGameMode("playerstats.skywars.solo", data.solo.normal))
            text.`br$append`(" | ")
            text.`br$append`(buildSkywarsGameMode("playerstats.skywars.duos", data.team.normal))
            text.`br$append`(" | ")
            text.`br$append`(buildSkywarsGameMode("playerstats.skywars.solo_insane", data.solo.insane))
            text.`br$append`(" | ")
            text.`br$append`(buildSkywarsGameMode("playerstats.skywars.duos_insane", data.team.insane))

            return text
        }

        private fun buildDuelsGameMode(entry: Map.Entry<String, PlayerData.DuelsData.DuelsGameData>): AxoText {
            val value = entry.value
            val hover = translatable(
                "playerstats.duels.mode_title",
                translatable("playerstats.duels." + entry.key).`br$color`(GOLD)
            )
            hover.`br$append`("\n")
            hover.`br$append`(statText("playerstats.duels.kdr", value.kills, value.deaths, value.kdr()))
            hover.`br$append`("\n")
            hover.`br$append`(
                statText("playerstats.duels.summary", value.wins, value.losses, value.wlr(), value.winstreak)
            )

            return translatable("playerstats.duels." + entry.key).`br$withStyle` { s ->
                s.`br$color`(GOLD)
                    .`br$tooltip`(hover)
            }
        }

        private fun buildDuelsGameModesLine(data: PlayerData.DuelsData): AxoText {
            val text = literal("")
            var empty = true
            for (stringDuelsGameDataEntry in data.modes.entries) {
                if (!empty) {
                    text.`br$append`("\n")
                }
                val buildDuelsGameMode = buildDuelsGameMode(stringDuelsGameDataEntry)
                text.`br$append`(literal("» ").`br$color`(RED))
                text.`br$append`(buildDuelsGameMode)
                empty = false
            }
            return text
        }

        private val HANDLERS = listOf(
            Entry("bedwars", Handler { c, uuid, username, data ->
                val allStats = data.bedwars.all
                listOf(
                    translatable(
                        "playerstats.bedwars.title", data.formattedName(),
                        BedwarsPrestige.format(data.bedwars.level)
                    ),
                    statText("playerstats.bedwars.kdr", allStats.kills, allStats.deaths, allStats.kdr()),
                    statText("playerstats.bedwars.fkdr", allStats.finalKills, allStats.finalDeaths, allStats.fkdr()),
                    statText("playerstats.bedwars.beds", allStats.bedsBroken, allStats.bedsLost, allStats.bblr()),
                    statText(
                        "playerstats.bedwars.summary", allStats.wins, allStats.losses, allStats.wlr(),
                        allStats.winstreak
                    ),
                    buildBedwarsGameModesLine(data.bedwars)
                ).forEach { c.`br$sendFeedback`(it) }
            }),
            Entry("skywars", Handler { c, uuid, username, data ->
                val allStats = data.skywars.all
                listOf(
                    translatable("playerstats.skywars.title", data.formattedName(), data.skywars.level),
                    statText("playerstats.skywars.kdr", allStats.kills, allStats.deaths, allStats.kdr()),
                    statText("playerstats.skywars.summary", allStats.wins, allStats.losses, allStats.wlr()),
                    buildSkywarsGameModesLine(data.skywars)
                ).forEach { c.`br$sendFeedback`(it) }
            }),
            Entry(
                "duels",
                Handler { c, uuid, username, data ->
                    listOf(
                        translatable("playerstats.duels.title", data.formattedName()),
                        buildDuelsGameModesLine(data.duels)
                    ).forEach { c.`br$sendFeedback`(it) }
                }
            )
        )

        private val instance = StatsMod()

        @JvmStatic
        fun getInstance(): StatsMod = instance
    }
}
