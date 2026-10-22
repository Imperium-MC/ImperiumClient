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

package io.github.axolotlclient.modules.hypixel

import java.io.IOException
import java.util.Collections
import java.util.HashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent
import io.github.axolotlclient.util.GsonHelper

class HypixelMessages : Runnable {
    @JvmField
    val languageMessageMap: MutableMap<String, MutableMap<String, Pattern>> = HashMap()
    private val messageLanguageMap: MutableMap<String, MutableMap<String, Pattern>> = HashMap()

    @Suppress("UNCHECKED_CAST")
    fun load() {
        languageMessageMap.clear()
        messageLanguageMap.clear()

        AxolotlClientCommon.getInstance().getLogger().debug("Loading Hypixel Messages")
        AxoMinecraftClient.getInstance().`br$getResourceManager`().`br$listResources`(
            "", "lang"
        ) { id -> id.`br$getPath`().endsWith(".hypixel.json") }
            .forEach { id, resource ->
                val i = id.`br$getPath`().lastIndexOf("/") + 1
                val lang = id.`br$getPath`().substring(i, i + 5)
                AxolotlClientCommon.getInstance().getLogger().debug("Found message file: $id")
                val lines: Map<String, String> = try {
                    @Suppress("UNCHECKED_CAST")
                    GsonHelper.read(resource.`br$asStream`()) as Map<String, String>
                } catch (e: IOException) {
                    return@forEach
                }
                languageMessageMap.computeIfAbsent(lang) { HashMap() }
                val map = languageMessageMap[lang]!!
                lines.forEach { (key, value) ->
                    val pattern = Pattern.compile(value)
                    map.putIfAbsent(key, pattern)
                    messageLanguageMap.computeIfAbsent(key) { HashMap() }[lang] = pattern
                }
            }
    }

    fun process(option: BooleanOption, messageKey: String, event: ReceiveChatMessageEvent) {
        if (option.get() && matchesAnyLanguage(messageKey, event.originalMessage)) {
            event.isCancelled = true
        }
    }

    fun matchesAnyLanguage(key: String, message: String): Boolean =
        messageLanguageMap.getOrDefault(key, Collections.emptyMap()).values.stream()
            .map { pattern -> pattern.matcher(message) }
            .anyMatch { obj -> obj.matches() }

    fun matchesAnyMessage(lang: String, message: String): Boolean =
        languageMessageMap.getOrDefault(lang, Collections.emptyMap())
            .values.stream().map { pattern -> pattern.matcher(message) }.anyMatch { obj -> obj.matches() }

    fun matchesAny(message: String): Boolean =
        languageMessageMap.values.stream().map { obj: Map<String, Pattern> -> obj.values }
            .anyMatch { patterns -> patterns.stream().map { pattern -> pattern.matcher(message) }.anyMatch { obj -> obj.matches() } }

    override fun run() {
        load()
    }

    companion object {
        private val Instance = HypixelMessages()

        @JvmStatic
        fun getInstance(): HypixelMessages = Instance
    }
}
