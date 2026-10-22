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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry
import io.github.axolotlclient.util.ThreadExecuter
import org.apache.commons.lang3.mutable.MutableInt

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

// TODO: figure out how to implement this logic without exposing everything to bridge
class PingHud : SimpleTextHudEntry(true) {
    private val refreshDelay = IntegerOption("refreshTime", 4, 1, 15)
    private val hideInSingleplayer = BooleanOption("pinghud.hide_in_singleplayer", true)
    private val currentServerPing = MutableInt()
    private var second = 0

    override fun getId(): AxoIdentifier = ID

    override fun tickable(): Boolean = true

    override fun tick() {
        if (second >= refreshDelay.get() * 20) {
            second = 0
            ThreadExecuter.scheduleTask {
                PlatformDispatch.`pingHud$updatePing`(currentServerPing)
            }
        } else {
            second++
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(refreshDelay)
        return options
    }

    override fun getValue(): String = currentServerPing.getValue().toString()

    override fun getPlaceholderValue(): String = "68"

    override fun getLabel(): String = "ms"

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (hideInSingleplayer.get() && client.`br$isLocalServer`()) {
            return
        }
        super.render(ctx, delta)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "pinghud")
    }
}
