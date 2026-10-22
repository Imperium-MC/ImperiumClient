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

import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.ChannelInvite
import io.github.axolotlclient.api.util.UUIDHelper
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class ChannelInvitesScreen(private val parent: Screen?) : Screen(Component.translatable("api.channels.invites")) {

    private var acceptButton: Button? = null
    private var denyButton: Button? = null
    private var invites: InvitesListWidget? = null

    override fun init() {
        val hfl = HeaderAndFooterLayout(this, 33, 55)

        hfl.addTitleHeader(title, font)

        invites = hfl.addToContents(InvitesListWidget(minecraft, hfl.headerHeight, width, hfl.contentHeight, 25))

        val footer = hfl.addToFooter(LinearLayout.vertical()).spacing(4)
        val footerTop = footer.addChild(LinearLayout.horizontal()).spacing(4)
        acceptButton = footerTop.addChild(
            Button.builder(Component.translatable("api.channels.invite.accept")) { w ->
                if (invites!!.selected != null) {
                    w.active = false
                    ChannelRequest.acceptChannelInvite(invites!!.selected!!.invite)
                        .thenRun { minecraft.execute(Runnable { this@ChannelInvitesScreen.rebuildWidgets() }) }
                }
            }.width(73).build()
        )
        denyButton = footerTop.addChild(
            Button.builder(Component.translatable("api.channels.invite.ignore")) { w ->
                if (invites!!.selected != null) {
                    w.active = false
                    ChannelRequest.ignoreChannelInvite(invites!!.selected!!.invite)
                        .thenRun { minecraft.execute(Runnable { this@ChannelInvitesScreen.rebuildWidgets() }) }
                }
            }.width(73).build()
        )
        footer.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())

        hfl.arrangeElements()

        hfl.visitWidgets(this::addRenderableWidget)
        updateButtons()
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    private fun updateButtons() {
        denyButton!!.active = invites!!.selected != null
        acceptButton!!.active = invites!!.selected != null
    }

    private inner class InvitesListWidget(
        client: Minecraft,
        y: Int,
        width: Int,
        height: Int,
        entryHeight: Int
    ) : ObjectSelectionList<ChannelInvitesScreen.InvitesListWidget.InvitesListEntry>(client, width, height, y, entryHeight) {

        init {
            ChannelRequest.getChannelInvites().thenAccept { list ->
                list.stream().map { invite -> InvitesListEntry(invite) }.forEach { this.addEntry(it) }
            }
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            val bl = super.mouseClicked(event, doubleClick)
            this@ChannelInvitesScreen.updateButtons()
            return bl
        }

        internal inner class InvitesListEntry(internal val invite: ChannelInvite) :
            ObjectSelectionList.Entry<ChannelInvitesScreen.InvitesListWidget.InvitesListEntry>() {

            private val fromName: CompletableFuture<String> = UUIDHelper.tryGetUsernameAsync(invite.fromUuid)

            override fun getNarration(): Component {
                return Component.translatable("api.channels.invite.desc", invite.fromUuid, invite.channelName)
            }

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovering: Boolean,
                partialTick: Float
            ) {
                val left = contentX
                val top = contentY
                graphics.text(
                    font, Component.translatable("api.channels.invite.name", invite.channelName), left + 2, top + 2, -1
                )
                if (fromName.isDone) {
                    graphics.text(
                        font,
                        Component.translatable("api.channels.invite.from", fromName.join())
                            .withStyle(Style.EMPTY.withItalic(true)),
                        left + 15, top + this@InvitesListWidget.height - font.lineHeight - 1, 0xFF808080.toInt()
                    )
                }
            }
        }
    }
}
