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

package io.github.axolotlclient.util

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import io.github.axolotlclient.api.util.InstantTypeAdapter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.time.Instant
import java.util.stream.Stream

object GsonHelper {

    @JvmField
    val GSON: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    @JvmStatic
    fun fromJson(s: String?): JsonObject =
        if (s.isNullOrEmpty()) JsonObject() else GSON.fromJson(s, JsonObject::class.java)

    @JvmStatic
    @Throws(IOException::class)
    fun read(input: InputStream): Any? = JsonReader(InputStreamReader(input)).use { reader -> read(reader) }

    @JvmStatic
    @Throws(IOException::class)
    fun read(s: String): Any? = JsonReader(StringReader(s)).use { reader -> read(reader) }

    @JvmStatic
    @Throws(IOException::class)
    fun read(reader: JsonReader): Any? =
        when (reader.peek()) {
            com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                val list = ArrayList<Any?>()

                reader.beginArray()

                while (reader.hasNext()) {
                    list.add(read(reader))
                }

                reader.endArray()
                list
            }
            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                val map = LinkedHashMap<String, Any?>()

                reader.beginObject()

                while (reader.hasNext()) {
                    val key = reader.nextName()
                    map[key] = read(reader)
                }

                reader.endObject()
                map
            }
            com.google.gson.stream.JsonToken.STRING -> reader.nextString()
            com.google.gson.stream.JsonToken.NUMBER -> {
                // Ugh.
                val num = reader.nextString()
                num.toLongOrNull() ?: num.toDouble()
            }
            com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean()
            com.google.gson.stream.JsonToken.NULL -> null
            // Unused, probably a sign of malformed json
            else -> throw IllegalStateException()
        }

    @JvmStatic
    fun jsonArrayToStream(array: JsonArray): Stream<JsonElement> {
        val elements = ArrayList<JsonElement>(array.size())
        array.forEach { elements.add(it) }
        return elements.stream()
    }
}
