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

package io.github.axolotlclient.modules.hypixel.bedwars.upgrades

import java.util.regex.Matcher
import java.util.regex.Pattern

import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffects
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoSprites
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMode
import java.util.Locale

/**
 * @author DarkKronicle
 */

class TrapUpgrade : TeamUpgrade("trap", REGEX) {
    private val traps: MutableList<TrapType> = ArrayList(3)

    override fun onMatch(upgrade: TeamUpgrade, matcher: Matcher) {
        if (matcher.group(1) == "off") {
            // Trap went off
            traps.removeAt(0)
            return
        }
        val type = TrapType.getFuzzy(matcher.group(1))
        if (matcher.groupCount() >= 2 && matcher.group(2) == "queue") {
            traps.remove(type)
            return
        }
        traps.add(type)
    }

    fun canPurchase(): Boolean = traps.size < 3

    override fun getPrice(mode: BedwarsMode): Int =
        when (traps.size) {
            0 -> 1
            1 -> 2
            2 -> 4
            else -> 0
        }

    override fun isPurchased(): Boolean = traps.isNotEmpty()

    override fun draw(context: AxoRenderContext, x: Int, y: Int, width: Int, height: Int) {
        if (traps.isEmpty()) {
            context.`br$drawTexture`(AxoSprites.BARRIER_ITEM_ICON, x, y, 16, 16)
        } else {
            var currentX = x
            for (type in traps) {
                type.draw(context, currentX, y, width, height)
                currentX += width + 1
            }
        }
    }

    val trapCount: Int
        get() = traps.size

    override fun isMultiUpgrade(): Boolean = true

    enum class TrapType(private val renderer: TeamUpgradeRenderer) {
        ITS_A_TRAP(TeamUpgradeRenderer { r, x, y, _, _, _ ->
            r.`br$drawTexture`(AxoStatusEffects.BLINDNESS.`br$getSprite`(), x - 1, y - 1, 18, 18)
        }),
        COUNTER_OFFENSIVE(TeamUpgradeRenderer { r, x, y, _, _, _ ->
            r.`br$drawTexture`(AxoStatusEffects.SPEED.`br$getSprite`(), x - 1, y - 1, 18, 18)
        }),
        ALARM(TeamUpgradeRenderer { r, x, y, _, _, _ ->
            r.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.ENDER_EYE), x - 1, y - 1)
        }),
        MINER_FATIGUE(TeamUpgradeRenderer { r, x, y, _, _, _ ->
            r.`br$drawTexture`(AxoStatusEffects.MINING_FATIGUE.`br$getSprite`(), x - 1, y - 1, 18, 18)
        });

        fun draw(context: AxoRenderContext, x: Int, y: Int, width: Int, height: Int) {
            renderer.render(context, x, y, width, height, 0)
        }

        companion object {

            @JvmStatic
            fun getFuzzy(s: String): TrapType {
                val lower = s.lowercase(Locale.ROOT)
                if (lower.contains("miner")) {
                    return MINER_FATIGUE
                }
                if (lower.contains("reveal")) {
                    return ALARM
                }
                if (lower.contains("counter")) {
                    return COUNTER_OFFENSIVE
                }
                return ITS_A_TRAP
            }
        }
    }

    companion object {
        private val REGEX = arrayOf(
            Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased (.+) Trap.?\\s*$"),
            Pattern.compile("[Tt]rap (?:was )?set (off)(?: by .+ from .+ team)?!"),
            Pattern.compile("Removed (.+) Trap from the (queue)!\\s*$")
        )
    }
}
