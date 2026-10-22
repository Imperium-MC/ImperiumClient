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

import java.util.HashMap
import java.util.function.Consumer

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.api.requests.UserRequest
import io.github.axolotlclient.api.types.Status
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.api.util.SocketMessageHandler
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.util.GsonHelper

class StatusUpdateHandler : SocketMessageHandler {

    override fun isApplicable(target: String): Boolean {
        return "activity_update" == target && API.getInstance().apiOptions.statusUpdateNotifs.get()
    }

    @JvmRecord
    private data class StatusUpdateMessage(val user: String, val activity: Status.Activity)

    override fun handle(response: Response) {
        val status = GsonHelper.GSON.fromJson(response.plainBody, StatusUpdateMessage::class.java)

        val activity = status.activity
        notification(
            "api.friends.activity.update",
            AxoI18n.translate(activity.title) + ": " + AxoI18n.translate(activity.description),
            UUIDHelper.tryGetUsernameAsync(status.user).join()
        )
        UserRequest.get(status.user).thenAccept { u ->
            val user = u.orElseThrow()
            user.status!!.setOnline(true)
            user.status!!.activity = activity
            updateListeners.values.forEach { c -> c.accept(user) }
        }
    }

    companion object {
        private val updateListeners: MutableMap<String, Consumer<User>> = HashMap()

        @JvmStatic
        fun addUpdateListener(id: String, listener: Consumer<User>) {
            updateListeners[id] = listener
        }

        @JvmStatic
        fun removeUpdateListener(id: String) {
            updateListeners.remove(id)
        }
    }
}
