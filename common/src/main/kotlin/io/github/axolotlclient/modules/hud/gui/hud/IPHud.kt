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

package io.github.axolotlclient.modules.hud.gui.hud

import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.network.AxoServerData
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.util.DrawPosition

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class IPHud : TextHudEntry(115, 13, true) {
    private val showIcon = BooleanOption("iphud.show_icon", false)
    private var sprite: AxoSprite.Dynamic? = null
    private val hudHeight = IntegerOption("hud.height", 13, 9, 64)

    init {
        Events.DISCONNECT.register(Runnable {
            if (sprite != null) {
                sprite!!.close()
                sprite = null
            }
        })

        Events.CONNECTION_PLAY_READY.register(Consumer<AxoServerData?> {
            if (showIcon.get()) {
                sprite = PlatformDispatch.`ipHud$getServerIcon`()
            }
        })
    }

    /**
     * The address only changes when connecting to a different server, so this entry is safe
     * to serve from the `hud_frame_limit` recording despite being a text HUD.
     */
    override fun dynamicContent(): Boolean = false

    override fun getId(): AxoIdentifier = ID

    fun getValue(): String {
        if (client.`br$isLocalServer`() || client.`br$getServerAddress`() == null) {
            return "Singleplayer"
        }

        return client.`br$getServerAddress`()!!
    }

    private fun updateSize(graphics: AxoRenderContext) {
        val w = getContentWidth()
        val h = getContentHeight()
        val hNew = hudHeight.get()
        var updated = false
        if (h != hNew) {
            setContentHeight(hNew)
            updated = true
        }
        var req = graphics.`br$getFont`().`br$getWidth`(getValue()) + 4
        if (showIcon.get() && sprite != null) {
            req += getContentHeight() + 1
        }
        if (w != req) {
            setContentWidth(req)
            updated = true
        }
        if (updated) {
            onBoundsUpdate()
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(showIcon)
        options.add(hudHeight)
        return options
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        updateSize(graphics)
        val pos = contentPos
        var textX = pos.x() + getContentWidth() / 2

        val icon = sprite
        if (showIcon.get() && icon != null) {
            val imageSize = getContentHeight() - 2
            textX += imageSize / 2
            graphics.`br$drawTexture`(icon, pos.x() + 1, pos.y() + 1, imageSize, imageSize)
        }

        graphics.`br$drawCenteredString`(
            getValue(), textX, pos.y() + getContentHeight() / 2 - client.`br$getFont`().`br$getFontHeight`() / 2,
            textColor.get().toInt(), true
        )
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        renderComponent(graphics, delta)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "iphud")
    }
}
