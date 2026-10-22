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

package io.github.axolotlclient.api.util

/**
 * Replaces the lombok @Data variant: mutable left/right pair with the same
 * getLeft/getRight/set accessors and equals/hashCode/toString semantics.
 * The components stay nullable because [empty] and [of] accept nulls.
 */
class BiContainer<A : Any, B : Any> private constructor(var left: A?, var right: B?) {

    fun set(left: A?, right: B?) {
        this.left = left
        this.right = right
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BiContainer<*, *>) return false
        return left == other.left && right == other.right
    }

    override fun hashCode(): Int {
        var result = left?.hashCode() ?: 0
        result = 31 * result + (right?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "BiContainer(left=$left, right=$right)"

    companion object {
        @JvmStatic
        fun <A : Any, B : Any> empty(): BiContainer<A, B> {
            return of(null, null)
        }

        @JvmStatic
        fun <A : Any, B : Any> of(left: A?, right: B?): BiContainer<A, B> {
            return BiContainer(left, right)
        }
    }
}
