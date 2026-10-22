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
package io.github.axolotlclient.api.handlers

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.ChannelInvite
import io.github.axolotlclient.api.util.SocketMessageHandler
import io.github.axolotlclient.api.util.UUIDHelper
import java.lang.Long.toUnsignedString
import java.util.function.Function

class ChannelInviteHandler : SocketMessageHandler {
    override fun isApplicable(target: String): Boolean {
        return "channel_invite" == target || "channel_invite_reaction" == target
    }

    override fun handle(response: Response) {
        val id: String = response.getBody<Any?, String>("channel", Function { d -> toUnsignedString(d as Long) })!!
        val target: String = response.getBody("target")!!
        val channelName: String? = response.getBody("channel_name")
        when (target) {
            "channel_invite" -> {
                val from: String = response.getBody("from")!!
                if (API.getInstance().apiOptions.channelInvitesEnabled.get()) {
                    notification(
                        "api.channels.invite", "api.channels.invite.desc",
                        UUIDHelper.tryGetUsernameAsync(from).join(), channelName
                    )
                } else {
                    ChannelRequest.ignoreChannelInvite(ChannelInvite(id, channelName!!, from))
                }
            }
            "channel_invite_reaction" -> {
                val player: String = response.getBody("player")!!
                val accepted: Boolean = response.getBody("accepted")!!
                if (accepted) {
                    notification(
                        "api.channels.invite.accepted", "api.channels.invite.accepted.desc",
                        UUIDHelper.tryGetUsernameAsync(player).join(), channelName
                    )
                } else {
                    notification(
                        "api.channels.invite.denied", "api.channels.invite.denied.desc",
                        UUIDHelper.tryGetUsernameAsync(player).join(), channelName
                    )
                }
            }
        }
    }
}
