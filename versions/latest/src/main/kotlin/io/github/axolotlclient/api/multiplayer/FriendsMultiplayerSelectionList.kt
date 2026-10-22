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

import com.google.common.collect.Lists
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.logging.LogUtils
import io.github.axolotlclient.api.types.PkSystem
import io.github.axolotlclient.api.types.Status
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.modules.auth.Auth
import java.net.UnknownHostException
import java.util.ArrayList
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.stream.Collectors
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.ChatFormatting
import net.minecraft.DefaultUncaughtExceptionHandler
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.FaviconTexture
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.server.network.EventLoopGroupHolder
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import org.slf4j.Logger

class FriendsMultiplayerSelectionList(
    private val screen: FriendsMultiplayerScreen,
    minecraft: Minecraft,
    width: Int,
    height: Int,
    y: Int,
    itemHeight: Int
) : ObjectSelectionList<FriendsMultiplayerSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

    private val friendEntries: MutableList<Entry> = ArrayList()
    private val loadingHeader: LoadingHeader = LoadingHeader()

    init {
        addEntry(loadingHeader)
    }

    private fun refreshEntries() {
        this.clearEntries()
        this.friendEntries.forEach { this.addEntry(it) }
    }

    override fun setSelected(entry: Entry?) {
        super.setSelected(entry)
        this.screen.onSelectedChange()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val entry: Entry? = this.selected
        return entry != null && entry.keyPressed(event) || super.keyPressed(event)
    }

    fun updateList(friends: List<User>) {
        this.friendEntries.clear()

        for (friend in friends) {
            if (friend.status!!.isOnline()) {
                this.friendEntries.add(createEntry(friend))
            }
        }
        this.refreshEntries()
    }

    private fun createEntry(friend: User): Entry {
        if (friend.status!!.activity != null && friend.status!!.activity!!.hasMetadata()) {
            if (friend.status!!.activity!!.hasMetadata(Status.Activity.ExternalServerMetadata.ID)) {
                return externalServerEntry(this.screen, friend)
            } else {
                return e4mcServerFriendEntry(this.screen, friend)
            }
        }
        return StatusFriendEntry(friend)
    }

    fun updateEntry(user: User) {
        this.friendEntries.stream().filter { e1 ->
            if (e1 is StatusFriendEntry) {
                return@filter e1.user == user
            } else if (e1 is ServerEntry) {
                return@filter e1.user == user
            }
            false
        }.findFirst().ifPresent { e ->
            this.friendEntries[friendEntries.indexOf(e)] = createEntry(user)
            refreshEntries()
        }
    }

    override fun getRowWidth(): Int {
        return 305
    }

    @Environment(EnvType.CLIENT)
    abstract class Entry : ObjectSelectionList.Entry<FriendsMultiplayerSelectionList.Entry>(), AutoCloseable {
        override fun close() {
        }

        open fun canJoin(): Boolean {
            return false
        }

        open fun getServerData(): ServerData? {
            return null
        }
    }

    inner class StatusFriendEntry(val user: User) : Entry() {

        override fun getNarration(): Component {
            return Component.literal(user.name!!)
        }

        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            if (user.isSystem()) {
                val fronters: MutableComponent = Component.literal(
                    user.system!!.fronters!!.stream().map { member: PkSystem.Member -> member.displayName }
                        .collect(Collectors.joining("/"))
                )
                val tag: Component = Component.literal("(" + user.system!!.name + "/" + user.name + ")")
                    .setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY))
                graphics.text(minecraft.font, fronters.append(tag), contentX + 3, contentY + 1, -1)
            } else {
                graphics.text(minecraft.font, user.name!!, contentX + 3 + 32, contentY + 1, -1)
            }

            if (user.status!!.isOnline() && user.status!!.activity != null) {
                graphics.text(minecraft.font, user.status!!.title, contentX + 3 + 32, contentY + 12, 0xFF808080.toInt())
                graphics.text(minecraft.font, user.status!!.description, contentX + 3 + 40, contentY + 23, 0xFF808080.toInt())
            } else if (user.status!!.getLastOnline() != null) {
                graphics.text(minecraft.font, user.status!!.getLastOnline(), contentX + 3 + 32, contentY + 12, 0xFF808080.toInt())
            }

            val texture: Identifier = Auth.getInstance().getSkinTexture(user)
            PlayerFaceExtractor.extractRenderState(graphics, texture, contentX, contentY, 32, true, false, -1)
        }
    }

    internal open inner class ServerEntry(
        screen: FriendsMultiplayerScreen,
        serverData: ServerData,
        val user: User
    ) : Entry() {

        // stored separately from the getter: a `val serverData` would generate a
        // getServerData() JVM signature that clashes with Entry.getServerData()
        protected val serverDataField: ServerData = serverData

        override fun getServerData(): ServerData {
            return serverDataField
        }

        private val screen: FriendsMultiplayerScreen = screen
        private val minecraft: Minecraft = Minecraft.getInstance()
        private val icon: FaviconTexture = FaviconTexture.forServer(
            minecraft.textureManager, serverData.ip ?: (user.uuid + "_" + serverData.name)
        )
        private var lastIconBytes: ByteArray? = null
        private var onlinePlayersTooltip: List<Component>? = null
        private var statusIcon: Identifier? = null
        private var statusIconTooltip: Component? = null

        protected open fun refreshStatus() {
            this.onlinePlayersTooltip = null
            if (!isPublished()) {
                this.serverDataField.setState(ServerData.State.UNREACHABLE)
            }
            when (this.serverDataField.state()) {
                ServerData.State.INITIAL, ServerData.State.PINGING -> {
                    this.statusIcon = PING_1_SPRITE
                    this.statusIconTooltip = PINGING_STATUS
                }
                ServerData.State.INCOMPATIBLE -> {
                    this.statusIcon = INCOMPATIBLE_SPRITE
                    this.onlinePlayersTooltip = this.serverDataField.playerList
                    this.statusIconTooltip = INCOMPATIBLE_STATUS
                }
                ServerData.State.UNREACHABLE -> {
                    this.statusIcon = UNREACHABLE_SPRITE
                    if (!isPublished()) {
                        return
                    }
                    this.statusIconTooltip = NO_CONNECTION_STATUS
                }
                ServerData.State.SUCCESSFUL -> {
                    this.statusIcon = if (this.serverDataField.ping < 150L) {
                        PING_5_SPRITE
                    } else if (this.serverDataField.ping < 300L) {
                        PING_4_SPRITE
                    } else if (this.serverDataField.ping < 600L) {
                        PING_3_SPRITE
                    } else if (this.serverDataField.ping < 1000L) {
                        PING_2_SPRITE
                    } else {
                        PING_1_SPRITE
                    }

                    this.statusIconTooltip = Component.translatable("multiplayer.status.ping", this.serverDataField.ping)
                    this.onlinePlayersTooltip = this.serverDataField.playerList
                }
            }
        }

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            if (this.serverDataField.state() == ServerData.State.INITIAL) {
                this.serverDataField.setState(ServerData.State.PINGING)
                this.serverDataField.motd = CommonComponents.EMPTY
                this.serverDataField.status = CommonComponents.EMPTY
                THREAD_POOL.submit(
                    Runnable {
                        try {
                            this.screen
                                .pinger
                                .pingServer(
                                    this.serverDataField,
                                    Runnable { },
                                    Runnable {
                                        this.serverDataField.setState(
                                            if (this.serverDataField.protocol == SharedConstants.getCurrentVersion().protocolVersion()) {
                                                ServerData.State.SUCCESSFUL
                                            } else {
                                                ServerData.State.INCOMPATIBLE
                                            }
                                        )
                                        this.minecraft.execute(Runnable { this.refreshStatus() })
                                    },
                                    EventLoopGroupHolder.remote(this.minecraft.options.useNativeTransport())
                                )
                        } catch (var2: UnknownHostException) {
                            this.serverDataField.setState(ServerData.State.UNREACHABLE)
                            this.serverDataField.motd = CANT_RESOLVE_TEXT
                            this.minecraft.execute(Runnable { this.refreshStatus() })
                        } catch (var3: Exception) {
                            this.serverDataField.setState(ServerData.State.UNREACHABLE)
                            this.serverDataField.motd = CANT_CONNECT_TEXT
                            this.minecraft.execute(Runnable { this.refreshStatus() })
                        }
                    }
                )
            }

            guiGraphicsExtractor.text(this.minecraft.font, this.serverDataField.name, contentX + ICON_WIDTH + 3, contentY + 1, -1)
            val list: List<FormattedCharSequence> = this.minecraft.font.split(this.serverDataField.motd, this@FriendsMultiplayerSelectionList.width - ICON_WIDTH - 2)

            for (i in 0 until minOf(list.size, 2)) {
                guiGraphicsExtractor.text(
                    this.minecraft.font, list[i], contentX + ICON_WIDTH + 3, contentY + 12 + 9 * i, -8355712
                )
            }

            guiGraphicsExtractor.blit(
                RenderPipelines.GUI_TEXTURED, this.icon.textureLocation(), contentX, contentY,
                0.0f, 0.0f, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT
            )
            val texture: Identifier = Auth.getInstance().getSkinTexture(user)
            PlayerFaceExtractor.extractRenderState(
                guiGraphicsExtractor, texture, contentX + ICON_WIDTH - 10, contentY + ICON_HEIGHT - 10, 10, true, false, -1
            )
            if (this.serverDataField.state() == ServerData.State.PINGING) {
                var i = ((Util.getMillis() / 100L + this@FriendsMultiplayerSelectionList.children().indexOf(this) * 2) and 7L).toInt()
                if (i > 4) {
                    i = 8 - i
                }
                this.statusIcon = when (i) {
                    1 -> PINGING_2_SPRITE
                    2 -> PINGING_3_SPRITE
                    3 -> PINGING_4_SPRITE
                    4 -> PINGING_5_SPRITE
                    else -> PINGING_1_SPRITE
                }
            }

            val i = contentX + this@FriendsMultiplayerSelectionList.width - STATUS_ICON_WIDTH - SPACING
            val statusIcon = this.statusIcon
            if (statusIcon != null) {
                guiGraphicsExtractor.blitSprite(
                    RenderPipelines.GUI_TEXTURED, statusIcon, i, contentY, STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT
                )
            }

            val bs = this.serverDataField.iconBytes
            if (!bs.contentEquals(this.lastIconBytes)) {
                if (this.uploadIcon(bs)) {
                    this.lastIconBytes = bs
                } else {
                    this.serverDataField.setIconBytes(null)
                }
            }

            val component: Component
            if (!isPublished()) {
                component = NOT_PUBLISHED_STATUS
            } else {
                if (this.serverDataField.state() == ServerData.State.INCOMPATIBLE) {
                    component = this.serverDataField.version!!.copy().withStyle(ChatFormatting.RED)
                } else {
                    component = this.serverDataField.status!!
                }
            }
            val j = this.minecraft.font.width(component)
            val k = i - j - SPACING
            guiGraphicsExtractor.text(this.minecraft.font, component, k, contentY + 1, -8355712)
            val statusIconTooltip = this.statusIconTooltip
            val onlinePlayersTooltip = this.onlinePlayersTooltip
            if (statusIconTooltip != null && mouseX >= i && mouseX <= i + STATUS_ICON_WIDTH &&
                mouseY >= contentY && mouseY <= contentY + STATUS_ICON_HEIGHT
            ) {
                guiGraphicsExtractor.setTooltipForNextFrame(statusIconTooltip, mouseX, mouseY)
            } else if (onlinePlayersTooltip != null && mouseX >= k && mouseX <= k + j &&
                mouseY >= contentY && mouseY <= contentY - 1 + 9
            ) {
                guiGraphicsExtractor.setTooltipForNextFrame(
                    Lists.transform(onlinePlayersTooltip) { obj: Component -> obj.visualOrderText },
                    mouseX, mouseY
                )
            }

            if (hovering) {
                val l = mouseX - contentX
                if (this.canJoin()) {
                    guiGraphicsExtractor.fill(contentX, contentY, contentX + ICON_WIDTH, contentY + ICON_HEIGHT, -1601138544)
                    if (l < ICON_WIDTH && l > ICON_WIDTH / 2) {
                        guiGraphicsExtractor.blitSprite(
                            RenderPipelines.GUI_TEXTURED, JOIN_HIGHLIGHTED_SPRITE, contentX, contentY, ICON_WIDTH, ICON_HEIGHT
                        )
                    } else {
                        guiGraphicsExtractor.blitSprite(
                            RenderPipelines.GUI_TEXTURED, JOIN_SPRITE, contentX, contentY, ICON_WIDTH, ICON_HEIGHT
                        )
                    }
                }
            }
        }

        protected open fun isPublished(): Boolean {
            return true
        }

        override fun canJoin(): Boolean {
            return serverDataField.state() == ServerData.State.SUCCESSFUL && isPublished()
        }

        private fun uploadIcon(iconBytes: ByteArray?): Boolean {
            if (iconBytes == null) {
                this.icon.clear()
            } else {
                try {
                    this.icon.upload(NativeImage.read(iconBytes))
                } catch (var3: Throwable) {
                    LOGGER.error("Invalid icon for server {} ({})", this.serverDataField.name, this.serverDataField.ip, var3)
                    return false
                }
            }

            return true
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            val d = event.x() - this@FriendsMultiplayerSelectionList.getRowLeft()
            if (d <= 32.0) {
                if (d < 32.0 && d > 16.0 && this.canJoin()) {
                    this.screen.setSelected(this)
                    this.screen.joinSelectedServer()
                    return true
                }
            }

            this.screen.setSelected(this)
            if (doubleClick && canJoin()) {
                this.screen.joinSelectedServer()
            }

            return super.mouseClicked(event, doubleClick)
        }

        override fun getNarration(): Component {
            val mutableComponent: MutableComponent = Component.empty()
            mutableComponent.append(Component.translatable("narrator.select", this.serverDataField.name))
            mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
            when (this.serverDataField.state()) {
                ServerData.State.PINGING -> mutableComponent.append(PINGING_STATUS)
                ServerData.State.INCOMPATIBLE -> {
                    mutableComponent.append(INCOMPATIBLE_STATUS)
                    mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                    mutableComponent.append(Component.translatable("multiplayer.status.version.narration", this.serverDataField.version))
                    mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                    mutableComponent.append(Component.translatable("multiplayer.status.motd.narration", this.serverDataField.motd))
                }
                ServerData.State.UNREACHABLE -> mutableComponent.append(NO_CONNECTION_STATUS)
                else -> {
                    mutableComponent.append(ONLINE_STATUS)
                    mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                    mutableComponent.append(Component.translatable("multiplayer.status.ping.narration", this.serverDataField.ping))
                    mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                    mutableComponent.append(Component.translatable("multiplayer.status.motd.narration", this.serverDataField.motd))
                    if (this.serverDataField.players != null) {
                        mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                        mutableComponent.append(
                            Component.translatable(
                                "multiplayer.status.player_count.narration",
                                this.serverDataField.players!!.online(), this.serverDataField.players!!.max()
                            )
                        )
                        mutableComponent.append(CommonComponents.NARRATION_SEPARATOR)
                        mutableComponent.append(
                            ComponentUtils.formatList(this.serverDataField.playerList, Component.literal(", "))
                        )
                    }
                }
            }

            return mutableComponent
        }

        override fun close() {
            this.icon.close()
        }

    }

    private fun externalServerEntry(screen: FriendsMultiplayerScreen, friend: User): ExternalServerFriendEntry {
        val metadata: Status.Activity.ExternalServerMetadata =
            friend.status!!.activity!!.metadata!!.attributes as Status.Activity.ExternalServerMetadata
        return ExternalServerFriendEntry(
            screen, metadata, ServerData(
                ServerInfoUtil.forceNonNull(metadata.serverName),
                ServerInfoUtil.forceNonNull(metadata.address), ServerData.Type.OTHER
            ), friend
        )
    }

    internal inner class ExternalServerFriendEntry internal constructor(
        screen: FriendsMultiplayerScreen,
        private val statusDescription: Status.Activity.ExternalServerMetadata,
        serverData: ServerData,
        friend: User
    ) : ServerEntry(screen, serverData, friend) {

        override fun canJoin(): Boolean {
            return statusDescription.address != null
        }
    }

    private fun e4mcServerFriendEntry(screen: FriendsMultiplayerScreen, friend: User): E4mcServerFriendEntry {
        val activity = friend.status!!.activity!!
        val metadata: Status.Activity.E4mcMetadata
        if (activity.hasMetadata(Status.Activity.WorldHostMetadata.ID)) {
            metadata = (activity.metadata!!.attributes as Status.Activity.WorldHostMetadata).asE4mcMetadata()
        } else {
            metadata = activity.metadata!!.attributes as Status.Activity.E4mcMetadata
        }
        return E4mcServerFriendEntry(screen, metadata, ServerInfoUtil.getServerData(friend.name!!, metadata), friend)
    }

    internal open inner class E4mcServerFriendEntry internal constructor(
        screen: FriendsMultiplayerScreen,
        private val statusDescription: Status.Activity.E4mcMetadata,
        serverData: ServerData,
        friend: User
    ) : ServerEntry(screen, serverData, friend) {

        init {
            refreshStatus()
        }

        override fun refreshStatus() {
            super.refreshStatus()
            serverDataField.motd = Component.nullToEmpty(statusDescription.serverInfo!!.levelName)
        }

        override fun isPublished(): Boolean {
            return statusDescription.domain != null
        }
    }

    @Environment(EnvType.CLIENT)
    class LoadingHeader : Entry() {
        private val minecraft: Minecraft = Minecraft.getInstance()

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            val i = contentYMiddle - 9 / 2
            val string: String = LoadingDotsText.get(Util.getMillis())
            guiGraphicsExtractor.text(
                this.minecraft.font, string, guiGraphicsExtractor.guiWidth() / 2 - this.minecraft.font.width(string) / 2,
                i, -8355712
            )
        }

        override fun getNarration(): Component {
            return Component.empty()
        }
    }

    companion object {
        private const val ICON_WIDTH = 32
        private const val ICON_HEIGHT = 32
        private const val SPACING = 5
        private const val STATUS_ICON_WIDTH = 10
        private const val STATUS_ICON_HEIGHT = 8

        private val INCOMPATIBLE_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/incompatible")
        private val UNREACHABLE_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/unreachable")
        private val PING_1_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/ping_1")
        private val PING_2_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/ping_2")
        private val PING_3_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/ping_3")
        private val PING_4_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/ping_4")
        private val PING_5_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/ping_5")
        private val PINGING_1_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/pinging_1")
        private val PINGING_2_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/pinging_2")
        private val PINGING_3_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/pinging_3")
        private val PINGING_4_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/pinging_4")
        private val PINGING_5_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/pinging_5")
        private val JOIN_HIGHLIGHTED_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/join_highlighted")
        private val JOIN_SPRITE: Identifier = Identifier.withDefaultNamespace("server_list/join")
        private val LOGGER: Logger = LogUtils.getLogger()
        private val THREAD_POOL: ThreadPoolExecutor = ScheduledThreadPoolExecutor(
            5,
            ThreadFactoryBuilder()
                .setNameFormat("Friends Server Pinger #%d")
                .setDaemon(true)
                .setUncaughtExceptionHandler(DefaultUncaughtExceptionHandler(LOGGER))
                .build()
        )
        private val CANT_RESOLVE_TEXT: Component = Component.translatable("multiplayer.status.cannot_resolve").withColor(-65536)
        private val CANT_CONNECT_TEXT: Component = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536)
        private val INCOMPATIBLE_STATUS: Component = Component.translatable("multiplayer.status.incompatible")
        private val NO_CONNECTION_STATUS: Component = Component.translatable("multiplayer.status.no_connection")
        private val PINGING_STATUS: Component = Component.translatable("multiplayer.status.pinging")
        private val ONLINE_STATUS: Component = Component.translatable("multiplayer.status.online")
        private val NOT_PUBLISHED_STATUS: Component = Component.translatable("api.worldhost.joinability.not_published")
    }
}
