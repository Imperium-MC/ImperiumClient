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
package io.github.axolotlclient.api.chat

import io.github.axolotlclient.api.ContextMenuContainer
import io.github.axolotlclient.api.ContextMenuScreen
import io.github.axolotlclient.api.requests.ChannelRequest
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ChatListScreen(override val parent: Screen?) :
    Screen(Component.translatable("api.chats")), ContextMenuScreen {

    private val container: ContextMenuContainer = ContextMenuContainer()

    override val menuContainer: ContextMenuContainer
        get() = container

    override val self: Screen
        get() = this

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        graphics.centeredText(font, title, width / 2, 20, -1)
        graphics.centeredText(font, Component.translatable("api.chat.dms"), width / 2 + 80, 40, -1)
        graphics.centeredText(font, Component.translatable("api.chat.groups"), width / 2 - 80, 40, -1)
    }

    override fun init() {
        val groups = addRenderableWidget(
            ChatListWidget(this, width, height, width / 2 - 155, 55, 150, height - 105) { c -> !c.isDM() }
        )
        val dms = addRenderableWidget(
            ChatListWidget(this, width, height, width / 2 + 5, 55, 150, height - 105) { c -> c.isDM() }
        )

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { minecraft.gui.setScreen(parent) }
                .bounds(this.width / 2 + 5, this.height - 40, 150, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("api.chat.groups.create")) {
                minecraft.gui.setScreen(CreateChannelScreen(this))
            }.bounds(this.width / 2 - 155, this.height - 40, 150, 20).build()
        )
        ChannelRequest.getChannelList().whenCompleteAsync { list, _ ->
            groups.addChannels(list!!)
            dms.addChannels(list)
        }
        addRenderableOnly(container)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (container.menu != null) {
            if (container.mouseClicked(event, doubleClick)) {
                return true
            }
            container.removeMenu()
        }
        return super.mouseClicked(event, doubleClick)
    }
}
