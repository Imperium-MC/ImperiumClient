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

package io.github.axolotlclient.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	/**
	 * Base (suffix-less) text of the message that currently ends the chat, while compact chat is
	 * stacking consecutive duplicates of it. {@code null} when no streak is live.
	 */
	@Unique
	private String axolotlclient$compactStreakBase = null;

	@Unique
	private int axolotlclient$compactStreakCount = 0;

	@WrapMethod(method = "addMessage")
	private void onChatMessage(Component contents, MessageSignature signature, GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, Operation<Void> original) {
		ReceiveChatMessageEvent event = new ReceiveChatMessageEvent(false, contents.getString(), contents);
		Events.RECEIVE_CHAT_MESSAGE.invoker().accept(event);
		if (event.isCancelled()) {
			return;
		} else if (event.getNewMessage() != null) {
			contents = (Component) event.getNewMessage();
		}

		if (axolotlclient$compactChatShouldStack(contents)) {
			// Cancel-and-re-add: drop the previous entry of the streak, then let the vanilla add
			// path re-insert the last instance's components with the counter appended.
			GuiMessage previous = allMessages.removeFirst();
			while (!trimmedMessages.isEmpty() && trimmedMessages.getFirst().parent() == previous) {
				trimmedMessages.removeFirst();
			}
			contents = contents.copy()
				.append(Component.literal(" x" + axolotlclient$compactStreakCount).withStyle(ChatFormatting.GRAY));
		}

		original.call(contents, signature, source, tag);
	}

	/**
	 * Decides whether the incoming message continues the live duplicate streak; maintains the streak
	 * state either way. Equality is on the plain visible text only — styling and click/hover events of
	 * the last instance are what end up rendered.
	 */
	@Unique
	private boolean axolotlclient$compactChatShouldStack(Component incoming) {
		if (AxolotlClient.config() == null || !AxolotlClient.config().compactChat.get()) {
			axolotlclient$compactStreakBase = null;
			axolotlclient$compactStreakCount = 0;
			return false;
		}

		String text = incoming.getString();
		if (text.isBlank()) {
			// Blank lines never stack (an " x2" on an empty line is noise) and break any streak.
			axolotlclient$compactStreakBase = null;
			axolotlclient$compactStreakCount = 0;
			return false;
		}

		if (text.equals(axolotlclient$compactStreakBase) && axolotlclient$compactStreakIsHeadOfChat()) {
			axolotlclient$compactStreakCount++;
			return true;
		}

		axolotlclient$compactStreakBase = text;
		axolotlclient$compactStreakCount = 1;
		return false;
	}

	/**
	 * The streak only survives while the message we last compacted is still the newest one — anything
	 * that reshuffles the chat behind our back (clear, server-side delete, state restore, another
	 * mod) shows up here as a mismatch and safely breaks the streak.
	 */
	@Unique
	private boolean axolotlclient$compactStreakIsHeadOfChat() {
		if (axolotlclient$compactStreakCount < 1 || allMessages.isEmpty()) {
			return false;
		}

		String expected = axolotlclient$compactStreakCount > 1
			? axolotlclient$compactStreakBase + " x" + axolotlclient$compactStreakCount
			: axolotlclient$compactStreakBase;
		return allMessages.getFirst().content().getString().equals(expected);
	}
}
