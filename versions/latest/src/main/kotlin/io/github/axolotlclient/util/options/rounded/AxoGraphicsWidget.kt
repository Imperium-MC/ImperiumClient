/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util.options.rounded

import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.screen.GraphicsEditorScreen
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.widgets.GraphicsWidget
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.widgets.RoundedButtonWidget
import io.github.axolotlclient.util.notifications.Notifications
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component
import java.util.Base64

@Suppress("unused")
class AxoGraphicsWidget(x: Int, y: Int, width: Int, height: Int, private val option: GraphicsOption) :
    GraphicsWidget(x, y, width, height, option) {

    override fun onPress(input: InputWithModifiers) {
        Minecraft.getInstance().gui.setScreen(AxoGraphicsEditorScreen(Minecraft.getInstance().gui.screen(), this.option))
    }

    class AxoGraphicsEditorScreen(parent: Screen?, private val option: GraphicsOption) :
        GraphicsEditorScreen(parent, option) {

        override fun init() {
            super.init()

            val clear = children().last() as RoundedButtonWidget
            val buttonX = clear.x
            val buttonY = clear.y
            val buttonWidth = clear.width
            addRenderableWidget(
                RoundedButtonWidget(buttonX, buttonY + 24, Component.translatable("graphics.copy_text")) {
                    minecraft.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(option.get().pixelData))
                }
            ).setWidth(buttonWidth)
            addRenderableWidget(
                RoundedButtonWidget(buttonX, buttonY + 48, Component.translatable("graphics.paste_text")) {
                    try {
                        option.get().setPixelData(Base64.getDecoder().decode(minecraft.keyboardHandler.clipboard))
                    } catch (e: IllegalArgumentException) {
                        Notifications.getInstance().addStatus("graphics.paste_text.failed", "graphics.paste_text.failed.desc")
                    }
                }
            ).setWidth(buttonWidth)
        }

        override fun getCurrentHeight(): Int {
            return super.getCurrentHeight() - 24 * 2
        }
    }
}
