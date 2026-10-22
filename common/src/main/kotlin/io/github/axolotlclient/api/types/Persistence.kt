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

abstract class Persistence {
    abstract fun type(): Type

    open fun count(): Int {
        return 0
    }

    open fun duration(): Long {
        return 0
    }

    open fun toJson(): MutableMap<String, Any> {
        return mutableMapOf("type" to type().id)
    }

    class Channel : Persistence() {
        override fun type(): Type {
            return Type.CHANNEL
        }
    }

    private class Duration(private val durationValue: Long) : Persistence() {
        override fun type(): Type {
            return Type.DURATION
        }

        override fun duration(): Long {
            return durationValue
        }

        override fun toJson(): MutableMap<String, Any> {
            val map = super.toJson()
            map["duration"] = durationValue
            return map
        }
    }

    private class Count(private val countValue: Int) : Persistence() {
        override fun type(): Type {
            return Type.COUNT
        }

        override fun count(): Int {
            return countValue
        }

        override fun toJson(): MutableMap<String, Any> {
            val map = super.toJson()
            map["count"] = countValue
            return map
        }
    }

    private class CountDuration(private val countValue: Int, private val durationValue: Long) : Persistence() {
        override fun type(): Type {
            return Type.COUNT_DURATION
        }

        override fun count(): Int {
            return countValue
        }

        override fun duration(): Long {
            return durationValue
        }

        override fun toJson(): MutableMap<String, Any> {
            val map = super.toJson()
            map["count"] = countValue
            map["duration"] = durationValue
            return map
        }
    }

    enum class Type(val id: String) {
        CHANNEL("channel"),
        COUNT("count"),
        DURATION("duration"),
        COUNT_DURATION("count_duration")
    }

    companion object {
        @JvmStatic
        fun of(type: Type, count: Int, duration: Long): Persistence {
            return when (type) {
                Type.CHANNEL -> Channel()
                Type.COUNT -> Count(count)
                Type.DURATION -> Duration(duration)
                Type.COUNT_DURATION -> CountDuration(count, duration)
            }
        }

        @JvmStatic
        fun fromJson(json: Any?): Persistence {
            val map = json as Map<*, *>
            return when (map["type"] as String?) {
                "channel" -> Channel()
                "count" -> Count(map["count"] as Int)
                "duration" -> Duration(map["duration"] as Long)
                "count_duration" -> CountDuration(map["count"] as Int, map["duration"] as Long)
                else -> throw IllegalArgumentException(json.toString())
            }
        }
    }
}
