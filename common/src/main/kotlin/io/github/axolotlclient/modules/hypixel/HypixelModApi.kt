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

package io.github.axolotlclient.modules.hypixel

import java.util.Arrays
import java.util.function.Consumer

import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.requests.StatusUpdate
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.network.AxoServerData
import net.hypixel.data.type.ServerType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.handler.ClientboundPacketHandler
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket

class HypixelModApi {
    private var current: ClientboundLocationPacket? = null

    fun init() {
        try {
            Class.forName("net.hypixel.modapi.HypixelModAPI")
        } catch (e: ClassNotFoundException) {
            return
        }

        init0()
    }

    private fun init0() {
        HypixelModAPI.getInstance().createHandler(
            ClientboundLocationPacket::class.java,
            ClientboundPacketHandler { packet -> current = packet }
        )
        Events.CONNECTION_PLAY_READY.register(Consumer { info: AxoServerData? ->
            if (info == null) return@Consumer
            val address = info.`br$getIp`()
            if (address != null && Arrays.stream(StatusUpdate.SupportedServer.values())
                .filter { s -> s.address.matcher(address).matches() }
                .anyMatch { s -> s == StatusUpdate.SupportedServer.HYPIXEL }
            ) {
                HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket::class.java)
            }
        })
    }

    fun getStatus(): Request? {
        val current = this.current ?: return null
        return StatusUpdate.inGame(
            StatusUpdate.SupportedServer.HYPIXEL,
            current.serverType.map { obj: ServerType -> obj.getName() }.orElse(""),
            current.mode.orElse(""),
            current.map.orElse("")
        )
    }
}
