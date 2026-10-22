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

package io.github.axolotlclient.util

import com.google.gson.JsonElement

object JsonBuilders {
    class JsonArray private constructor() {
        private val arr = com.google.gson.JsonArray()

        fun field(ele: JsonElement): JsonArray {
            arr.add(ele)
            return this
        }

        fun field(builder: JsonArray): JsonArray = field(builder.build())

        fun field(builder: JsonObject): JsonArray = field(builder.build())

        fun field(prop: Number): JsonArray {
            arr.add(prop)
            return this
        }

        fun field(prop: String): JsonArray {
            arr.add(prop)
            return this
        }

        fun field(prop: Boolean): JsonArray {
            arr.add(prop)
            return this
        }

        fun field(prop: Char): JsonArray {
            arr.add(prop)
            return this
        }

        fun build(): com.google.gson.JsonArray = arr

        fun asString(): String = arr.toString()

        companion object {
            @JvmStatic
            fun create(): JsonArray = JsonArray()
        }
    }

    class JsonObject private constructor() {
        private val obj = com.google.gson.JsonObject()

        fun field(name: String, ele: JsonElement): JsonObject {
            obj.add(name, ele)
            return this
        }

        fun field(name: String, builder: JsonObject): JsonObject = field(name, builder.build())

        fun field(name: String, builder: JsonArray): JsonObject = field(name, builder.build())

        fun field(name: String, prop: Number): JsonObject {
            obj.addProperty(name, prop)
            return this
        }

        fun field(name: String, prop: String): JsonObject {
            obj.addProperty(name, prop)
            return this
        }

        fun field(name: String, prop: Boolean): JsonObject {
            obj.addProperty(name, prop)
            return this
        }

        fun field(name: String, prop: Char): JsonObject {
            obj.addProperty(name, prop)
            return this
        }

        fun build(): com.google.gson.JsonObject = obj

        fun asString(): String = obj.toString()

        companion object {
            @JvmStatic
            fun create(): JsonObject = JsonObject()
        }
    }
}
