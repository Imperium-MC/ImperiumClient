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

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.types.ChannelInvite
import io.github.axolotlclient.api.types.ChatMessage
import io.github.axolotlclient.api.types.Persistence
import io.github.axolotlclient.api.util.UUIDHelper
import java.lang.Long.toUnsignedString

object ChannelRequest {

    @JvmStatic
    fun getById(id: String): CompletableFuture<Channel> {
        if (id.isEmpty()) {
            return CompletableFuture.failedFuture(IllegalArgumentException("Empty channel id"))
        }
        return API.getInstance().get(Request.Route.CHANNEL.builder().path(id).build()).thenApply(::parseChannel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseChannel(response: Response): Channel {
        val id = toUnsignedString(response.getBody<Long>("id")!!)
        val name: String = response.getBody("name")!!
        val participantUuids: List<String> = response.getBody("participants")!!
        val owner = UserRequest.get(response.getBody("owner")!!).join().orElseThrow()
        val participants: List<io.github.axolotlclient.api.types.User> = participantUuids.stream()
            .map { uuid: String? -> UserRequest.get(uuid!!) }
            .map { obj -> obj.join() }
            .map { obj -> obj.orElseThrow() }
            .toList()

        val deserialized = ArrayList<ChatMessage>()
        val persistence = Persistence.fromJson(response.getBody("persistence"))
        API.getInstance().get(
            Request.Route.CHANNEL.builder().path(id).path("messages")
                .build()
        )
            .thenAccept { res ->
                val messages = res.body as List<Map<String, Any?>>

                for (o in messages) {
                    deserialized.add(
                        ChatMessage(
                            toUnsignedString(o["id"] as Long), toUnsignedString(o["channel_id"] as Long),
                            UserRequest.get(o["sender"] as String).join().orElseThrow(), o["sender_name"] as String?,
                            o["content"] as String?, Instant.parse(o["timestamp"] as String?)
                        )
                    )
                }
            }.join()
        if (participants.size == 1) {
            val other = if (owner == API.getInstance().self) participants[0] else owner
            if (createDmChannelName(other.uuid) == name) {
                return Channel.DM(id, name, persistence, participants, owner, deserialized)
            }
        }
        return Channel.Group(id, name, persistence, participants, owner, deserialized)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getChannelList(): CompletableFuture<List<Channel>> {
        return API.getInstance().get(Request.Route.CHANNELS.create())
            .thenApply { response ->
                val ids = response.body as List<Long>
                ids.stream().map { id: Long? -> toUnsignedString(id!!) }
                    .map { id: String? -> getById(id!!) }.map { obj -> obj.join() }.toList()
            }
    }

    @JvmStatic
    fun createChannel(name: String, persistence: Persistence, vararg users: String?): CompletableFuture<*> {
        if (name.isEmpty()) {
            return CompletableFuture.failedFuture<Any?>(Throwable("name is empty"))
        }
        val participants = ArrayList<String>()
        for (username in users) {
            participants.add(UUIDHelper.ensureUuid(username!!).join())
        }
        return API.getInstance().post(
            Request.Route.CHANNEL.builder()
                .field("name", name).field("persistence", persistence.toJson())
                .field("participants", participants).build()
        )
        //.thenApply(Response::getPlainBody).thenCompose(ChannelRequest::getById);
    }

    @JvmStatic
    fun updateChannel(id: String, name: String, persistence: Persistence, vararg additionalUsers: String?) {
        API.getInstance().patch(
            Request.Route.CHANNEL.builder().path(id).field("name", name)
                .field("persistence", persistence.toJson()).field("participants", additionalUsers).build()
        )
    }

    @JvmStatic
    fun leaveOrDeleteChannel(channel: Channel): CompletableFuture<*> {
        return API.getInstance().delete(Request.Route.CHANNEL.builder().path(channel.id).build())
    }

    @JvmStatic
    fun getOrCreateDM(user: io.github.axolotlclient.api.types.User): CompletableFuture<Channel> {
        return getChannelList().thenApply { list ->
            list.stream().filter { c: Channel ->
                if (c !is Channel.DM) {
                    return@filter false
                }
                if (c.owner == API.getInstance().self && c.receiver == user) {
                    return@filter true
                }
                c.owner == user && c.participants[0] == API.getInstance().self
            }.findFirst()
        }.thenApply { opt ->
            opt.orElseGet {
                API.getInstance().post(
                    Request.Route.CHANNEL.builder()
                        .field("name", createDmChannelName(user.uuid))
                        .field("persistence", Persistence.of(Persistence.Type.CHANNEL, 0, 0).toJson())
                        .field("participants", listOf(user.uuid))
                        .build()
                )
                    .thenApply { obj: Response -> obj.plainBody }.thenCompose { id: String? -> getById(id!!) }.join()
            }
        }
    }

    private fun createDmChannelName(otherUuid: String): String {
        val self = UUIDHelper.fromUndashed(API.getInstance().self!!.uuid)
        val other = UUIDHelper.fromUndashed(otherUuid)
        val prime = 53

        val leastSig = (self.leastSignificantBits xor other.leastSignificantBits) * prime
        val mostSig = (self.mostSignificantBits xor other.mostSignificantBits) * prime
        return UUIDHelper.toUndashed(UUID(mostSig, leastSig))
    }

    @JvmStatic
    fun removeUserFromChannel(channel: Channel, user: io.github.axolotlclient.api.types.User) {
        API.getInstance().post(
            Request.Route.CHANNEL.builder().path(channel.id).path("remove").query("user", user.uuid).build()
        )
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getChannelInvites(): CompletableFuture<List<ChannelInvite>> {
        return API.getInstance().get(Request.Route.CHANNELS_INVITES.create()).thenApply { res ->
            val invites = res.body as List<Map<String, Any?>>
            invites.map { m: Map<String, Any?> ->
                ChannelInvite(
                    toUnsignedString(m["id"] as Long), m["channel_name"] as String, m["from"] as String
                )
            }
        }
    }

    @JvmStatic
    fun acceptChannelInvite(invite: ChannelInvite): CompletableFuture<*> {
        return API.getInstance().post(
            Request.Route.CHANNELS_INVITES.builder().query("id", invite.channelId).query("accept", true).build()
        )
    }

    @JvmStatic
    fun ignoreChannelInvite(invite: ChannelInvite): CompletableFuture<*> {
        return API.getInstance().post(
            Request.Route.CHANNELS_INVITES.builder().query("id", invite.channelId).query("accept", false).build()
        )
    }
}
