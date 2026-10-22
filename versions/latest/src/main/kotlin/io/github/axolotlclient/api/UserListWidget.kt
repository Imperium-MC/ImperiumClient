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
package io.github.axolotlclient.api

import io.github.axolotlclient.api.types.PkSystem
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.modules.auth.Auth
import java.util.stream.Collectors
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

class UserListWidget(
    private val screen: FriendsScreen,
    client: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    bottom: Int,
    entryHeight: Int
) : ObjectSelectionList<UserListWidget.UserListEntry>(client, width, bottom - top, top, entryHeight) {

    fun setUsers(users: List<User>) {
        users.forEach { user -> addEntry(UserListEntry(user)) }
    }

    override fun getRowWidth(): Int {
        return super.getRowWidth() + 85
    }

    public override fun addEntry(entry: UserListEntry): Int {
        return super.addEntry(entry.init(screen))
    }

    override fun scrollBarX(): Int {
        return super.scrollBarX() + 30
    }

    override fun isFocused(): Boolean {
        return this.screen.focused === this
    }

    override fun isValidClickButton(mouseButtonInfo: MouseButtonInfo): Boolean {
        return true
    }

    public override fun clearEntries() {
        super.clearEntries()
    }

    class UserListEntry(val user: User) : ObjectSelectionList.Entry<UserListEntry>() {

        private val client: Minecraft = Minecraft.getInstance()
        private var note: MutableComponent? = null
        private var screen: FriendsScreen? = null

        var isOutgoingRequest: Boolean = false
            private set

        constructor(user: User, note: MutableComponent) : this(user) {
            this.note = note.withStyle(ChatFormatting.ITALIC)
        }

        // not a Kotlin `init` - this initializes the entry with its owning screen (Java name kept)
        fun init(screen: FriendsScreen): UserListEntry {
            this.screen = screen
            return this
        }

        fun outgoing(): UserListEntry {
            isOutgoingRequest = true
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
            if (user.isSystem()) {
                val fronters: MutableComponent = Component.literal(
                    user.system!!.fronters!!.stream().map { member: PkSystem.Member -> member.displayName }
                        .collect(Collectors.joining("/"))
                )
                val tag: Component = Component.literal("(" + user.system!!.name + "/" + user.name + ")")
                    .setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY))
                graphics.text(client.font, fronters.append(tag), x + 3, y + 1, -1)
            } else {
                graphics.text(client.font, user.name, x + 3 + 33, y + 1, -1)
            }

            if (user.status!!.isOnline() && user.status!!.activity != null) {
                graphics.text(client.font, user.status!!.title, x + 3 + 33, y + 12, 0xFF808080.toInt())
                graphics.text(client.font, user.status!!.description, x + 3 + 40, y + 23, 0xFF808080.toInt())
            } else if (user.status!!.getLastOnline() != null) {
                graphics.text(client.font, user.status!!.getLastOnline(), x + 3 + 33, y + 12, 0xFF808080.toInt())
            }

            val n = note
            if (n != null) {
                graphics.text(
                    client.font, n, x + entryWidth - client.font.width(n) - 4,
                    y + entryHeight - 10, 0xFF808080.toInt()
                )
            }

            val texture: Identifier = Auth.getInstance().getSkinTexture(user.uuid)
            PlayerFaceExtractor.extractRenderState(graphics, texture, x - 1, y - 1, 33, true, false, -1)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            this.screen!!.select(this)
            if (doubleClick && client.level == null) {
                screen!!.openChat()
            }

            return false
        }
    }
}
