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

package io.github.axolotlclient.imperium.emotes;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.util.keybinds.KeyBinds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Client wiring of the emote wheel (W5-A): the keybind, the open tick, and the slot model the
 * screen renders. Own cosmetics — nothing of Alpine's is used or depended on.
 *
 * <p><strong>Scope note (gating piece):</strong> player-side animation rendering (arm waves,
 * sitting poses on other players) is deliberately OUT of scope for this shell. It is blocked on
 * the {@code imperiumclient:cosmetics} plugin channel, which does not exist yet: until the
 * server defines the emote wire format and broadcasts emotes to other clients, there is nothing
 * to animate. When that channel lands it swaps {@link EmoteDispatch}'s dispatcher and adds the
 * remote-player animation renderer; see docs/media-and-emotes.md.</p>
 */
public final class EmoteWheel {

	/**
	 * Hold to open the wheel (default G — B is taken by the waypoints-add keybind). Shown under
	 * the ImperiumClient controls category.
	 */
	public static final KeyMapping OPEN_WHEEL = new KeyMapping("key.imperiumclient.emote_wheel",
		InputConstants.KEY_G, KeyBinds.CATEGORY_AXOLOTLCLIENT);

	private static boolean initialized;

	public static synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		KeyBinds.getInstance().register(OPEN_WHEEL);
		ClientTickEvents.END_CLIENT_TICK.register(EmoteWheel::onEndTick);
	}

	/**
	 * Opens the wheel on the keybind's press edge ({@code consumeClick}). Holding behaviour
	 * comes from the screen closing on the key's release, not from polling {@code isDown}:
	 * vanilla zeroes all keybind states when a screen opens and re-polls them once it closes,
	 * so an {@code isDown}-held wheel would reopen itself whenever it closed while the key was
	 * still physically down (click-send, ESC). A consumed click only fires on a real press.
	 */
	private static void onEndTick(Minecraft client) {
		while (OPEN_WHEEL.consumeClick()) {
			if (client.player == null || client.gui.screen() != null || !EmoteWheelConfig.emotes.get()) {
				continue;
			}
			List<Emote> slots = slots();
			if (!slots.isEmpty()) {
				client.gui.setScreen(new EmoteWheelScreen(OPEN_WHEEL, slots));
			}
		}
	}

	/** The wheel's emotes, parsed from the config slots in order. */
	public static List<Emote> slots() {
		return Emotes.parseSlots(EmoteWheelConfig.slotValues());
	}

	private EmoteWheel() {
	}
}
