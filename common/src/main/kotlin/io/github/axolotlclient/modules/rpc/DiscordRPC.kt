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

package io.github.axolotlclient.modules.rpc

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.JsonObject
import com.jagrosh.discordipc.IPCClient
import com.jagrosh.discordipc.IPCListener
import com.jagrosh.discordipc.entities.ActivityType
import com.jagrosh.discordipc.entities.Packet
import com.jagrosh.discordipc.entities.RichPresence
import com.jagrosh.discordipc.entities.StatusDisplayType
import com.jagrosh.discordipc.entities.User
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.OSUtil
import io.github.axolotlclient.util.ThreadExecuter
import io.github.axolotlclient.util.options.ForceableBooleanOption

class DiscordRPC : AbstractCommonModule() {
    private val category = OptionCategory.create("rpc")
    private val showActivity = BooleanOption("showActivity", true)
    private val enabled = ForceableBooleanOption("enabled", false) { value ->
        if (!value) {
            ThreadExecuter.scheduleTask(Runnable { this.shutdown() })
        }
    }
    private val showServerNameMode = StringArrayOption(
        "showServerNameMode",
        arrayOf("showIp", "showName", "off"), "off"
    )
    private val showTime = BooleanOption("showTime", true)

    private val time = Instant.now()
    private val logger = AxolotlClientCommon.getInstance().logger
    private val state = AtomicReference(State.NOT_CONNECTED)
    private var ipcClient: IPCClient? = null
    private var currentWorld = ""

    fun setWorld(world: String) {
        currentWorld = world
    }

    override fun init() {
        category.add(enabled, showTime, showActivity, showServerNameMode)
        if (OSUtil.getOS() == OSUtil.OperatingSystem.OTHER) {
            enabled.setForceOff(true, "crash")
        }
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
        Events.CLIENT_STOP.register(Runnable { this.shutdown() })
    }

    override fun tick() {
        if (enabled.get() && state.compareAndSet(State.NOT_CONNECTED, State.STARTING)) {
            ThreadExecuter.scheduleTask(Runnable { this.initRPC() })
        } else if (state.get() == State.CONNECTED) {
            ThreadExecuter.scheduleTask(Runnable { this.updateRichPresence() })
        }
    }

    private fun shutdown() {
        if (state.get() == State.CONNECTED) {
            setRichPresence(null)
            ipcClient!!.close()
        }
        state.set(State.NOT_CONNECTED)
    }

    private fun createRichPresence(state: String, details: String): RichPresence {
        val builder = RichPresence.Builder()
        builder.setLargeImageWithTooltip(
            "icon",
            "AxolotlClient " + AxolotlClientCommon.VERSION + " | Minecraft " + AxolotlClientCommon.GAME_VERSION
        )
        if (showTime.get()) {
            builder.setStartTimestamp(time.epochSecond)
        }
        builder.setState(state).setDetails(details)
        builder.setStatusDisplayType(StatusDisplayType.Name)
        builder.setActivityType(ActivityType.Playing)
        return builder.build()
    }

    private fun updateRichPresence() {
        val state = when (showServerNameMode.get()) {
            "showIp" -> if (client.`br$getWorld`() == null) "In the menu"
            else (CommonUtil.getCurrentServerAddress() ?: "Singleplayer")

            "showName" -> if (client.`br$getWorld`() == null) "In the menu"
            else if (client.`br$getServerAddress`() == null)
                (CommonUtil.getCurrentServerAddress() ?: "Singleplayer")
            else (client.`br$getServerName`() ?: "")

            else -> ""
        }

        val details: String
        if (showActivity.get() && !client.`br$isLocalServer`()) {
            details = CommonUtil.getGame()
        } else if (showActivity.get() && currentWorld.isNotEmpty()) {
            details = currentWorld
            currentWorld = ""
        } else {
            details = ""
        }

        setRichPresence(createRichPresence(state, details))
    }

    private fun setRichPresence(presence: RichPresence?) {
        if (ipcClient != null && state.get() == State.CONNECTED) {
            ipcClient!!.sendRichPresence(presence)
        }
    }

    @Synchronized
    private fun initRPC() {
        if (enabled.get() && state.get() == State.STARTING) {
            if (ipcClient == null) {
                ipcClient = IPCClient(CLIENT_ID)
                ipcClient!!.setListener(object : IPCListener {
                    override fun onPacketSent(client: IPCClient, packet: Packet) {
                    }

                    override fun onPacketReceived(client: IPCClient, packet: Packet) {
                    }

                    override fun onActivityJoin(client: IPCClient, secret: String) {
                    }

                    override fun onActivitySpectate(client: IPCClient, secret: String) {
                    }

                    override fun onActivityJoinRequest(client: IPCClient, secret: String, user: User) {
                    }

                    override fun onReady(client: IPCClient) {
                        state.set(State.CONNECTED)
                        updateRichPresence()
                    }

                    override fun onClose(client: IPCClient, json: JsonObject) {
                        logger.info("RPC Closed")
                        state.set(State.NOT_CONNECTED)
                    }

                    override fun onDisconnect(client: IPCClient, t: Throwable) {
                        state.set(State.NOT_CONNECTED)
                    }
                })
            }
            try {
                ipcClient!!.connect()
                logger.info("Started RPC")
            } catch (e: Exception) {
                logger.warn("Failed to start RPC", e)
                AxolotlClientCommon.getInstance().notificationProvider.addStatus("rpc.starting.error", e.message ?: "")
                try {
                    ipcClient!!.close()
                } catch (_: Throwable) {
                }
                state.set(State.ERRORED)
            }
        }
    }

    private enum class State {
        NOT_CONNECTED,
        STARTING,
        CONNECTED,
        ERRORED
    }

    companion object {
        private const val CLIENT_ID = 875835666729152573L
        private val Instance = DiscordRPC()

        @JvmStatic
        fun getInstance(): DiscordRPC = Instance
    }
}
