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

import io.github.axolotlclient.api.types.Status
import io.github.axolotlclient.api.util.UUIDHelper
import java.util.Optional
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.status.ServerStatus
import net.minecraft.server.players.NameAndId

object ServerInfoUtil {

    // Passes a nullable value through a NonNull-annotated Java parameter without a
    // runtime null check (the erased cast inserts none): the original Java callers
    // legitimately passed nulls here (favicons of iconless worlds, the null domain
    // of an unpublished e4mc server), and asserting instead would regress those states.
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> forceNonNull(value: Any?): T = value as T

    @JvmStatic
    fun getServerInfo(levelName: String?, status: ServerStatus?): Status.Activity.ServerInfo {
        if (status == null) {
            return Status.Activity.ServerInfo(levelName, null, null, null, null)
        }
        return Status.Activity.ServerInfo(
            levelName, status.description().getString(),
            Status.Activity.ServerInfo.Favicon(
                status.favicon().map { icon: ServerStatus.Favicon -> icon.iconBytes() }.orElse(null)
            ),
            status.players().map { p: ServerStatus.Players ->
                Status.Activity.ServerInfo.Players(
                    p.max(), p.online(),
                    p.sample().stream().map { prof ->
                        Status.Activity.ServerInfo.Players.Player(
                            prof.name(), UUIDHelper.toUndashed(prof.id())
                        )
                    }.toList()
                )
            }.orElse(null),
            status.version().map { v: ServerStatus.Version -> Status.Activity.ServerInfo.Version(v.name(), v.protocol()) }
                .orElse(null)
        )
    }

    @JvmStatic
    fun getServerStatus(info: Status.Activity.ServerInfo): ServerStatus {
        return ServerStatus(
            Component.nullToEmpty(info.description),
            Optional.ofNullable(info.players).map { p: Status.Activity.ServerInfo.Players ->
                ServerStatus.Players(
                    p.max,
                    p.online,
                    p.sample!!.stream().map { prof: Status.Activity.ServerInfo.Players.Player ->
                        NameAndId(UUIDHelper.fromUndashed(prof.uuid!!), forceNonNull(prof.name))
                    }.toList()
                )
            },
            Optional.ofNullable(info.version).map { v: Status.Activity.ServerInfo.Version ->
                ServerStatus.Version(v.name!!, v.protocol)
            },
            Optional.ofNullable(info.icon).map { f: Status.Activity.ServerInfo.Favicon ->
                ServerStatus.Favicon(forceNonNull(f.iconBytes))
            },
            false
        )
    }

    @JvmStatic
    fun getServerData(username: String?, metadata: Status.Activity.E4mcMetadata): ServerData {
        val serverMetadata: ServerStatus? = if (metadata.serverInfo != null) getServerStatus(metadata.serverInfo!!) else null
        val data = ServerData(forceNonNull(username), forceNonNull(metadata.domain), ServerData.Type.OTHER)
        if (serverMetadata != null) {
            serverMetadata.favicon().ifPresent { icon: ServerStatus.Favicon -> data.setIconBytes(icon.iconBytes()) }
            data.motd = Component.literal(metadata.serverInfo!!.levelName!!)
            serverMetadata.version().ifPresentOrElse({ ver: ServerStatus.Version ->
                data.version = Component.literal(ver.name())
                data.protocol = ver.protocol()
            }) {
                data.version = Component.translatable("multiplayer.status.old")
                data.protocol = 0
            }
            serverMetadata.players().ifPresentOrElse({ player: ServerStatus.Players ->
                data.status = ServerStatusPinger.formatPlayerCount(player.online(), player.max())
                data.players = player
                if (player.sample().isNotEmpty()) {
                    val list: MutableList<Component> = ArrayList(player.sample().size)

                    for (gameProfile in player.sample()) {
                        list.add(Component.literal(gameProfile.name()))
                    }

                    if (player.sample().size < player.online()) {
                        list.add(
                            Component.translatable(
                                "multiplayer.status.and_more", player.online() - player.sample().size
                            )
                        )
                    }

                    data.playerList = list
                } else {
                    data.playerList = emptyList()
                }
            }) {
                data.status = Component.translatable("multiplayer.status.unknown").withStyle(ChatFormatting.DARK_GRAY)
            }
        }
        return data
    }
}
