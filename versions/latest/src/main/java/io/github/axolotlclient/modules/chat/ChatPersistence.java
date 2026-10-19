/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.Util;
import io.github.axolotlclient.util.duck.ChatHudExtension;
import io.github.axolotlclient.util.options.GenericOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent chat (the {@code persistent_chat} chat-settings row): the visible history is
 * written to a sidecar JSON file ({@code imperiumclient/chat-history.json} in the game
 * directory) when a play session ends, and restored when the next one starts, so the chat
 * survives relogs.
 *
 * <p>Hooks: save on {@code Events.DISCONNECT} and {@code Events.CLIENT_STOP} (whichever
 * fires first wins; saving an empty history never overwrites an existing file, so a
 * teardown order that clears the chat before the event cannot wipe the store). Restore on
 * {@code Events.CONNECTION_PLAY_READY} — only into an <em>empty</em> history, because
 * vanilla itself carries chat state across dimension changes and server transfers
 * ({@code ChatComponent.storeState/restoreState}); when vanilla already restored, the
 * sidecar would only duplicate it.</p>
 *
 * <p>Clearing: the {@code clear_chat_history} keybind and a button in the chat settings both
 * wipe the live history and delete the sidecar.</p>
 */
public class ChatPersistence extends AbstractModule {

	private static final ChatPersistence Instance = new ChatPersistence();

	public static ChatPersistence getInstance() {
		return Instance;
	}

	/** Sidecar location: {@code <game dir>/imperiumclient/chat-history.json}. */
	public static final Path STORE_DIR = FabricLoader.getInstance().getGameDir().resolve("imperiumclient");

	private ChatPersistence() {
	}

	@Override
	public void init() {
		AxolotlClient.config().clearChatHistoryKey.br$registerOnConsumeClick(this::clear);
		AxolotlClient.config().chatSettings.add(new GenericOption("persistent_chat", "clear_history_button", this::clear));

		Events.DISCONNECT.register(this::save);
		Events.CLIENT_STOP.register(this::save);
		Events.CONNECTION_PLAY_READY.register(serverData -> restore());
	}

	private ChatComponent liveChat() {
		return client.gui.hud.getChat();
	}

	/** Writes the current visible history to the sidecar (no-op for an empty history). */
	public void save() {
		if (!AxolotlClient.config().persistentChat.get() || client.gui == null || client.gui.hud == null) {
			return;
		}
		List<GuiMessage> messages = ((ChatHudExtension) liveChat()).axolotlclient$getMessages();
		if (messages.isEmpty()) {
			// Never overwrite a previous session's file with nothing: if teardown already
			// cleared the chat before the disconnect event fired, the old history survives.
			return;
		}
		try {
			Files.createDirectories(STORE_DIR);
			Files.writeString(storeFile(), ChatHistoryCodec.toJson(messages).toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to persist chat history", e);
		}
	}

	/**
	 * Restores the persisted history into the live chat, but only when the live history is
	 * empty (vanilla's own state restore already ran otherwise). Runs on the join event.
	 */
	public void restore() {
		if (!AxolotlClient.config().persistentChat.get() || client.gui == null || client.gui.hud == null) {
			return;
		}
		ChatComponent chat = liveChat();
		if (!((ChatHudExtension) chat).axolotlclient$getMessages().isEmpty()) {
			return;
		}
		List<GuiMessage> restored = load();
		if (restored.isEmpty()) {
			return;
		}
		((ChatHudExtension) chat).axolotlclient$restoreMessages(restored);
	}

	/** Reads the sidecar; an empty list on any missing/damaged file. */
	public List<GuiMessage> load() {
		JsonObject parsed = readFile();
		return parsed == null ? List.of() : ChatHistoryCodec.fromJson(parsed, client.gui.hud.getGuiTicks());
	}

	/** Wipes the live history and deletes the sidecar (keybind + settings button). */
	public void clear() {
		if (client.gui != null && client.gui.hud != null) {
			// clearMessages starts by flushing the chat listener's delayed queue into the
			// history, so a second clear is needed to actually end up with an empty chat.
			liveChat().clearMessages(true);
			liveChat().clearMessages(true);
		}
		try {
			Files.deleteIfExists(storeFile());
		} catch (IOException e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to delete persisted chat history", e);
		}
		if (client.player != null) {
			Util.addMessageToChatHud(Component.translatable("persistent_chat.cleared"));
		}
	}

	public Path storeFile() {
		return STORE_DIR.resolve("chat-history.json");
	}

	private @Nullable JsonObject readFile() {
		Path file = storeFile();
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Chat history file is damaged, ignoring it", e);
			return null;
		}
	}
}
