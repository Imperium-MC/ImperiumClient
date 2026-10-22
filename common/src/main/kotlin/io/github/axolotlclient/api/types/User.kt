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

import java.time.Instant

import io.github.axolotlclient.api.API

class User(
    uuid: String,
    name: String?,
    relation: Relation?,
    registered: Instant?,
    status: Status?,
    previousUsernames: List<OldUsername>?
) {
    var uuid: String = API.sanitizeUUID(uuid)
    var name: String? = name
    var relation: Relation? = relation
    var registered: Instant? = registered
    var status: Status? = status
    var previousUsernames: List<OldUsername>? = previousUsernames
    var system: PkSystem? = null

    fun isSystem(): Boolean {
        return system != null
    }

    override fun hashCode(): Int {
        var result = 1
        val name = this.name
        result = result * 59 + (name?.hashCode() ?: 43)
        val uuid = this.uuid
        result = result * 59 + (uuid?.hashCode() ?: 43)
        val status = this.status
        result = result * 59 + (status?.hashCode() ?: 43)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other is User) {
            return uuid == other.uuid
        }
        return false
    }

    override fun toString(): String {
        return "User(uuid=$uuid, name=$name, relation=$relation, registered=$registered, status=$status, previousUsernames=$previousUsernames, system=$system)"
    }

    fun getDisplayName(message: String?): String? {
        if (!isSystem()) {
            return name
        }
        return system!!.getProxy(message!!).orElse(name)
    }

    data class OldUsername(var name: String?, var isPub: Boolean)
}
