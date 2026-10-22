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

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.ContextMenuContainer
import io.github.axolotlclient.api.ContextMenuScreen
import io.github.axolotlclient.api.handlers.ChatHandler
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.Channel
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ChatScreen(
    override val parent: Screen?,
    private val channel: Channel
) : Screen(Component.literal(channel.name!!)), ContextMenuScreen {

    private val contextMenu: ContextMenuContainer = ContextMenuContainer()
    private var widget: ChatWidget? = null
    private var users: ChatUserListWidget? = null
    private var input: EditBox? = null

    override val menuContainer: ContextMenuContainer
        get() = contextMenu

    override val self: Screen
        get() = this

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        graphics.centeredText(this.font, channel.name!!, this.width / 2, 15, -1)
    }

    override fun init() {
        val chatList = addRenderableWidget(ChatListWidget(this, width, height, 0, 30, 55, height - 90))
        ChannelRequest.getChannelList().thenAccept { chatList.addChannels(it) }
            .thenRun { chatList.setActiveChannel(channel) }

        val widget = ChatWidget(channel, 65, 30, width - 155, height - 90, this)
        this.widget = widget
        addRenderableWidget(widget)

        val users = ChatUserListWidget(this, minecraft, 80, height - 20, 30, height - 60, 25)
        this.users = users
        users.setX(width - 80)
        users.setUsers(channel.getAllUsers(), channel)
        addRenderableWidget(users)

        val input: EditBox = object : EditBox(
            font, width / 2 - 150, height - 50,
            300, 20, Component.translatable("api.chat.enterMessage")
        ) {
            override fun keyPressed(event: KeyEvent): Boolean {
                if (event.key() == InputConstants.KEY_RETURN && !this.value.isEmpty()) {
                    ChatHandler.getInstance().sendMessage(channel, this.value)
                    this.setValue("")
                    return true
                }
                return super.keyPressed(event)
            }
        }
        this.input = input
        addRenderableWidget(input)

        input.setSuggestion(
            Component.translatable(
                if (channel.isDM()) "api.chat.messageUser" else "api.chat.messageGroup", channel.name!!
            ).getString()
        )
        input.setResponder { s ->
            if (s.isEmpty() && !input.isFocused) {
                input.setSuggestion(
                    Component.translatable(
                        if (channel.isDM()) "api.chat.messageUser" else "api.chat.messageGroup", channel.name!!
                    ).getString()
                )
            } else {
                input.setSuggestion("")
            }
        }
        input.setMaxLength(1024)

        if (channel.owner == API.getInstance().self) {
            addRenderableWidget(
                Button.builder(Component.translatable("api.channel.configure")) {
                    minecraft.gui.setScreen(ChannelSettingsScreen(this, channel))
                }.bounds(width - 60, 5, 50, 20).build()
            )
        }

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { this.minecraft.gui.setScreen(this.parent) }
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build()
        )

        addRenderableOnly(contextMenu)
    }

    override fun removed() {
        if (widget != null) {
            widget!!.remove()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (contextMenu.menu != null) {
            if (contextMenu.mouseClicked(event, doubleClick)) {
                return true
            }
            contextMenu.removeMenu()
        }
        return super.mouseClicked(event, doubleClick)
    }
}
