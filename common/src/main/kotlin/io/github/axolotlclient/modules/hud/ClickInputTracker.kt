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

package io.github.axolotlclient.modules.hud

import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.modules.AbstractCommonModule
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongPredicate
import it.unimi.dsi.fastutil.longs.LongSet

class ClickInputTracker : AbstractCommonModule() {
    @JvmField
    val leftMouse: ClickList = ClickList()
    @JvmField
    val leftBind: ClickList = ClickList()
    @JvmField
    val rightMouse: ClickList = ClickList()
    @JvmField
    val rightBind: ClickList = ClickList()

    override fun init() {
        Events.KEY_INPUT.register { key ->
            if (key == client.`br$getGameOptions`().`br$getAttackKey`().`br$getBoundKey`()) {
                leftBind.click()
            } else if (key == client.`br$getGameOptions`().`br$getUseKey`().`br$getBoundKey`()) {
                rightBind.click()
            }

            if (key == AxoKeys.MOUSE_LEFT) {
                leftMouse.click()
            } else if (key == AxoKeys.MOUSE_RIGHT) {
                rightMouse.click()
            }
        }
    }

    override fun tick() {
        leftMouse.update()
        leftBind.update()
        rightMouse.update()
        rightBind.update()
    }

    class ClickList {
        private val clicks: LongSet = LongOpenHashSet()

        fun update() {
            clicks.removeIf(LongPredicate { click -> Platform.getMeasuringTimeMs() - click > 1000 })
        }

        fun click() {
            clicks.add(Platform.getMeasuringTimeMs())
        }

        fun clicks(): Int = clicks.size
    }

    companion object {
        private val instance = ClickInputTracker()

        @JvmStatic
        fun getInstance(): ClickInputTracker = instance
    }
}
