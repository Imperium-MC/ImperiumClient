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

import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.api.API
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hypixel.HypixelAbstractionLayer
import io.github.axolotlclient.modules.hypixel.PlayerData
import io.github.axolotlclient.modules.hypixel.PlayerData.Bedwars.CombinedGameData
import kotlin.math.max

class StatsOverlay(private val mod: BedwarsMod) : TextHudEntry(400, 600, true) {
    private fun interface EntryRenderer {
        fun render(team: BedwarsTeam, name: String, data: PlayerData.Bedwars?, winstreak: Int): AxoText
    }

    private data class Entry(
        val acceptNull: Boolean, val name: String, val condition: (StatsOverlay) -> Boolean,
        val compRenderer: EntryRenderer
    )

    private inner class RenderHelper(
        val stats: Map<String, PlayerData.Bedwars>,
        val playersByTeam: Map<BedwarsTeam, List<String>>
    ) {
        private var xCursor: Int = contentPos.x + padding.get()
        private var yFinal = 0

        private fun renderColumn(ctx: AxoRenderContext, renderEntry: Entry) {
            val dy = AxoMinecraftClient.getInstance().`br$getFont`().`br$getFontHeight`() + rowMargin.get()

            var currY = contentPos.y + padding.get()
            var newXCursor = ctx.`br$drawString`(AxoI18n.translate(renderEntry.name), xCursor, currY, -0x1, shadow.get())

            currY += dy

            for (entry in playersByTeam.entries) {
                val team = entry.key
                val members = entry.value

                for (playerName in members) {
                    val data = stats[playerName]
                    val text = if (data == null) {
                        (if (renderEntry.acceptNull) renderEntry.compRenderer.render(team, playerName, null, 0)
                        else AxoText.literal("?").`br$color`(AxoText.Color.RED))
                    } else {
                        renderEntry.compRenderer.render(team, playerName, data, data.all.winstreak)
                    }

                    newXCursor = max(newXCursor, ctx.`br$drawString`(text, xCursor, currY, -0x1, shadow.get()))
                    currY += dy
                }
            }

            yFinal = currY
            xCursor = newXCursor + columnMargin.get()
        }

        fun render(ctx: AxoRenderContext) {
            for (renderEntry in RENDER_ENTRIES) {
                if (renderEntry.condition(this@StatsOverlay)) {
                    renderColumn(ctx, renderEntry)
                }
            }

            // don't multiply the padding by two, since it's already accounted for by the cursors
            val newWidth = xCursor - contentPos.x + padding.get() - columnMargin.get()
            val newHeight = yFinal - contentPos.y + padding.get() - rowMargin.get()

            val dirty = newWidth != getContentWidth() || newHeight != getContentHeight()

            setContentWidth(newWidth)
            setContentHeight(newHeight)

            if (dirty) {
                onBoundsUpdate()
            }
        }
    }

    private val padding = IntegerOption("hud.padding", 3, 1, 10)
    private val columnMargin = IntegerOption("hud.column_margin", 3, 0, 10)
    private val rowMargin = IntegerOption("hud.row_margin", 1, 0, 10)
    private val columnLevel = BooleanOption("bedwars.stats_overlay.column.level", false)
    private val columnFkdr = BooleanOption("bedwars.stats_overlay.column.fkdr", true)
    private val columnKdr = BooleanOption("bedwars.stats_overlay.column.kdr", true)
    private val columnWlr = BooleanOption("bedwars.stats_overlay.column.wlr", true)
    private val columnWs = BooleanOption("bedwars.stats_overlay.column.ws", true)
    private val toggleKey = BooleanOption("toggle", "bedwars.stats_overlay.toggle", true)
    private val autoActivate = BooleanOption("bedwars.stats_overlay.auto_activate", true)

    private var stats: MutableMap<String, PlayerData.Bedwars> = HashMap()
    private val playersByTeam: MutableMap<BedwarsTeam, MutableList<String>> = HashMap()
    private val toggle = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "bedwars_stats_overlay")
    private var shouldRender = false
    private var errorMessage: String? = null

    init {
        BedwarsMod.GAME_START_EVENT.register(Consumer { g -> onStart(g) })
        BedwarsMod.GAME_END_EVENT.register(Runnable { onEnd() })
        BedwarsMod.PLAYER_ADD.register(Consumer { player ->
            val teamNames = playersByTeam.computeIfAbsent(player.team) { ArrayList() }
            val namesSize = teamNames.size
            if (namesSize == 0) {
                teamNames.add(player.profile.`br$getName`())
            } else {
                val index = minOf(namesSize, player.number - 1)
                teamNames.add(index, player.profile.`br$getName`())
            }
            HypixelAbstractionLayer.getInstance().playerDataApi
                .getAsync(player.profile.`br$getId`().toString())
                .thenAcceptAsync(
                    Consumer { o ->
                        o.ifPresent { data -> stats[player.profile.`br$getName`()] = data.bedwars }
                    },
                    client
                )
        })
    }

    private fun onStart(g: BedwarsGame) {
        playersByTeam.clear()
        // can't call clear here, since we need a fresh map to avoid requests from writing
        stats = HashMap()
        shouldRender = toggleKey.get() && autoActivate.get()

        if (!API.getInstance().apiOptions.enabled.get()) {
            errorMessage = "API Not Enabled!"
            return
        }

        if (!API.getInstance().isAuthenticated()) {
            errorMessage = "API Not Authenticated!"
            return
        }

        val api = HypixelAbstractionLayer.getInstance().playerDataApi

        // need to use capturedStats since this map could've been "retired"
        val capturedStats = this.stats

        g.playersByTeam.forEach { (t, e) ->
            playersByTeam[t] = e.map { it.`br$getName`() }.toCollection(ArrayList()) // explicit creation because we require mutability
            e.forEach { entry ->
                api.getAsync(entry.`br$getId`().toString())
                    .whenCompleteAsync(
                        { playerData, _ ->
                            if (playerData.isEmpty) {
                                return@whenCompleteAsync
                            }

                            capturedStats[entry.`br$getName`()] = playerData.get().bedwars
                        },
                        client
                    )
            }
        }
    }

    private fun onEnd() {
        shouldRender = false
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (errorMessage != null) {
            ctx.`br$drawString`(AxoText.Color.RED.name + errorMessage, contentPos.x, contentPos.y, -0x1, shadow.get())
        }

        if (mod.inGame() && shouldRender) {
            super.render(ctx, delta)
        }
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        RenderHelper(stats, playersByTeam).render(ctx)
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        RenderHelper(SAMPLE_STATS, SAMPLE_PLAYERS).render(ctx)
    }

    override fun tick() {
        if (mod.inGame()) {
            if (toggleKey.get()) {
                if (this.toggle.`br$consumeClick`()) {
                    shouldRender = !shouldRender
                }
            } else {
                shouldRender = toggle.`br$isPressed`()
            }
        }
    }

    override fun tickable(): Boolean = true

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val opts = super.getConfigurationOptions()
        opts.remove(textColor)
        opts.add(padding)
        opts.add(columnMargin)
        opts.add(rowMargin)
        opts.add(columnLevel)
        opts.add(columnFkdr)
        opts.add(columnKdr)
        opts.add(columnWlr)
        opts.add(columnWs)
        opts.add(toggleKey)
        opts.add(autoActivate)
        return opts
    }

    companion object {
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "bedwars_stats_overlay")

        private val RENDER_ENTRIES = listOf(
            Entry(true, "bedwars.stats_overlay.header.player", { _: StatsOverlay -> true },
                EntryRenderer { t, n, _, _ -> AxoText.literal(t.getColorSection() + n) }),
            Entry(false, "bedwars.stats.overlay.header.level", { o: StatsOverlay -> o.columnLevel.get() },
                EntryRenderer { _, _, bw, _ -> BedwarsPrestige.format(bw!!.level) }),
            Entry(false, "bedwars.stats_overlay.header.fkdr", { o: StatsOverlay -> o.columnFkdr.get() },
                EntryRenderer { _, _, bw, _ ->
                    AxoText.literal("%.2f (%s/%s)".format(bw!!.core.fkdr(), bw.core.finalKills, bw.core.finalDeaths))
                        .`br$color`(AxoText.Color.GOLD)
                }),
            Entry(false, "bedwars.stats_overlay.header.kdr", { o: StatsOverlay -> o.columnKdr.get() },
                EntryRenderer { _, _, bw, _ ->
                    AxoText.literal("%.2f (%s/%s)".format(bw!!.core.kdr(), bw.core.kills, bw.core.deaths))
                        .`br$color`(AxoText.Color.GOLD)
                }),
            Entry(false, "bedwars.stats_overlay.header.wlr", { o: StatsOverlay -> o.columnWlr.get() },
                EntryRenderer { _, _, bw, _ ->
                    AxoText.literal("%.2f (%s/%s)".format(bw!!.core.wlr(), bw.core.wins, bw.core.losses))
                        .`br$color`(AxoText.Color.GOLD)
                }),
            Entry(false, "bedwars.stats_overlay.header.ws", { o: StatsOverlay -> o.columnWs.get() },
                EntryRenderer { _, _, _, ws -> AxoText.literal(ws.toString()).`br$color`(AxoText.Color.GOLD) })
        )

        private val SAMPLE_PLAYERS: Map<BedwarsTeam, List<String>> = mapOf(
            BedwarsTeam.AQUA to listOf("FloweyTF", "Adaklys"),
            BedwarsTeam.GREEN to listOf("herobrine", "steve")
        )

        private val SAMPLE_STATS: Map<String, PlayerData.Bedwars> = mapOf(
            "FloweyTF" to createFake(525, 3, CombinedGameData(4234, 5634, 500, 300, 1469, 336, 230, 123)),
            "Adaklys" to createFake(179, 3, CombinedGameData(1984, 2048, 300, 500, 834, 737, 123, 273)),
            "steve" to createFake(5, 2, CombinedGameData(10, 1, 10, 1, 10, 1, 10, 1))
        )

        private fun createFake(level: Int, winstreak: Int, data: CombinedGameData): PlayerData.Bedwars =
            PlayerData.Bedwars(
                level,
                PlayerData.Bedwars.GameData(0, 0, 0, 0, winstreak, 0, 0, 0, 0),
                data, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
            )
    }
}
