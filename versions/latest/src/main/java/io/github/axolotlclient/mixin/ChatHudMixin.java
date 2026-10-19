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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent;
import io.github.axolotlclient.util.ChatPeek;
import io.github.axolotlclient.util.duck.ChatHudExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements ChatHudExtension {

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

	@Unique
	private static final DateTimeFormatter axolotlclient$TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

	@WrapMethod(method = "addMessage")
	private void onChatMessage(Component contents, MessageSignature signature, GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, Operation<Void> original) {
		ReceiveChatMessageEvent event = new ReceiveChatMessageEvent(false, contents.getString(), contents);
		Events.RECEIVE_CHAT_MESSAGE.invoker().accept(event);
		if (event.isCancelled()) {
			return;
		} else if (event.getNewMessage() != null) {
			contents = (Component) event.getNewMessage();
		}

		// Empty filter: blank/whitespace-only lines are pure formatting noise; drop them before
		// they ever reach the history.
		if (AxolotlClient.config().chatEmptyFilter.get() && contents.getString().isBlank()) {
			return;
		}

		// Own-name highlight (and optional ping sound) runs before the timestamp prefix so the
		// gold tint is applied to the message text itself, not the timestamp.
		contents = axolotlclient$highlightOwnName(contents);

		if (AxolotlClient.config().chatTimestamps.get()) {
			MutableComponent stamped = Component.empty();
			stamped.append(Component.literal("[" + LocalTime.now().format(axolotlclient$TIMESTAMP_FORMAT) + "] ").withStyle(ChatFormatting.GRAY));
			stamped.append(contents);
			contents = stamped;
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

	/**
	 * Own-name highlight: case-insensitively finds occurrences of the local player's name inside
	 * literal text segments of the component tree and tints them gold, preserving every other style
	 * (colors, click/hover events) by rebuilding only the nodes on the path to a match. Returns the
	 * original component untouched when the name does not occur.
	 */
	@Unique
	private static Component axolotlclient$highlightOwnName(Component component) {
		if (!AxolotlClient.config().chatNameHighlight.get()) {
			return component;
		}

		String name = Minecraft.getInstance().getUser().getName();
		if (name == null || name.length() < 2) {
			return component;
		}

		String needle = name.toLowerCase(Locale.ROOT);
		MutableComponent highlighted = axolotlclient$highlightIn(component, needle);
		if (highlighted == null) {
			return component;
		}

		if (AxolotlClient.config().chatNamePing.get()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F));
		}
		return highlighted;
	}

	/**
	 * Recursive worker for {@link #axolotlclient$highlightOwnName(Component)}; returns {@code null}
	 * when nothing in this subtree matched so untouched branches keep their exact component tree.
	 */
	@Unique
	private static MutableComponent axolotlclient$highlightIn(Component component, String needle) {
		if (component.getContents() instanceof PlainTextContents plain && plain.text().toLowerCase(Locale.ROOT).contains(needle)) {
			MutableComponent rebuilt = Component.empty().withStyle(component.getStyle());
			axolotlclient$appendHighlighted(rebuilt, plain.text(), component.getStyle(), needle);
			for (Component sibling : component.getSiblings()) {
				rebuilt.append(sibling.copy());
			}
			return rebuilt;
		}

		List<Component> siblings = component.getSiblings();
		for (int i = 0; i < siblings.size(); i++) {
			MutableComponent highlighted = axolotlclient$highlightIn(siblings.get(i), needle);
			if (highlighted != null) {
				MutableComponent rebuilt = MutableComponent.create(component.getContents()).withStyle(component.getStyle());
				for (int j = 0; j < siblings.size(); j++) {
					rebuilt.append(j == i ? highlighted : siblings.get(j).copy());
				}
				return rebuilt;
			}
		}
		return null;
	}

	/**
	 * Splits one literal string around case-insensitive matches of the name, appending the untouched
	 * segments with the original style (inherited from {@code parent}) and the matches in gold.
	 */
	@Unique
	private static void axolotlclient$appendHighlighted(MutableComponent parent, String text, net.minecraft.network.chat.Style style, String needle) {
		String haystack = text.toLowerCase(Locale.ROOT);
		int from = 0;
		int match;
		while ((match = haystack.indexOf(needle, from)) >= 0) {
			if (match > from) {
				parent.append(Component.literal(text.substring(from, match)).withStyle(style));
			}
			parent.append(Component.literal(text.substring(match, match + needle.length())).withStyle(style.withColor(ChatFormatting.GOLD)));
			from = match + needle.length();
		}
		if (from < text.length()) {
			parent.append(Component.literal(text.substring(from)).withStyle(style));
		}
	}

	/**
	 * Chat peek (Alpine parity): while the peek key is held in-game, the chat renders at focused
	 * height instead of the collapsed unfocused height, so the full history becomes visible without
	 * opening the chat screen. Screen-open states never peek (the chat screen already shows all).
	 */
	@Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$peekFullHeight(CallbackInfoReturnable<Integer> cir) {
		if (ChatPeek.isActive()) {
			cir.setReturnValue(ChatComponent.getHeight(Minecraft.getInstance().options.chatHeightFocused().get()));
		}
	}

	/**
	 * Hover highlight: the per-line backdrop fill lambda of the focused chat pass draws a subtle
	 * colored overlay on the line currently under the mouse, before the text pass runs. The mouse
	 * position is only tracked while the chat screen is open, so this is a no-op in HUD/background
	 * mode (where the cursor is grabbed anyway).
	 */
	@Inject(method = "lambda$extractRenderState$1", at = @At("TAIL"))
	private static void axolotlclient$hoverHighlight(int chatBottom, int entryHeight, ChatGraphicsAccess graphics, int chatWidth, float backgroundOpacity, GuiMessage.Line line, int lineIndex, float alpha, CallbackInfo ci) {
		if (AxolotlClient.config() == null || !AxolotlClient.config().chatHoverHighlight.get() || !(graphics instanceof ChatDrawingFocusedAccessAccessor focused)) {
			return;
		}

		Vector2f mouse = focused.axolotlclient$getLocalMousePos();
		int top = chatBottom - lineIndex * entryHeight;
		if (mouse.y >= top - entryHeight && mouse.y <= top && mouse.x >= -4 && mouse.x <= chatWidth + 8) {
			graphics.fill(-4, top - entryHeight, chatWidth + 8, top, AxolotlClient.config().chatHoverHighlightColor.get().toInt());
		}
	}

	@Override
	public List<GuiMessage> axolotlclient$getMessages() {
		return allMessages;
	}
}
