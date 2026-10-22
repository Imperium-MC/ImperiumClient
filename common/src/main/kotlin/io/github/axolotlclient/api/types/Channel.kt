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
package io.github.axolotlclient.api.types

import io.github.axolotlclient.api.API

abstract class Channel protected constructor(
    val id: String,
    channelName: String?,
    val persistence: Persistence?,
    val participants: List<User>,
    val owner: User,
    val messages: MutableList<ChatMessage>
) : Comparable<Channel> {
    private val rawName: String? = channelName

    open val name: String?
        get() = rawName

    val realName: String?
        get() = rawName

    fun getAllUsers(): MutableList<User> {
        val list = ArrayList(participants)
        list.add(0, owner)
        return list
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Channel) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    abstract fun isDM(): Boolean

    override fun compareTo(other: Channel): Int {
        if (id == other.id) {
            return 0
        }
        val o1LatestMessage = messages.maxOfOrNull { it.timestamp }
        val o2LatestMessage = other.messages.maxOfOrNull { it.timestamp }
        if (o1LatestMessage != null && o2LatestMessage != null) {
            return o1LatestMessage.compareTo(o2LatestMessage)
        }
        return if (o1LatestMessage != null) 1 else if (o2LatestMessage != null) -1 else 0
    }

    class Group(
        id: String,
        name: String?,
        persistence: Persistence?,
        participants: List<User>,
        owner: User,
        messages: MutableList<ChatMessage>
    ) : Channel(id, name, persistence, participants, owner, messages) {
        override fun isDM(): Boolean {
            return false
        }
    }

    class DM(
        id: String,
        name: String?,
        persistence: Persistence?,
        participants: List<User>,
        owner: User,
        messages: MutableList<ChatMessage>
    ) : Channel(id, name, persistence, participants, owner, messages) {
        val receiver: User = if (API.getInstance().self == owner) participants[0] else owner

        override fun isDM(): Boolean {
            return true
        }

        override val name: String?
            get() = receiver.name
    }
}
