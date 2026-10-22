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

import io.github.axolotlclient.api.e4mc.E4mcStatusProvider
import io.github.axolotlclient.api.requests.StatusUpdate
import io.github.axolotlclient.api.util.StatusUpdateProvider
import io.github.axolotlclient.modules.hypixel.HypixelMods
import io.github.axolotlclient.modules.mcci.MccIslandMods
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen

class StatusUpdateProviderImpl : StatusUpdateProvider {

    override fun initialize() {
    }

    override fun getStatus(): Request? {
        val mc = Minecraft.getInstance()
        val entry = mc.currentServer
        if (entry != null) {
            if (!entry.isLan) {
                val server: StatusUpdate.SupportedServer? = StatusUpdate.SupportedServer.entries
                    .firstOrNull { it.address.matcher(entry.ip).matches() }
                if (server != null) {
                    if (server == StatusUpdate.SupportedServer.HYPIXEL) {
                        return HypixelMods.getInstance().status
                    } else if (server == StatusUpdate.SupportedServer.MCC_ISLAND) {
                        return MccIslandMods.getInstance().mccIStatus
                    }
                }
                return StatusUpdate.inGameServer(entry.name, entry.ip)
            }
            return StatusUpdate.inGameUnknown(entry.name)
        } else if (mc.singleplayerServer != null) {
            val e4mcStatus = E4mcStatusProvider.getStatusDescription()
            if (e4mcStatus != null) {
                return StatusUpdate.e4mcStatusUpdate(e4mcStatus)
            }
            return StatusUpdate.inGameUnknown(mc.singleplayerServer!!.worldData.levelName)
        }
        val current = mc.gui.screen()
        if (current is TitleScreen) {
            return StatusUpdate.online(StatusUpdate.MenuId.MAIN_MENU)
        } else if (current is JoinMultiplayerScreen) {
            return StatusUpdate.online(StatusUpdate.MenuId.SERVER_LIST)
        } else if (current != null) {
            return StatusUpdate.online(StatusUpdate.MenuId.SETTINGS)
        }
        return null
    }
}
