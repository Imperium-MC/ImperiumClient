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
import io.github.axolotlclient.api.ContextMenu
import io.github.axolotlclient.api.ContextMenuScreen
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.types.Relation
import java.util.function.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component

class ChatListWidget @JvmOverloads constructor(
    screen: ContextMenuScreen,
    screenWidth: Int,
    screenHeight: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val predicate: Predicate<Channel> = Predicate { _ -> true }
) : ContainerObjectSelectionList<ChatListWidget.ChatListEntry>(Minecraft.getInstance(), width, height, y, 25) {

    protected val screen: ContextMenuScreen = screen

    init {
        setX(x)
    }

    fun addChannels(channels: List<Channel>) {
        channels.stream().filter(predicate).sorted().forEach { c -> addEntryToTop(ChatListEntry(c)) }
        setScrollAmount(0.0)
    }

    fun setActiveChannel(channel: Channel) {
        children().stream()
            .filter { e -> e.channel == channel }
            .findFirst().ifPresent { c -> c.widget.active = false }
    }

    override fun getRowWidth(): Int {
        return getWidth() - 8
    }

    override fun scrollBarX(): Int {
        return super.scrollBarX() - 6
    }

    override fun isValidClickButton(index: MouseButtonInfo): Boolean {
        return true
    }

    inner class ChatListEntry(internal val channel: Channel) :
        ContainerObjectSelectionList.Entry<ChatListEntry>() {

        internal val widget: Button = Button.builder(
            Component.literal(channel.name!!),
            { minecraft.gui.setScreen(ChatScreen(screen.parent, channel)) }
        )
            .width(getRowWidth()).build()

        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            widget.setX(contentX)
            widget.setY(contentY)
            widget.extractRenderState(graphics, mouseX, mouseY, tickDelta)
        }

        override fun children(): List<GuiEventListener> {
            return listOf(widget)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (widget.isMouseOver(event.x(), event.y())) {
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    return widget.mouseClicked(event, doubleClick)
                } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    val builder: ContextMenu.Builder = ContextMenu.builder()
                        .title(Component.literal(channel.name!!))
                        .spacer()
                        .entry(Component.translatable("api.channel.configure")) {
                            minecraft.gui.setScreen(ChannelSettingsScreen(screen.self, channel))
                        }
                        .spacer()
                    if (channel.owner == API.getInstance().self) {
                        builder.entry(Component.translatable("api.channel.delete")) {
                            minecraft.gui.setScreen(
                                ConfirmScreen(
                                    { bl: Boolean ->
                                        if (bl) {
                                            ChannelRequest.leaveOrDeleteChannel(channel).whenComplete { _, _ ->
                                                minecraft.execute(Runnable { minecraft.gui.setScreen(screen.self) })
                                            }
                                        }
                                    },
                                    Component.translatable("api.channels.delete.confirm"),
                                    Component.translatable("api.channels.delete.confirm.desc", channel.name!!)
                                )
                            )
                        }
                    } else {
                        builder.entry(Component.translatable("api.channel.leave")) {
                            minecraft.gui.setScreen(
                                ConfirmScreen(
                                    { bl: Boolean ->
                                        if (bl) {
                                            ChannelRequest.leaveOrDeleteChannel(channel).whenComplete { _, _ ->
                                                minecraft.execute(Runnable { minecraft.gui.setScreen(screen.self) })
                                            }
                                        }
                                    },
                                    Component.translatable("api.channels.leave.confirm"),
                                    if (channel.owner.relation == Relation.FRIEND) {
                                        Component.translatable("api.channels.leave.confirm.desc_add", channel.name!!)
                                    } else {
                                        Component.translatable("api.channels.leave.confirm.desc_invite", channel.name!!)
                                    }
                                )
                            )
                        }
                    }
                    screen.setContextMenu(builder.build())
                    return true
                }
            }
            return super.mouseClicked(event, doubleClick)
        }

        override fun narratables(): List<NarratableEntry> {
            return listOf(widget)
        }
    }
}
