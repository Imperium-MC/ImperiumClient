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
package io.github.axolotlclient.api.requests

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.types.Status
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.util.GsonHelper

object StatusUpdate {

    private fun createStatusUpdate(activity: Status.Activity): Request? {
        val status = API.getInstance().self!!.status!!
        if (status.activity != null) {
            if (status.activity == activity) {
                return null
            }
        }
        status.activity = activity
        val json = GsonHelper.GSON.toJson(activity)
        API.getInstance().logDetailed("Updating status: {}", json)
        return Request.Route.ACCOUNT_ACTIVITY.builder().rawBody(json.toByteArray(StandardCharsets.UTF_8))
            .build()
    }

    private fun createStatusUpdate(title: String, description: String): Request? {
        return createStatusUpdate(Status.Activity(title, description))
    }

    @JvmStatic
    fun online(menuId: MenuId): Request? {
        return createStatusUpdate("api.status.title.online", "api.status.description.menu." + menuId.identifier)
    }

    @JvmStatic
    fun inGame(server: SupportedServer, gameType: String, gameMode: String, map: String): Request? {
        val gm = gameMode.isNotEmpty()
        val mp = map.isNotEmpty()
        val description: String = if (gm && mp) {
            AxoI18n.translate("api.status.description.in_game.game_mode_map", server.serverName, gameType, gameMode, map)
        } else if (gm) {
            AxoI18n.translate("api.status.description.in_game.game_mode_map", server.serverName, gameType, gameMode, map)
        } else if (mp) {
            AxoI18n.translate("api.status.description.in_game.map", server.serverName, gameType, map)
        } else {
            AxoI18n.translate("api.status.description.in_game", server.serverName, gameType)
        }
        val metadata = Status.Activity.ExternalServerMetadata(server.serverName, server.ip)
        return createStatusUpdate(
            Status.Activity(
                "api.status.title.in_game", description,
                if (API.getInstance().apiOptions.allowFriendsServerJoin.get()) Status.Activity.Metadata(metadata) else null
            )
        )
    }

    @JvmStatic
    fun inGameUnknown(description: String): Request? {
        return createStatusUpdate("api.status.title.in_game_unknown", description)
    }

    @JvmStatic
    fun worldHostStatusUpdate(metadata: Status.Activity.WorldHostMetadata): Request? {
        return createStatusUpdate(Status.Activity("api.status.title.world_host", metadata.serverInfo!!.levelName!!, metadata))
    }

    @JvmStatic
    fun e4mcStatusUpdate(metadata: Status.Activity.E4mcMetadata): Request? {
        return createStatusUpdate(Status.Activity("api.status.title.e4mc", metadata.serverInfo!!.levelName!!, metadata))
    }

    @JvmStatic
    fun inGameServer(name: String, ip: String): Request? {
        if (API.getInstance().apiOptions.allowFriendsServerJoin.get()) {
            return createStatusUpdate(
                Status.Activity(
                    "api.status.title.unknown_server", name,
                    Status.Activity.Metadata(Status.Activity.ExternalServerMetadata(name, ip))
                )
            )
        }
        return createStatusUpdate("api.status.title.unknown_server", name)
    }

    enum class MenuId(val identifier: String) {
        IN_MENU("in_menu"),
        MAIN_MENU("main_menu"),
        SERVER_LIST("server_list"),
        SETTINGS("settings")
    }

    enum class SupportedServer(
        // cannot be called `name` (final on enum); the JVM getter keeps the Java call shape getName()
        @get:JvmName("getName") val serverName: String,
        val address: Pattern,
        val ip: String
    ) {
        HYPIXEL("Hypixel", Pattern.compile("^(?:mc\\.)?hypixel\\.net$"), "mc.hypixel.net"),
        MCC_ISLAND("MCC Island", Pattern.compile("^play\\.mccisland\\.net$"), "play.mccisland.net")
    }
}
