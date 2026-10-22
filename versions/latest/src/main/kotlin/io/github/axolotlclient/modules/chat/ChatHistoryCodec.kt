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

package io.github.axolotlclient.modules.chat

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import kotlin.math.max
import kotlin.math.min

/**
 * Pure serializer for the `persistent_chat` module. No IO, no client state — the round-trip
 * is unit-testable.
 */
object ChatHistoryCodec {
    const val CURRENT_VERSION: Int = 1

    /** Hard cap on persisted lines (vanilla keeps at most 100 messages in memory anyway). */
    const val MAX_LINES: Int = 100

    /** Serializes `messages` (newest first); only the newest [MAX_LINES] are kept. */
    @JvmStatic
    fun toJson(messages: List<GuiMessage>): JsonObject {
        val root = JsonObject()
        root.addProperty("v", CURRENT_VERSION)
        val array = JsonArray()
        var kept = 0
        for (message in messages) {
            if (kept >= MAX_LINES) {
                break
            }
            val entry = JsonObject()
            entry.addProperty("t", message.addedTime())
            entry.addProperty("s", message.source().name)
            val encoded = encodeComponent(message.content()) ?: continue // guard anyway
            entry.add("c", encoded)
            array.add(entry)
            kept++
        }
        root.add("messages", array)
        return root
    }

    /**
     * Parses a document produced by [toJson]. Damaged entries are skipped, never thrown.
     */
    @JvmStatic
    fun fromJson(root: JsonObject?, currentGuiTicks: Int): List<GuiMessage> {
        val result: MutableList<GuiMessage> = ArrayList()
        if (root == null || !root.has("messages") || !root.get("messages").isJsonArray) {
            return result
        }
        val floor = max(0, currentGuiTicks - 20)
        for (element in root.getAsJsonArray("messages")) {
            if (!element.isJsonObject || result.size >= MAX_LINES) {
                break
            }
            val entry = element.asJsonObject
            if (!entry.has("c")) {
                continue
            }
            val content = decodeComponent(entry.get("c")) ?: continue
            val addedTime = if (entry.has("t") && entry.get("t").isJsonPrimitive) entry.get("t").asInt else 0
            result.add(GuiMessage(min(max(addedTime, 0), floor), content, null, parseSource(entry), null))
        }
        return result
    }

    private fun parseSource(entry: JsonObject): GuiMessageSource {
        if (entry.has("s") && entry.get("s").isJsonPrimitive) {
            try {
                return GuiMessageSource.valueOf(entry.get("s").asString)
            } catch (_: IllegalArgumentException) {
                // unknown source name from a future version
            }
        }
        return GuiMessageSource.SYSTEM_CLIENT
    }

    private fun encodeComponent(component: Component): JsonElement? {
        var encoded: JsonElement? =
            ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).result().orElse(null)
        if (encoded == null) {
            // Fallback: keep the visible text (styling lost) instead of dropping the line.
            encoded = JsonObject()
            (encoded as JsonObject).addProperty("text", component.string)
        }
        return encoded
    }

    private fun decodeComponent(element: JsonElement): Component? =
        ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null)
}
