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

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Pure serializer for the {@code persistent_chat} module: the visible chat history (a
 * newest-first {@link GuiMessage} list, exactly what {@code ChatHudExtension} exposes) to and
 * from a small JSON document. No IO, no client state — the round-trip is unit-testable.
 *
 * <p>Format: {@code {"v":1,"messages":[{"t":<addedTime>,"s":<source>,"c":<component JSON>}]}}
 * with the newest message first. Message signatures, tags and deletion queue entries are
 * deliberately not persisted: signatures are worthless after a relog and a stale tag could
 * render a deletion marker over live text. Components serialize through the vanilla codec;
 * if one fails to encode (exotic translatable argument), the fallback stores its plain text
 * so a history line survives instead of the whole file being dropped.</p>
 */
public final class ChatHistoryCodec {

	@VisibleForTesting
	public static final int CURRENT_VERSION = 1;

	/** Hard cap on persisted lines (vanilla keeps at most 100 messages in memory anyway). */
	@VisibleForTesting
	public static final int MAX_LINES = 100;

	private ChatHistoryCodec() {
	}

	/** Serializes {@code messages} (newest first); only the newest {@link #MAX_LINES} are kept. */
	public static JsonObject toJson(List<GuiMessage> messages) {
		JsonObject root = new JsonObject();
		root.addProperty("v", CURRENT_VERSION);
		JsonArray array = new JsonArray();
		int kept = 0;
		for (GuiMessage message : messages) {
			if (kept >= MAX_LINES) {
				break;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("t", message.addedTime());
			entry.addProperty("s", message.source().name());
			JsonElement encoded = encodeComponent(message.content());
			if (encoded == null) {
				continue; // neither codec nor plain-text fallback can fail; guard anyway
			}
			entry.add("c", encoded);
			array.add(entry);
			kept++;
		}
		root.add("messages", array);
		return root;
	}

	/**
	 * Parses a document produced by {@link #toJson}. {@code currentGuiTicks} is the live chat
	 * tick counter at restore time: restored {@code addedTime}s are clamped to at least 20
	 * ticks old so a stale timestamp from a longer previous session can never make a message
	 * count as "brand new" (and never produce a negative age the fade logic does not expect).
	 * Damaged entries are skipped, never thrown.
	 */
	public static List<GuiMessage> fromJson(JsonObject root, int currentGuiTicks) {
		List<GuiMessage> result = new ArrayList<>();
		if (root == null || !root.has("messages") || !root.get("messages").isJsonArray()) {
			return result;
		}
		int floor = Math.max(0, currentGuiTicks - 20);
		for (JsonElement element : root.getAsJsonArray("messages")) {
			if (!element.isJsonObject() || result.size() >= MAX_LINES) {
				break;
			}
			JsonObject entry = element.getAsJsonObject();
			if (!entry.has("c")) {
				continue;
			}
			Component content = decodeComponent(entry.get("c"));
			if (content == null) {
				continue;
			}
			int addedTime = entry.has("t") && entry.get("t").isJsonPrimitive() ? entry.get("t").getAsInt() : 0;
			result.add(new GuiMessage(Math.min(Math.max(addedTime, 0), floor), content, null, parseSource(entry), null));
		}
		return result;
	}

	private static GuiMessageSource parseSource(JsonObject entry) {
		if (entry.has("s") && entry.get("s").isJsonPrimitive()) {
			try {
				return GuiMessageSource.valueOf(entry.get("s").getAsString());
			} catch (IllegalArgumentException ignored) {
				// unknown source name from a future version
			}
		}
		return GuiMessageSource.SYSTEM_CLIENT;
	}

	private static JsonElement encodeComponent(Component component) {
		JsonElement encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).result().orElse(null);
		if (encoded == null) {
			// Fallback: keep the visible text (styling lost) instead of dropping the line.
			encoded = new JsonObject();
			((JsonObject) encoded).addProperty("text", component.getString());
		}
		return encoded;
	}

	private static Component decodeComponent(JsonElement element) {
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
	}
}
