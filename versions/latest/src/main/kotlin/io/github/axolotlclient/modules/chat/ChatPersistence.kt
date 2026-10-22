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

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.Util
import io.github.axolotlclient.util.duck.ChatHudExtension
import io.github.axolotlclient.util.options.GenericOption
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component

/**
 * Persistent chat (the `persistent_chat` chat-settings row): the visible history is
 * written to a sidecar JSON file when a play session ends, and restored when the next one
 * starts, so the chat survives relogs.
 */
class ChatPersistence : AbstractModule() {
    private fun liveChat() = client.gui.hud.getChat()

    override fun init() {
        AxolotlClient.config().clearChatHistoryKey.`br$registerOnConsumeClick`(Runnable { this.clear() })
        AxolotlClient.config().chatSettings.add(GenericOption("persistent_chat", "clear_history_button") { this.clear() })

        Events.DISCONNECT.register(Runnable { this.save() })
        Events.CLIENT_STOP.register(Runnable { this.save() })
        Events.CONNECTION_PLAY_READY.register(Consumer { restore() })
    }

    /** Writes the current visible history to the sidecar (no-op for an empty history). */
    fun save() {
        if (!AxolotlClient.config().persistentChat.get() || client.gui == null || client.gui.hud == null) {
            return
        }
        val messages = (liveChat() as ChatHudExtension).`axolotlclient$getMessages`()
        if (messages.isEmpty()) {
            // Never overwrite a previous session's file with nothing.
            return
        }
        try {
            Files.createDirectories(STORE_DIR)
            Files.writeString(storeFile(), ChatHistoryCodec.toJson(messages).toString(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to persist chat history", e)
        }
    }

    /**
     * Restores the persisted history into the live chat, but only when the live history is
     * empty (vanilla's own state restore already ran otherwise). Runs on the join event.
     */
    fun restore() {
        if (!AxolotlClient.config().persistentChat.get() || client.gui == null || client.gui.hud == null) {
            return
        }
        val chat = liveChat()
        if ((chat as ChatHudExtension).`axolotlclient$getMessages`().isNotEmpty()) {
            return
        }
        val restored = load()
        if (restored.isEmpty()) {
            return
        }
        (chat as ChatHudExtension).`axolotlclient$restoreMessages`(restored)
    }

    /** Reads the sidecar; an empty list on any missing/damaged file. */
    fun load(): List<net.minecraft.client.multiplayer.chat.GuiMessage> {
        val parsed = readFile() ?: return listOf()
        return ChatHistoryCodec.fromJson(parsed, client.gui.hud.getGuiTicks())
    }

    /** Wipes the live history and deletes the sidecar (keybind + settings button). */
    fun clear() {
        if (client.gui != null && client.gui.hud != null) {
            // clearMessages starts by flushing the chat listener's delayed queue into the
            // history, so a second clear is needed to actually end up with an empty chat.
            liveChat().clearMessages(true)
            liveChat().clearMessages(true)
        }
        try {
            Files.deleteIfExists(storeFile())
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to delete persisted chat history", e)
        }
        if (client.player != null) {
            Util.addMessageToChatHud(Component.translatable("persistent_chat.cleared"))
        }
    }

    fun storeFile(): Path = STORE_DIR.resolve("chat-history.json")

    private fun readFile(): JsonObject? {
        val file = storeFile()
        if (!Files.isRegularFile(file)) {
            return null
        }
        return try {
            val element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
            if (element.isJsonObject) element.asJsonObject else null
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Chat history file is damaged, ignoring it", e)
            null
        }
    }

    companion object {
        /** Sidecar location: `<game dir>/imperiumclient/chat-history.json`. */
        @JvmField
        val STORE_DIR: Path = FabricLoader.getInstance().gameDir.resolve("imperiumclient")

        private val Instance = ChatPersistence()

        @JvmStatic
        fun getInstance(): ChatPersistence = Instance
    }
}
