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

import java.util.ArrayList
import java.util.Optional
import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.api.types.Relation
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.api.util.BiContainer
import io.github.axolotlclient.api.util.UUIDHelper

class FriendRequest protected constructor() {
    private val api: API = API.getInstance()

    fun setRelation(uuid: String, relation: Relation): CompletableFuture<Response> {
        return api.post(Request.Route.USER.builder().path(uuid).query("relation", relation.id).build())
    }

    fun addFriend(uuid: String): CompletableFuture<*> {
        return setRelation(uuid, Relation.REQUEST).thenAcceptBoth(UUIDHelper.tryGetUsernameAsync(uuid)) { response, name ->
            if (!response.isError) {
                api.notificationProvider
                    .addStatus("api.success.request_sent", "api.success.request_sent.desc", name)
            } else if (response.error!!.httpCode == 404) {
                api.notificationProvider.addStatus("api.failure.request_sent", "api.failure.request_sent.not_found", name)
            } else if (response.error!!.httpCode == 403) {
                api.notificationProvider.addStatus("api.failure.request_sent", "api.failure.request_sent.forbidden", name)
            }
        }
    }

    fun removeFriend(user: User): CompletableFuture<*> {
        return setRelation(API.sanitizeUUID(user.uuid), Relation.NONE).whenCompleteAsync { response, t ->
            if (!response.isError) {
                api.notificationProvider.addStatus("api.success.removeFriend", "api.success.removeFriend.desc", user.name)
                user.relation = Relation.NONE
            }
        }
    }

    fun blockUser(user: User) {
        setRelation(user.uuid, Relation.BLOCKED).whenCompleteAsync { response, t ->
            if (!response.isError) {
                api.notificationProvider.addStatus("api.success.blockUser", "api.success.blockUser.desc", user.name)
                user.relation = Relation.BLOCKED
            }
        }
    }

    fun unblockUser(user: User): CompletableFuture<*> {
        return setRelation(user.uuid, Relation.NONE).whenCompleteAsync { response, t ->
            if (!response.isError) {
                api.notificationProvider.addStatus("api.success.unblockUser", "api.success.unblockUser.desc", user.name)
                user.relation = Relation.NONE
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getFriendUuids(): CompletableFuture<List<String>> {
        return api.get(Request.Route.ACCOUNT_RELATIONS_FRIENDS.builder().build())
            .thenApply { r ->
                if (!r.isError) {
                    r.body as List<String>
                } else {
                    ArrayList()
                }
            }
    }

    fun getFriends(): CompletableFuture<List<User>> {
        return getFriendUuids()
            .thenApply { r ->
                r.stream().map { uuid: String? -> UserRequest.get(uuid!!) }
                    .map { obj -> obj.join() }.map { obj -> obj.orElseThrow() }.toList()
            }
    }

    fun getOnlineFriendCount(): CompletableFuture<Long> {
        return getFriends().thenApply { list -> list.stream().filter { u -> u.status!!.isOnline() }.count() }
    }

    fun getFriendRequests(): CompletableFuture<BiContainer<List<User>, List<User>>> {
        return api.get(Request.Route.ACCOUNT_RELATIONS_REQUESTS.create())
            .thenApply { res ->
                val incomingUuids: List<String> = res.getBody("in")!!
                val outgoingUuids: List<String> = res.getBody("out")!!
                val incoming = incomingUuids.stream().map { uuid: String? -> UserRequest.get(uuid!!) }
                    .map { obj -> obj.join() }.map { obj -> obj.orElseThrow() }.toList()
                val outgoing = outgoingUuids.stream().map { uuid: String? -> UserRequest.get(uuid!!) }
                    .map { obj -> obj.join() }.map { obj -> obj.orElseThrow() }.toList()
                BiContainer.of(incoming, outgoing)
            }
    }

    @Suppress("UNCHECKED_CAST")
    fun getBlocked(): CompletableFuture<List<User>> {
        return api.get(Request.Route.ACCOUNT_RELATIONS_BLOCKED.create())
            .thenApply { res ->
                val uuids = res.body as List<String>
                uuids.stream().map { uuid: String? -> UserRequest.get(uuid!!) }
                    .map { obj -> obj.join() }.map { obj -> obj.orElseThrow() }.toList()
            }
    }

    fun acceptFriendRequest(from: User): CompletableFuture<*> {
        return setRelation(from.uuid, Relation.FRIEND)
            .thenAccept { res ->
                if (!res.isError) {
                    api.notificationProvider.addStatus("api.success.acceptFriend", "api.success.acceptFriend.desc", from.name)
                    from.relation = Relation.FRIEND
                }
            }
    }

    fun denyFriendRequest(from: User): CompletableFuture<*> {
        return setRelation(from.uuid, Relation.NONE).thenAccept { res ->
            if (!res.isError) {
                api.notificationProvider.addStatus("api.success.denyFriend", "api.success.denyFriend.desc", from.name)
                from.relation = Relation.NONE
            }
        }
    }

    fun cancelFriendRequest(to: User): CompletableFuture<*> {
        return setRelation(to.uuid, Relation.NONE).thenAccept { res ->
            if (!res.isError) {
                api.notificationProvider.addStatus("api.friends", "api.friends.request.cancelled", to.name)
                to.relation = Relation.NONE
            }
        }
    }

    companion object {
        private val Instance = FriendRequest()

        @JvmStatic
        fun getInstance(): FriendRequest {
            return Instance
        }
    }
}
