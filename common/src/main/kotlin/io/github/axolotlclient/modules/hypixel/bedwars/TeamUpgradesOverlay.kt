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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hypixel.bedwars.upgrades.BedwarsTeamUpgrades
import io.github.axolotlclient.modules.hypixel.bedwars.upgrades.TeamUpgrade
import io.github.axolotlclient.modules.hypixel.bedwars.upgrades.TrapUpgrade
import kotlin.math.max

/**
 * @author DarkKronicle
 */

class TeamUpgradesOverlay(private val mod: BedwarsMod) :
    BoxHudEntry(35, 18, true), DynamicallyPositionable {
    private val renderWhenRelevant = BooleanOption(ID.`br$getPath`() + ".renderWhenRelevant", true)
    private val anchor = DefaultOptions.getAnchorPoint(this)
    private var upgrades: BedwarsTeamUpgrades? = null

    init {
        BedwarsMod.GAME_START_EVENT.register(Consumer { game -> upgrades = game.upgrades })
        BedwarsMod.GAME_END_EVENT.register(Runnable { upgrades = null })
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (!renderWhenRelevant.get() || mod.inGame()) {
            super.render(context, delta)
        }
    }

    fun drawOverlay(context: AxoRenderContext, position: DrawPosition, editMode: Boolean) {
        if (upgrades == null && !editMode) {
            return
        }

        var x = position.x() + 1
        var y = position.y() + 1
        var width = 18
        var normalUpgrades = false
        if (upgrades != null) {
            for (u in upgrades!!.upgrades) {
                if (!u.isPurchased()) {
                    continue
                }
                if (u is TrapUpgrade) {
                    continue
                }
                u.draw(context, x, y, 16, 16)
                x += 17
                normalUpgrades = true
            }
            width = max(x - position.x() - 1, width)
        }
        x = position.x() + 1
        if (normalUpgrades) {
            y += 17
        }
        if (editMode) {
            for (type in trapEdit) {
                type.draw(context, x, y, 16, 16)
                x += 17
            }
            width = max(x - position.x() - 1, width)
        } else if (upgrades != null) {
            upgrades!!.trap.draw(context, x, y, 16, 16)
            width = max(x + upgrades!!.trap.trapCount * 16 - position.x() - 1, width)
        }
        val height = y - position.y() - 1 + 17
        if (getContentHeight() != height || getContentWidth() != width) {
            setContentWidth(width)
            setContentHeight(height)
            onBoundsUpdate()
        }
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        drawOverlay(context, contentPos, false)
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                val pos = contentPos
                context.`br$drawCenteredString`(
                    getName(),
                    pos.x() + getContentWidth() / 2,
                    pos.y() + getContentHeight() / 2 - context.`br$getFont`().`br$getFontHeight`() / 2,
                    -1
                )
                return
            }
        }
        drawOverlay(context, contentPos, true)
    }

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(renderWhenRelevant)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    companion object {
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "bedwars_teamupgrades")
        private val trapEdit = arrayOf(TrapUpgrade.TrapType.MINER_FATIGUE, TrapUpgrade.TrapType.ITS_A_TRAP)
    }
}
