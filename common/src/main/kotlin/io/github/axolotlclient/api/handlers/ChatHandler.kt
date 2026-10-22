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

import java.time.Instant
import java.util.ArrayList
import java.util.function.Consumer

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.api.requests.UserRequest
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.types.ChatMessage
import io.github.axolotlclient.api.util.SocketMessageHandler
import io.github.axolotlclient.bridge.util.AxoI18n
import java.lang.Long.toUnsignedString
import java.util.function.Function

object ChatHandler : SocketMessageHandler {

    @JvmField
    val DEFAULT_MESSAGE_CONSUMER: Consumer<ChatMessage> = Consumer {}

    @JvmField
    val DEFAULT_MESSAGES_CONSUMER: Consumer<List<ChatMessage>> = Consumer {}

    @JvmField
    val DEFAULT: NotificationsEnabler = NotificationsEnabler { true }

    var messageConsumer: Consumer<ChatMessage> = DEFAULT_MESSAGE_CONSUMER
    var messagesConsumer: Consumer<List<ChatMessage>> = DEFAULT_MESSAGES_CONSUMER
    var enableNotifications: NotificationsEnabler = DEFAULT

    @JvmStatic
    fun getInstance(): ChatHandler {
        return ChatHandler
    }

    override fun isApplicable(target: String): Boolean {
        return "chat_message" == target
    }

    override fun handle(response: Response) {
        val time = Instant.now()
        val channelId: String = response.getBody<Any?, String>(
            "channel", Function<Any?, String> { d -> toUnsignedString(d as Long) }
        )!!
        val id: String = response.getBody<Any?, String>(
            "id", Function<Any?, String> { d -> toUnsignedString(d as Long) }
        )!!
        val sender: String = response.getBody("sender")!!
        val senderName: String? = response.getBody("sender_name")
        val content: String? = response.getBody("content")
        val message = ChatMessage(id, channelId, UserRequest.get(sender).join().orElseThrow(), senderName, content, time)
        if (enableNotifications.showNotification(message)) {
            notification(
                AxoI18n.translate("api.chat.newMessageFrom", message.sender.name),
                message.content!!
            )
        }
        messageConsumer.accept(message)
    }

    fun sendMessage(channel: Channel, message: String?) {
        if (message == null || message.isBlank()) {
            return
        }
        val displayName = API.getInstance().self!!.getDisplayName(message)

        API.getInstance().post(
            Request.Route.CHANNEL.builder().path(channel.id).field("content", message)
                .field("display_name", displayName).build()
        )
            .whenComplete { res, th ->
                val msg = ChatMessage(
                    res.plainBody!!, channel.id, API.getInstance().self!!, displayName, message, Instant.now()
                )
                messageConsumer.accept(msg)
                channel.messages.add(msg)
            }
    }

    @Suppress("UNCHECKED_CAST")
    fun getMessagesBefore(channel: Channel, getBefore: Long) {
        API.getInstance().get(
            Request.Route.CHANNEL.builder().path(channel.id).path("messages")
                .query("before", Instant.ofEpochSecond(getBefore).toString()).build()
        )
            .thenAccept { res ->
                val messages = res.body as List<Map<String, Any?>>

                val deserialized = ArrayList<ChatMessage>()

                for (o in messages) {
                    deserialized.add(
                        ChatMessage(
                            toUnsignedString(o["id"] as Long),
                            toUnsignedString(o["channel_id"] as Long),
                            UserRequest.get(o["sender"] as String).join().orElseThrow(), o["sender_name"] as String?,
                            o["content"] as String?, Instant.parse(o["timestamp"] as String?)
                        )
                    )
                }

                messagesConsumer.accept(deserialized)
            }
    }

    fun reportMessage(message: ChatMessage) {
        API.getInstance().post(Request.Route.REPORT.builder().path(message.id).build())
    }

    fun interface NotificationsEnabler {
        fun showNotification(message: ChatMessage): Boolean
    }
}
