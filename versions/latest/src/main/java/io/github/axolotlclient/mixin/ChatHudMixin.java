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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent;
import io.github.axolotlclient.util.ChatPeek;
import io.github.axolotlclient.util.chat.ChatScrollSmoothing;
import io.github.axolotlclient.util.duck.ChatHudExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.client.gui.components.ChatComponent.DisplayMode;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
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

	@Shadow
	private int chatScrollbarPos;

	@Shadow
	public abstract int getLinesPerPage();

	@Shadow
	abstract int getLineHeight();

	@Shadow
	abstract double getScale();

	@Shadow
	abstract int getWidth();

	@Shadow
	abstract int getHeight();

	@Shadow
	abstract void refreshTrimmedMessages();

	/**
	 * Base (suffix-less) text of the message that currently ends the chat, while compact chat is
	 * stacking consecutive duplicates of it. {@code null} when no streak is live.
	 */
	@Unique
	private String axolotlclient$compactStreakBase = null;

	@Unique
	private int axolotlclient$compactStreakCount = 0;

	/**
	 * Wall clock of the last stacked instance; drives compact_time_based stacking
	 * (Alpine profile M6). 0 = no live streak.
	 */
	@Unique
	private long axolotlclient$compactStreakAt = 0L;

	// ── Smooth chat (smooth_chat) state ─────────────────────────────────────────
	//
	// chatScrollbarPos stays vanilla-authoritative (the animation TARGET). The animated
	// fractional position below only drives rendering: line selection uses its floor and a
	// sub-line pose translation carries the remainder. Stepping happens once per rendered
	// frame in the public extractRenderState wrap (dt==0 later in the same frame is a no-op),
	// so the interpolation is frame-rate aware by construction.

	/** Animated fractional scroll position (lines; negative while a new message slides in). */
	@Unique
	private float axolotlclient$smoothScrollCurrent = 0.0F;

	/** Wall-clock sample of the last animation step (0 = none yet). */
	@Unique
	private long axolotlclient$smoothLastFrameMs = 0L;

	/** Sub-line y offset (local units) the current render pass translates the chat pose by. */
	@Unique
	private float axolotlclient$smoothRenderOffset = 0.0F;

	/** Whether the current render pass draws one extra line above the window (slide in/out). */
	@Unique
	private boolean axolotlclient$smoothExtraTopLine = false;

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
			stamped.append(Component.literal("[" + LocalTime.now().format(AxolotlClient.config().chatTimestampFormat.get().formatter()) + "] ").withStyle(ChatFormatting.GRAY));
			stamped.append(contents);
			contents = stamped;
		}

		if (axolotlclient$compactChatShouldStack(contents)) {
			// Cancel-and-re-add: drop the previous entry of the streak, then let the vanilla add
			// path re-insert the last instance's components with the counter appended.
			if (!axolotlclient$compactRemoveStackedInstance()) {
				return; // streak bookkeeping said stack, but the entry vanished underneath us
			}
			contents = contents.copy()
				.append(Component.literal(" x" + axolotlclient$compactStreakCount).withStyle(ChatFormatting.GRAY));
		}

		original.call(contents, signature, source, tag);
		axolotlclient$smoothChatKickForNewMessage();
	}

	/**
	 * Smooth chat: when a message lands while the chat is pinned to the bottom
	 * ({@code chatScrollbarPos == 0}), vanilla keeps the scroll target but the content visibly
	 * jumps up by the new lines. Starting the animation below the target by the number of
	 * lines just added makes the message slide in instead. (While scrolled up with the chat
	 * screen focused vanilla itself bumps the scroll per line, which the animation follows
	 * for free.)
	 */
	@Unique
	private void axolotlclient$smoothChatKickForNewMessage() {
		if (AxolotlClient.config() == null || !AxolotlClient.config().smoothChat.get() || chatScrollbarPos != 0) {
			return;
		}
		if (allMessages.isEmpty()) {
			return;
		}
		GuiMessage newest = allMessages.getFirst();
		int addedLines = 0;
		while (addedLines < trimmedMessages.size() && trimmedMessages.get(addedLines).parent() == newest) {
			addedLines++;
		}
		if (addedLines > 0) {
			axolotlclient$smoothScrollCurrent -= addedLines;
		}
	}

	/**
	 * Decides whether the incoming message continues the live duplicate streak; maintains the streak
	 * state either way. Equality is on the plain visible text only — styling and click/hover events of
	 * the last instance are what end up rendered.
	 *
	 * <p>Alpine profile M6 sub-settings: {@code compact_time_based} also stacks a duplicate that
	 * arrives within {@code compact_time_window} seconds even when other messages landed in between
	 * (the earlier instance is then re-found mid-list instead of expected at the head);
	 * {@code compact_ignore_line_breaks} normalizes embedded newlines before comparing;
	 * {@code compact_ignore_blank} keeps whitespace-only lines from stacking.</p>
	 */
	@Unique
	private boolean axolotlclient$compactChatShouldStack(Component incoming) {
		if (AxolotlClient.config() == null || !AxolotlClient.config().compactChat.get()) {
			axolotlclient$compactStreakBase = null;
			axolotlclient$compactStreakCount = 0;
			axolotlclient$compactStreakAt = 0L;
			return false;
		}

		String text = axolotlclient$compactNormalize(incoming.getString());
		if (text.isBlank()) {
			// compact_ignore_blank (default): blank lines are ignored noise — they neither
			// stack nor break a live streak, so duplicates split only by a blank line still
			// compact. With it off, a blank line resets the streak like any other message.
			if (!AxolotlClient.config().compactIgnoreBlank.get()) {
				axolotlclient$compactStreakBase = null;
				axolotlclient$compactStreakCount = 0;
				axolotlclient$compactStreakAt = 0L;
			}
			return false;
		}

		if (text.equals(axolotlclient$compactStreakBase) && axolotlclient$compactStreakEligible()) {
			axolotlclient$compactStreakCount++;
			axolotlclient$compactStreakAt = System.currentTimeMillis();
			return true;
		}

		axolotlclient$compactStreakBase = text;
		axolotlclient$compactStreakCount = 1;
		axolotlclient$compactStreakAt = System.currentTimeMillis();
		return false;
	}

	/** Normalized comparison form of a chat line (compact_ignore_line_breaks). */
	@Unique
	private String axolotlclient$compactNormalize(String text) {
		if (AxolotlClient.config().compactIgnoreLineBreaks.get()) {
			return text.replace("\n", " ").replace("\r", " ").trim();
		}
		return text;
	}

	/**
	 * Whether the live streak may still be extended by a duplicate: consecutive mode
	 * requires the compacted message to still be the newest one — anything that
	 * reshuffles the chat behind our back (clear, server-side delete, state restore,
	 * another mod) shows up as a mismatch and safely breaks the streak. Time-based
	 * mode instead only requires arrival within the window; the removal pass then
	 * re-finds the entry mid-list, breaking the streak itself if it is gone.
	 */
	@Unique
	private boolean axolotlclient$compactStreakEligible() {
		if (AxolotlClient.config().compactTimeBased.get()) {
			return axolotlclient$compactStreakAt > 0
				&& System.currentTimeMillis() - axolotlclient$compactStreakAt
				<= AxolotlClient.config().compactTimeWindow.get() * 1000L;
		}
		return axolotlclient$compactStreakIsHeadOfChat();
	}

	/**
	 * Removes the previously stacked instance from the chat. Consecutive mode: it is the
	 * head (as verified by {@link #axolotlclient$compactStreakIsHeadOfChat()}); time-based
	 * mode: it is searched for anywhere in the history. Returns false when the entry is
	 * gone (the caller then leaves the message unstacked).
	 */
	@Unique
	private boolean axolotlclient$compactRemoveStackedInstance() {
		String expected = axolotlclient$compactStreakCount > 1
			? axolotlclient$compactStreakBase + " x" + axolotlclient$compactStreakCount
			: axolotlclient$compactStreakBase;
		if (AxolotlClient.config().compactTimeBased.get()) {
			for (int i = 0; i < allMessages.size(); i++) {
				GuiMessage candidate = allMessages.get(i);
				if (axolotlclient$compactNormalize(candidate.content().getString()).equals(expected)) {
					allMessages.remove(i);
					trimmedMessages.removeIf(line -> line.parent() == candidate);
					return true;
				}
			}
			return false;
		}
		if (allMessages.isEmpty()) {
			return false;
		}
		GuiMessage previous = allMessages.getFirst();
		if (!axolotlclient$compactNormalize(previous.content().getString()).equals(expected)) {
			return false;
		}
		allMessages.removeFirst();
		while (!trimmedMessages.isEmpty() && trimmedMessages.getFirst().parent() == previous) {
			trimmedMessages.removeFirst();
		}
		return true;
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
		return axolotlclient$compactNormalize(allMessages.getFirst().content().getString()).equals(expected);
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

	/**
	 * Smooth chat, public render entry: advances the animation once per frame (later passes in
	 * the same frame sample dt 0 and stay put) and — while an animation is actually in flight —
	 * scissors the drawing to the chat rectangle so the fractional slide cannot bleed over the
	 * rest of the HUD (one extra line is drawn above the window during a slide; without the
	 * clip it would float over the game view).
	 */
	@WrapMethod(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V")
	private void axolotlclient$smoothChatStep(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY, int arg, DisplayMode mode, boolean restricted, Operation<Void> original) {
		boolean smooth = AxolotlClient.config() != null && AxolotlClient.config().smoothChat.get();
		if (smooth) {
			long now = Util.getMillis();
			long dt = axolotlclient$smoothLastFrameMs == 0L ? 16L : now - axolotlclient$smoothLastFrameMs;
			axolotlclient$smoothLastFrameMs = now;
			axolotlclient$smoothScrollCurrent = ChatScrollSmoothing.step(axolotlclient$smoothScrollCurrent, chatScrollbarPos, dt,
				AxolotlClient.config().smoothChatDuration.get());
		} else if (axolotlclient$smoothScrollCurrent != chatScrollbarPos) {
			axolotlclient$smoothScrollCurrent = chatScrollbarPos;
		}

		if (!smooth || !ChatScrollSmoothing.animating(axolotlclient$smoothScrollCurrent, chatScrollbarPos)) {
			axolotlclient$smoothRenderOffset = 0.0F;
			axolotlclient$smoothExtraTopLine = false;
			original.call(extractor, font, mouseX, mouseY, arg, mode, restricted);
			return;
		}

		axolotlclient$smoothRenderOffset = ChatScrollSmoothing.pixelOffset(axolotlclient$smoothScrollCurrent, getLineHeight());
		axolotlclient$smoothExtraTopLine = axolotlclient$smoothRenderOffset > ChatScrollSmoothing.SNAP_THRESHOLD;

		int guiHeight = extractor.guiHeight();
		// All in GUI units (the scissor is transformed by the base pose): vanilla lays the
		// chat out with a 40 px bottom margin; one extra line keeps the queued-messages
		// notice below the chat visible while the slide runs.
		int lineHeightGui = Mth.ceil(getLineHeight() * (float) getScale());
		int bottom = guiHeight - 40 + lineHeightGui + 8;
		int top = bottom - Mth.ceil(getHeight() * (float) getScale()) - 1;
		int right = Mth.ceil((Mth.ceil(getWidth() / (float) getScale()) + 12) * (float) getScale());
		extractor.enableScissor(0, top, right, bottom);
		try {
			original.call(extractor, font, mouseX, mouseY, arg, mode, restricted);
		} finally {
			extractor.disableScissor();
		}
	}

	/**
	 * Smooth chat, the pass both render modes and the click-capture path funnel through: line
	 * selection runs at the animation's floor while the authoritative scrollbar value is
	 * restored afterwards, so vanilla logic (clamping, delete handling, scrollbar state) is
	 * untouched.
	 */
	@WrapMethod(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V")
	private void axolotlclient$smoothChatWindow(ChatGraphicsAccess graphics, int guiHeight, int mouseY, DisplayMode mode, Operation<Void> original) {
		if (axolotlclient$smoothRenderOffset == 0.0F) {
			original.call(graphics, guiHeight, mouseY, mode);
			return;
		}
		int realScrollbarPos = chatScrollbarPos;
		chatScrollbarPos = ChatScrollSmoothing.windowBase(axolotlclient$smoothScrollCurrent);
		try {
			original.call(graphics, guiHeight, mouseY, mode);
		} finally {
			chatScrollbarPos = realScrollbarPos;
		}
	}

	/**
	 * Smooth chat: after vanilla's scale-and-shift pose setup, add the sub-line translation the
	 * current frame is at. Applied inside every graphics access (background, focused and
	 * click-capture), so hit testing stays in the same coordinate space as the pixels.
	 */
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V"))
	private void axolotlclient$smoothChatPose(ChatGraphicsAccess graphics, Consumer<Matrix3x2f> vanillaPose, Operation<Void> original) {
		original.call(graphics, vanillaPose);
		float offset = axolotlclient$smoothRenderOffset;
		if (offset != 0.0F) {
			graphics.updatePose(matrix -> matrix.translate(0.0F, offset));
		}
	}

	/**
	 * Smooth chat: while a slide is in flight the window needs one extra line above the vanilla
	 * range — the line sliding in from (or out through) the top edge. Reimplements vanilla's
	 * tiny loop with {@code end} starting one higher; when the history does not overflow the
	 * window the extra index points past the list and is skipped, which makes this identical
	 * to vanilla.
	 */
	@WrapMethod(method = "forEachLine")
	private int axolotlclient$smoothChatLines(ChatComponent.AlphaCalculator calculator, ChatComponent.LineConsumer consumer, Operation<Integer> original) {
		if (!axolotlclient$smoothExtraTopLine) {
			return original.call(calculator, consumer);
		}

		int linesPerPage = getLinesPerPage();
		int vanillaEnd = Math.min(trimmedMessages.size() - chatScrollbarPos, linesPerPage) - 1;
		int count = 0;
		boolean drewExtra = false;
		int end = vanillaEnd + 1;
		while (end >= 0) {
			int start = end + chatScrollbarPos;
			if (start < trimmedMessages.size()) {
				GuiMessage.Line line = trimmedMessages.get(start);
				float alpha = calculator.calculate(line);
				if (alpha > 1.0E-5F) {
					count++;
					if (end > vanillaEnd) {
						drewExtra = true;
					}
					consumer.accept(line, end, alpha);
				}
			}
			end--;
		}
		// Keep the returned visible-line count vanilla-equivalent (the caller sizes the
		// scrollbar thumb from it).
		return count - (drewExtra ? 1 : 0);
	}

	/** Snap the smooth animation whenever the history is replaced wholesale. */
	@Inject(method = {"clearMessages", "restoreState", "rescaleChat"}, at = @At("HEAD"))
	private void axolotlclient$snapSmoothScroll(CallbackInfo ci) {
		axolotlclient$smoothScrollCurrent = chatScrollbarPos;
	}

	@Override
	public List<GuiMessage> axolotlclient$getMessages() {
		return allMessages;
	}

	@Override
	public void axolotlclient$restoreMessages(List<GuiMessage> messages) {
		allMessages.addAll(messages);
		refreshTrimmedMessages();
		axolotlclient$smoothScrollCurrent = chatScrollbarPos;
	}

	@Override
	public float axolotlclient$getSmoothScrollCurrent() {
		return axolotlclient$smoothScrollCurrent;
	}
}
