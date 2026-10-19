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

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.Util;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.network.chat.Component;

/**
 * Quick phrases (the AutoGG-class QoL row, config category {@code quickphrases}): three
 * free-text slots ({@code phrase_1..3}), each with its own keybind, that send the configured
 * text through the <em>ordinary</em> chat-send path — {@link Util#sendChatMessage}, which is
 * {@code player.connection.sendChat} / {@code sendCommand} — so the local rules enforcement
 * layer's outgoing chokepoint and the server both see plain, ordinary chat or command
 * packets, indistinguishable from typing. No packet is ever injected directly.
 *
 * <p>Tier system: QoL family (docs/server-gated-features.md) — on by default in singleplayer
 * and on v1 servers; a server {@code deny} force-offs it through
 * {@link ServerGating#enforceQoL}, and the {@code block_mods} channel can lock it the same
 * way ({@link FeatureDisablerCommon#registerDeniable}). A locked module's keybinds send
 * nothing. Auto-GG on game-end detection is deliberately out of scope.</p>
 */
public class QuickPhrases extends AbstractModule {

	private static final QuickPhrases Instance = new QuickPhrases();

	public static final String MODULE_ID = "quick_phrases";
	public static final String LOCK_REASON_KEY = "imperiumclient.gate.quick_phrases";

	/** QoL tier: usable by default, only a server deny locks it. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", true);

	public final StringOption phrase1 = new StringOption("phrase_1", "gg");
	public final StringOption phrase2 = new StringOption("phrase_2", "");
	public final StringOption phrase3 = new StringOption("phrase_3", "");

	private final OptionCategory category = OptionCategory.create("quickphrases");

	private final AxoKeybinding[] phraseKeys = new AxoKeybinding[3];

	public static QuickPhrases getInstance() {
		return Instance;
	}

	private QuickPhrases() {
	}

	@Override
	public void init() {
		category.add(enabled, phrase1, phrase2, phrase3);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);

		// Server-denyable through the block_mods channel too (FeatureDisabler pattern).
		FeatureDisablerCommon.registerDeniable(MODULE_ID, enabled);

		for (int slot = 0; slot < phraseKeys.length; slot++) {
			final StringOption option = phraseOption(slot);
			phraseKeys[slot] = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "quick_phrase_" + (slot + 1));
			phraseKeys[slot].br$registerOnConsumeClick(() -> sendPhrase(option));
		}
	}

	@Override
	public void tick() {
		// Re-evaluated every tick so a mid-session deny stops the very next keybind press.
		ServerGating.enforceQoL(enabled, MODULE_ID, LOCK_REASON_KEY);
	}

	private StringOption phraseOption(int slot) {
		return switch (slot) {
			case 0 -> phrase1;
			case 1 -> phrase2;
			default -> phrase3;
		};
	}

	/**
	 * Sends one phrase slot through the normal chat-send path. Takes the option (not the slot
	 * index) so tests can drive the exact path a keypress takes.
	 */
	public void sendPhrase(StringOption option) {
		if (!enabled.get()) {
			return; // locked (server deny / block_mods) or toggled off: keybind is a no-op
		}
		if (client.player == null) {
			return;
		}
		String phrase = option.get() == null ? "" : option.get().trim();
		if (phrase.isEmpty()) {
			return; // unconfigured slot: silence, not an error the player has to read
		}
		Util.sendChatMessage(phrase);
	}
}
