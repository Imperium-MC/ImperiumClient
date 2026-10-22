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

import java.util.regex.Pattern

import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffects
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoSprites

/**
 * @author DarkKronicle
 */

class BedwarsTeamUpgrades {
    val trap: TrapUpgrade = TrapUpgrade()

    val sharpness: TeamUpgrade = TieredUpgrade(
        "sharp", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased Sharpened Swords"),
        intArrayOf(8, 32), intArrayOf(4, 16)
    ) { context, x, y, _, _, upgradeLevel ->
        if (upgradeLevel == 0) {
            context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.STONE_SWORD), x, y)
        } else {
            context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_SWORD), x, y)
        }
    }

    val healPool: TeamUpgrade = BinaryUpgrade(
        "healpool", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased Heal Pool\\s*$"), 3, 1
    ) { context, x, y, _, _, _ ->
        context.`br$drawTexture`(AxoStatusEffects.REGEN.`br$getSprite`(), x - 1, y - 1, 18, 18)
    }

    val protection: TeamUpgrade = TieredUpgrade(
        "prot", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased Reinforced Armor .{1,3}\\s*$"),
        intArrayOf(5, 10, 20, 30), intArrayOf(2, 4, 8, 16)
    ) { context, x, y, width, height, upgradeLevel ->
        when (upgradeLevel) {
            1 -> {
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.IRON_CHESTPLATE), x, y)
                context.`br$pushScissor`(x, y + height / 2, width / 2, height)
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_CHESTPLATE), x, y)
                context.`br$popScissor`()
            }

            2 -> {
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.IRON_CHESTPLATE), x, y)
                context.`br$pushScissor`(x, y, width / 2, height)
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_CHESTPLATE), x, y)
                context.`br$popScissor`()
            }

            3 -> {
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_CHESTPLATE), x, y)
                context.`br$pushScissor`(x + width / 2, y + height / 2, width / 2, height / 2)
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.IRON_CHESTPLATE), x, y)
                context.`br$popScissor`()
            }

            4 -> {
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_CHESTPLATE), x, y)
            }

            else -> {
                context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.IRON_CHESTPLATE), x, y)
            }
        }
    }

    val maniacMiner: TeamUpgrade = TieredUpgrade(
        "haste", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased Maniac Miner .{1,3}\\s*$"),
        intArrayOf(2, 4), intArrayOf(4, 6)
    ) { context, x, y, width, height, upgradeLevel ->
        context.`br$drawTexture`(AxoStatusEffects.HASTE.`br$getSprite`(), x - 1, y - 1, 18, 18)
        context.`br$drawString`(upgradeLevel.toString(), x + width - 4, y + height - 6, -1)
    }

    val forge: TeamUpgrade = TieredUpgrade(
        "forge", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased (?:Iron|Golden|Emerald|Molten) Forge\\s*$"),
        intArrayOf(2, 4), intArrayOf(4, 6)
    ) { context, x, y, width, height, upgradeLevel ->
        if (upgradeLevel == 0) {
            context.`br$drawTexture`(AxoSprites.FURNACE_OFF, x, y, width, height)
        } else {
            var color = -1
            if (upgradeLevel == 2) {
                color = 0xFFFFFF00.toInt()
            } else if (upgradeLevel == 3) {
                color = 0xFF00FF00.toInt()
            } else if (upgradeLevel == 4) {
                color = 0xFFFF0000.toInt()
            }

            context.`br$drawTexture`(AxoSprites.FURNACE_ON, x, y, width, height, color)
            context.`br$drawString`(upgradeLevel.toString(), x + width - 4, y + height - 6, -1, true)
        }
    }

    val featherFalling: TeamUpgrade = TieredUpgrade(
        "feather_falling", Pattern.compile("^\\b[A-Za-z0-9_§]{3,16}\\b purchased Cushioned Boots .{1,2}\\s*$"),
        intArrayOf(2, 4), intArrayOf(1, 2)
    ) { context, x, y, _, _, upgradeLevel ->
        if (upgradeLevel == 1) {
            context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.IRON_BOOTS), x, y)
        } else {
            context.`br$renderGuiItemModel`(AxoItemStack.of(AxoItems.DIAMOND_BOOTS), x, y)
        }
    }

    val upgrades: Array<TeamUpgrade> =
        arrayOf(trap, sharpness, healPool, protection, maniacMiner, forge, featherFalling)

    fun onMessage(rawMessage: String) {
        for (upgrade in upgrades) {
            if (upgrade.match(rawMessage)) {
                return
            }
        }
    }
}
