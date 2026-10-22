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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import java.util.function.BiConsumer

import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ComboHud : SimpleTextHudEntry() {
    private var lastTime = 0L
    private var target = -1
    private var count = 0

    override fun getId(): AxoIdentifier = ID

    override fun init() {
        Events.PLAYER_ATTACK.register(
            BiConsumer { _, attacked -> target = attacked.`br$getNetId`() }
        )
        Events.PLAYER_HURT.register(
            BiConsumer { player, attacker ->
                if (client.`br$getPlayer`() == null) {
                    return@BiConsumer
                }

                // if the entity that was hurt is the client player
                if (player.`br$getNetId`() == client.`br$getPlayer`()!!.`br$getNetId`()) {
                    target = -1
                    count = 0
                } else if (player.`br$getNetId`() == target && attacker != null &&
                    attacker.`br$getNetId`() == client.`br$getPlayer`()!!.`br$getNetId`()
                ) {
                    count++
                    lastTime = Platform.getMeasuringTimeMs()
                }
            }
        )
    }

    override fun getValue(): String {
        if (lastTime + 2000 < Platform.getMeasuringTimeMs()) {
            count = 0
        }
        if (count == 0) {
            return AxoI18n.translate("combocounter.no_hits")
        }
        if (count == 1) {
            return AxoI18n.translate("combocounter.one_hit")
        }
        return AxoI18n.translate("combocounter.hits", count)
    }

    override fun getPlaceholderValue(): String = AxoI18n.translate("combocounter.hits", 3)

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "combohud")
    }
}
