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
package io.github.axolotlclient.api.multiplayer

import io.github.axolotlclient.api.FriendsScreen
import io.github.axolotlclient.api.handlers.StatusUpdateHandler
import io.github.axolotlclient.api.requests.FriendRequest
import java.util.function.Consumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DirectJoinServerScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class FriendsMultiplayerScreen(private val lastScreen: Screen?) :
    Screen(Component.translatable("api.servers.friends.title")) {

    val pinger: ServerStatusPinger = ServerStatusPinger()
    protected var serverSelectionList: FriendsMultiplayerSelectionList? = null
    private var selectButton: Button? = null
    private val friendsCountButton: Button =
        Button.builder(Component.translatable("api.servers.friends", "...")) { }.build()
    private var editingServer: ServerData? = null
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 60, 60)

    override fun init() {
        val header = this.layout.addToHeader(LinearLayout.vertical()).spacing(8)
        header.addChild(StringWidget(title, font), LayoutSettings::alignHorizontallyCenter)
        val headerRow = header.addChild(LinearLayout.horizontal()).spacing(4)
        headerRow.defaultCellSetting().alignHorizontallyCenter()
        headerRow.addChild(
            Button.builder(Component.translatable("api.servers")) {
                minecraft.gui.setScreen(JoinMultiplayerScreen(lastScreen!!))
            }.width(100).build()
        )
        headerRow.addChild(friendsCountButton)
        friendsCountButton.setWidth(100)
        friendsCountButton.active = false

        this.serverSelectionList = this.layout.addToContents(
            FriendsMultiplayerSelectionList(this, this.minecraft, this.width, this.layout.contentHeight, layout.headerHeight, 36)
        )
        FriendRequest.getInstance().getFriends().thenAccept { friends ->
            friendsCountButton.setMessage(
                Component.translatable(
                    "api.servers.friends", friends.stream().filter { u -> u.status!!.isOnline() }.count()
                )
            )
            this.serverSelectionList!!.updateList(friends)
        }
        StatusUpdateHandler.addUpdateListener(
            "friends_multiplayer_screen",
            Consumer { serverSelectionList!!.updateEntry(it) }
        )

        val linearLayout = this.layout.addToFooter(LinearLayout.vertical()).spacing(4)
        linearLayout.defaultCellSetting().alignHorizontallyCenter()
        val linearLayout2 = linearLayout.addChild(LinearLayout.horizontal()).spacing(4)
        val linearLayout3 = linearLayout.addChild(LinearLayout.horizontal()).spacing(4)

        this.selectButton = linearLayout2.addChild(
            Button.builder(Component.translatable("selectServer.select")) { this.joinSelectedServer() }
                .width(100).build()
        )
        linearLayout2.addChild(
            Button.builder(Component.translatable("selectServer.direct")) {
                this.editingServer = ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER)
                this.minecraft.gui.setScreen(DirectJoinServerScreen(this, ::directJoinCallback, this.editingServer!!))
            }.width(100).build()
        )
        linearLayout2.addChild(
            Button.builder(Component.translatable("api.friends")) {
                this.minecraft.gui.setScreen(FriendsScreen(this))
            }.width(100).build()
        )
        linearLayout3.addChild(
            Button.builder(Component.translatable("selectServer.edit")) { }.width(74).build()
        ).active = false
        linearLayout3.addChild(
            Button.builder(Component.translatable("selectServer.delete")) { }.width(74).build()
        ).active = false
        linearLayout3.addChild(
            Button.builder(Component.translatable("selectServer.refresh")) { this.refreshServerList() }.width(74).build()
        )
        linearLayout3.addChild(
            Button.builder(CommonComponents.GUI_BACK) { this.onClose() }.width(74).build()
        )
        layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
        this.onSelectedChange()
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        if (this.serverSelectionList != null) {
            this.serverSelectionList!!.updateSize(this.width, this.layout)
        }
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.lastScreen)
    }

    override fun tick() {
        this.pinger.tick()
    }

    override fun removed() {
        StatusUpdateHandler.removeUpdateListener("friends_multiplayer_screen")
        this.pinger.removeAll()
    }

    override fun extractRenderState(guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)

        if (serverSelectionList!!.children().isEmpty()) {
            guiGraphicsExtractor.centeredText(font, NO_ONLINE_FRIENDS, width / 2, height / 2 - font.lineHeight / 2, -1)
        }
    }

    private fun refreshServerList() {
        this.minecraft.gui.setScreen(FriendsMultiplayerScreen(this.lastScreen))
    }

    private fun directJoinCallback(confirmed: Boolean) {
        if (confirmed) {
            val servers = ServerList(minecraft)
            servers.load()
            val serverData = servers.get(this.editingServer!!.ip)
            if (serverData == null) {
                servers.add(this.editingServer!!, true)
                servers.save()
                this.join(this.editingServer)
            } else {
                this.join(serverData)
            }
        } else {
            this.minecraft.gui.setScreen(this)
        }
    }

    fun joinSelectedServer() {
        val entry: FriendsMultiplayerSelectionList.Entry? = this.serverSelectionList!!.selected
        if (entry != null) {
            this.join(entry.getServerData())
        }
    }

    private fun join(server: ServerData?) {
        if (server == null) {
            return
        }
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(server.ip), server, false, null)
    }

    fun setSelected(selected: FriendsMultiplayerSelectionList.Entry?) {
        this.serverSelectionList!!.setSelected(selected)
        this.onSelectedChange()
    }

    // internal (not protected): the selection list in the same package calls this, and
    // Kotlin protected is not package-visible
    internal fun onSelectedChange() {
        this.selectButton!!.active = false
        val entry: FriendsMultiplayerSelectionList.Entry? = this.serverSelectionList!!.selected
        if (entry != null && entry !is FriendsMultiplayerSelectionList.LoadingHeader) {
            this.selectButton!!.active = entry.canJoin()
        }
    }

    companion object {
        private val NO_ONLINE_FRIENDS: Component = Component.translatable("api.servers.friends.no_online_friends")
    }
}
