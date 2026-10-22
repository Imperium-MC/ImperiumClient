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

import io.github.axolotlclient.api.chat.ChatScreen
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.requests.FriendRequest
import io.github.axolotlclient.api.util.AlphabeticalComparator
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CommonButtons
import net.minecraft.client.gui.components.FriendsButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class FriendsScreen(private val parent: Screen?) : Screen(Component.translatable("api.screen.friends")) {

    private var widget: UserListWidget? = null

    private var chatButton: Button? = null
    private var removeButton: Button? = null
    private var onlineTab: Button? = null
    private var allTab: Button? = null
    private var pendingTab: Button? = null
    private var blockedTab: Button? = null
    private var denyButton: Button? = null
    private var acceptButton: Button? = null
    private var unblockButton: Button? = null
    private var cancelButton: Button? = null
    private var vanillaFriendsButton: FriendsButton? = null

    private var current: Tab = Tab.ONLINE

    protected constructor(parent: Screen?, tab: Tab) : this(parent) {
        current = tab
    }

    override fun extractBlurredBackground(graphics: GuiGraphicsExtractor) {
        if (minecraft.gui.screen() is FriendsOverlayScreen) return
        super.extractBlurredBackground(graphics)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.centeredText(this.font, this.title, this.width / 2, 15, -1)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (super.keyPressed(event)) {
            return true
        } else if (event.key() == 294) {
            this.refresh()
            return true
        } else if (this.widget!!.selected != null) {
            if (event.key() != 257 && event.key() != 335) {
                return this.widget!!.keyPressed(event)
            } else {
                this.openChat()
                return true
            }
        }
        return false
    }

    override fun init() {
        val widget = UserListWidget(this, minecraft, width, height, 32, height - 64, 35)
        this.widget = widget
        addRenderableWidget(widget)

        widget.clearEntries()

        if (current == Tab.ALL || current == Tab.ONLINE) {
            FriendRequest.getInstance().getFriends().whenCompleteAsync { list, _ ->
                widget.setUsers(
                    list!!.stream()
                        .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }
                        .filter { user ->
                            if (current == Tab.ONLINE) {
                                return@filter user.status!!.isOnline()
                            }
                            true
                        }.toList()
                )
            }
        } else if (current == Tab.PENDING) {
            FriendRequest.getInstance().getFriendRequests().whenCompleteAsync { con, _ ->
                con.left!!.stream()
                    .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }.forEach { user ->
                        widget.addEntry(
                            UserListWidget.UserListEntry(
                                user, Component.translatable("api.friends.pending.incoming")
                            )
                        )
                    }
                con.right!!.stream()
                    .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }.forEach { user ->
                        widget.addEntry(
                            UserListWidget.UserListEntry(
                                user, Component.translatable("api.friends.pending.outgoing")
                            ).outgoing()
                        )
                    }
            }
        } else if (current == Tab.BLOCKED) {
            FriendRequest.getInstance().getBlocked().whenCompleteAsync { list, _ ->
                widget.setUsers(
                    list!!.stream()
                        .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }
                        .toList()
                )
            }
        }

        vanillaFriendsButton = addRenderableWidget(
            CommonButtons.friends(
                20,
                {
                    OnlineOptionsScreen.confirmFriendsListEnabled(
                        this.minecraft, { this.minecraft.gui.setScreen(FriendsOverlayScreen(this)) }, this
                    )
                },
                !this.minecraft.isDemo
            )
        )
        vanillaFriendsButton!!.setPosition(width - 30, 10)

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.tab.blocked")) {
                minecraft.gui.setScreen(FriendsScreen(parent, Tab.BLOCKED))
            }.bounds(this.width / 2 + 24, this.height - 52, 57, 20).build()
        ).also { blockedTab = it }

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.tab.pending")) {
                minecraft.gui.setScreen(FriendsScreen(parent, Tab.PENDING))
            }.bounds(this.width / 2 - 34, this.height - 52, 57, 20).build()
        ).also { pendingTab = it }

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.tab.all")) {
                minecraft.gui.setScreen(FriendsScreen(parent, Tab.ALL))
            }.bounds(this.width / 2 - 94, this.height - 52, 57, 20).build()
        ).also { allTab = it }

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.tab.online")) {
                minecraft.gui.setScreen(FriendsScreen(parent, Tab.ONLINE))
            }.bounds(this.width / 2 - 154, this.height - 52, 57, 20).build()
        ).also { onlineTab = it }

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.add")) {
                minecraft.gui.setScreen(AddFriendScreen(this))
            }.bounds(this.width / 2 + 88, this.height - 52, 66, 20).build()
        )

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.remove")) {
                val entry: UserListWidget.UserListEntry? = this.widget!!.selected
                if (entry != null) {
                    removeButton!!.active = false
                    FriendRequest.getInstance().removeFriend(entry.user)
                        .thenRun { minecraft.submit(Runnable { this@FriendsScreen.refresh() }) }
                }
            }.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build()
        ).also { removeButton = it }

        addRenderableWidget(
            Button.builder(Component.translatable("api.friends.request.deny")) {
                denyRequest()
            }.bounds(this.width / 2 - 50, this.height - 28, 48, 20).build()
        ).also { denyButton = it }

        addRenderableWidget(
            Button.builder(Component.translatable("api.friends.request.accept")) {
                acceptRequest()
            }.bounds(this.width / 2 + 2, this.height - 28, 48, 20).build()
        ).also { acceptButton = it }

        addRenderableWidget(
            Button.builder(Component.translatable("api.users.unblock")) { b ->
                b.active = false
                FriendRequest.getInstance().unblockUser(widget!!.selected!!.user)
                    .thenRun { minecraft.submit(Runnable { this@FriendsScreen.refresh() }) }
            }.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build()
        ).also { unblockButton = it }

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { b ->
                b.active = false
                FriendRequest.getInstance().cancelFriendRequest(widget!!.selected!!.user)
                    .thenRun { minecraft.submit(Runnable { this@FriendsScreen.refresh() }) }
            }.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build()
        ).also { cancelButton = it }

        this.addRenderableWidget(
            Button.builder(Component.translatable("api.friends.chat")) { openChat() }
                .bounds(this.width / 2 - 154, this.height - 28, 100, 20).build()
        ).also { chatButton = it }

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { this.minecraft.gui.setScreen(this.parent) }
                .bounds(this.width / 2 + 4 + 50, this.height - 28, 100, 20).build()
        )
        updateButtonActivationStates()
    }

    private fun refresh() {
        minecraft.gui.setScreen(FriendsScreen(parent))
    }

    private fun denyRequest() {
        val entry: UserListWidget.UserListEntry? = widget!!.selected
        if (entry != null) {
            denyButton!!.active = false
            FriendRequest.getInstance().denyFriendRequest(entry.user)
                .thenRun { minecraft.submit(Runnable { this@FriendsScreen.refresh() }) }
        }
    }

    private fun acceptRequest() {
        val entry: UserListWidget.UserListEntry? = widget!!.selected
        if (entry != null) {
            acceptButton!!.active = false
            FriendRequest.getInstance().acceptFriendRequest(entry.user)
                .thenRun { minecraft.submit(Runnable { this@FriendsScreen.refresh() }) }
        }
    }

    private fun updateButtonActivationStates() {
        val entry: UserListWidget.UserListEntry? = widget!!.selected
        chatButton!!.active = entry != null && (current == Tab.ALL || current == Tab.ONLINE)

        removeButton!!.visible = true
        unblockButton!!.active = (entry != null).also { removeButton!!.active = it }
        denyButton!!.visible = false
        acceptButton!!.visible = false
        unblockButton!!.visible = false
        cancelButton!!.visible = false
        if (current == Tab.ONLINE) {
            onlineTab!!.active = false
            allTab!!.active = true
            pendingTab!!.active = true
            blockedTab!!.active = true
        } else if (current == Tab.ALL) {
            allTab!!.active = false
            onlineTab!!.active = true
            pendingTab!!.active = true
            blockedTab!!.active = true
        } else if (current == Tab.PENDING) {
            pendingTab!!.active = false
            onlineTab!!.active = true
            allTab!!.active = true
            blockedTab!!.active = true
            removeButton!!.visible = false

            if (entry != null && entry.isOutgoingRequest) {
                cancelButton!!.visible = true
            } else {
                denyButton!!.visible = true
                acceptButton!!.visible = true
            }
            denyButton!!.active = (entry != null).also { acceptButton!!.active = it }
        } else if (current == Tab.BLOCKED) {
            blockedTab!!.active = false
            onlineTab!!.active = true
            allTab!!.active = true
            pendingTab!!.active = true
            removeButton!!.visible = false
            unblockButton!!.visible = true
        }
    }

    fun openChat() {
        if (!chatButton!!.active) {
            return
        }
        val entry: UserListWidget.UserListEntry? = widget!!.selected
        if (entry != null) {
            chatButton!!.active = false
            ChannelRequest.getOrCreateDM(entry.user)
                .thenAccept { c -> minecraft.execute(Runnable { minecraft.gui.setScreen(ChatScreen(this, c)) }) }
        }
    }

    fun select(entry: UserListWidget.UserListEntry) {
        this.widget!!.selected = entry
        this.updateButtonActivationStates()
    }

    override fun tick() {
        if (this.minecraft.playerSocialManager.isFriendListEnabled && vanillaFriendsButton != null) {
            vanillaFriendsButton!!.refreshIncomingRequestCount()
        }
    }

    enum class Tab {
        ONLINE, ALL, PENDING, BLOCKED
    }
}
