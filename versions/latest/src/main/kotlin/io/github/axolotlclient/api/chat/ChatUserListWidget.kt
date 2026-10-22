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
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.requests.FriendRequest
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.types.Relation
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.api.util.AlphabeticalComparator
import io.github.axolotlclient.modules.auth.Auth
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util

class ChatUserListWidget(
    private val screen: ChatScreen,
    client: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    bottom: Int,
    entryHeight: Int
) : ObjectSelectionList<ChatUserListWidget.UserListEntry>(client, width, bottom - top, top, entryHeight) {

    fun setUsers(users: List<User>, channel: Channel) {
        users.stream()
            .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }
            .forEach { user -> addEntry(UserListEntry(user, channel)) }
    }

    override fun getRowWidth(): Int {
        return width - 5
    }

    public override fun addEntry(entry: UserListEntry): Int {
        return super.addEntry(entry.init(screen))
    }

    override fun scrollBarX(): Int {
        return getRowLeft() + width - 8
    }

    override fun isFocused(): Boolean {
        return this.screen.focused === this
    }

    override fun isValidClickButton(index: MouseButtonInfo): Boolean {
        return true
    }

    inner class UserListEntry internal constructor(
        internal val user: User,
        private val channel: Channel
    ) : ObjectSelectionList.Entry<UserListEntry>() {

        private val client: Minecraft = Minecraft.getInstance()
        private var screen: ChatScreen? = null

        fun init(screen: ChatScreen): UserListEntry {
            this.screen = screen
            return this
        }

        override fun getNarration(): Component {
            return Component.literal(user.name!!)
        }

        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            val x = contentX
            val y = contentY
            val entryWidth = contentWidth
            val entryHeight = contentHeight
            if (hovered && screen!!.hasContextMenu()) {
                graphics.fill(x - 2, y - 1, x + entryWidth - 3, y + entryHeight + 1, 0x55ffffff.toInt())
            }
            drawScrollableText(
                graphics, client.font, Component.literal(user.name!!), x + 3 + entryHeight, y + 1,
                x + entryWidth - 6, y + 1 + client.font.lineHeight + 2, -1
            )
            drawScrollableText(
                graphics, client.font, Component.literal(user.status!!.title), x + 3 + entryHeight,
                y + 12, x + entryWidth - 6, y + 12 + client.font.lineHeight + 2, 8421504
            )

            val texture: Identifier = Auth.getInstance().getSkinTexture(user.uuid)
            PlayerFaceExtractor.extractRenderState(graphics, texture, x, y, entryHeight, true, false, -1)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            this@ChatUserListWidget.selected = this
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) { // right click

                if (user != API.getInstance().self) {
                    val menu: ContextMenu.Builder =
                        ContextMenu.builder().title(Component.literal(user.name!!)).spacer()
                    if (!channel.isDM()) {
                        menu.entry(Component.translatable("api.friends.chat")) {
                            ChannelRequest.getOrCreateDM(user).whenCompleteAsync { channel, _ ->
                                client.execute(
                                    Runnable { client.gui.setScreen(ChatScreen(screen!!.parent, channel)) }
                                )
                            }
                        }.spacer()
                    }
                    if (user.relation != Relation.BLOCKED) {
                        if (user.relation != Relation.FRIEND) {
                            menu.entry(Component.translatable("api.friends.add")) {
                                FriendRequest.getInstance().addFriend(user.uuid)
                            }.spacer()
                        }
                        menu.entry(Component.translatable("api.users.block")) {
                            FriendRequest.getInstance().blockUser(user)
                        }
                    } else {
                        menu.entry(Component.translatable("api.users.unblock")) {
                            FriendRequest.getInstance().unblockUser(user)
                        }
                    }
                    if (channel.owner == API.getInstance().self) {
                        menu.spacer().entry(Component.translatable("api.channel.remove_user")) {
                            ChannelRequest.removeUserFromChannel(channel, user)
                        }
                    }
                    screen!!.setContextMenu(menu.build())
                    return true
                }
            }

            return false
        }

    }
}

// was a protected static method on the Java inner entry class; inner Kotlin classes
// cannot own companion objects, so it lives at file level
private fun drawScrollableText(
    graphics: GuiGraphicsExtractor,
    textRenderer: Font,
    text: Component,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    color: Int
) {
    val i = textRenderer.width(text)
    val j = (top + bottom - 9) / 2 + 1
    val k = right - left
    if (i > k) {
        val l = i - k
        val d = Util.getMillis() / 1000.0
        val e = Math.max(l * 0.5, 3.0)
        val f = Math.sin((Math.PI / 2) * Math.cos(Math.PI * 2 * d / e)) / 2.0 + 0.5
        val g = Mth.lerp(f, 0.0, l.toDouble())
        graphics.enableScissor(left, top, right, bottom)
        graphics.text(textRenderer, text, left - g.toInt(), j, color)
        graphics.disableScissor()
    } else {
        graphics.text(textRenderer, text, left, j, color)
    }
}
