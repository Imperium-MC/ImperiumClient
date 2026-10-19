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

package io.github.axolotlclient.modules.autoutils;

import java.util.Map;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Auto-Reconnect (wave 4 native utility) — QoL tier per docs/server-gated-features.md:
 * ships enabled, only a server deny (features or block_mods channel) force-offs it via
 * {@link ServerGating#enforceQoL}.
 *
 * <p>When the connection to a server drops, a countdown (default 5s) is drawn on the
 * disconnect screen and the client rejoins through the vanilla
 * {@link ConnectScreen#startConnecting} path — the same connect flow the multiplayer menu
 * uses, cookies and all. It only ever reconnects to the <strong>same</strong> server: the
 * {@link ServerData} captured when the session started is the one and only target. The
 * countdown cancels the moment the player leaves the disconnect screen (anything else they
 * opened wins), and {@code max_attempts} bounds the retry loop; a successful join resets
 * the attempt counter.</p>
 */
public class AutoReconnect extends AbstractModule {

	private static final AutoReconnect Instance = new AutoReconnect();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_reconnect";

	/** QoL tier: on by default, only a server deny locks it. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", true);
	/** Seconds to wait on the disconnect screen before rejoining. */
	public final IntegerOption delaySeconds = new IntegerOption("delay_seconds", 5, 1, 300);
	/** Reconnect attempts per disconnect streak; a successful join resets the counter. */
	public final IntegerOption maxAttempts = new IntegerOption("max_attempts", 3, 1, 50);

	private final OptionCategory category = OptionCategory.create("autoreconnect");

	/** The one and only reconnect target: the server this session actually joined. */
	@Nullable
	private ServerData lastServer;
	/** Reconnects already fired since the last successful join. */
	private int attempts;
	/** True while counting down on a disconnect screen we own. */
	private boolean counting;
	/** The disconnect screen the countdown is attached to (leaving it cancels). */
	@Nullable
	private Screen attachedScreen;
	/** The countdown label on that screen; null until the screen mixin attached one. */
	@Nullable
	private StringWidget countdownLabel;
	private int lastRenderedSeconds = -1;
	/** When the countdown started (Util.getMillis); the deadline is this plus the delay. */
	private long attachAtMs;

	public static AutoReconnect getInstance() {
		return Instance;
	}

	private AutoReconnect() {
	}

	@Override
	public void init() {
		category.add(enabled, delaySeconds, maxAttempts);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_RECONNECT, enabled);

		// Remember the joined server (the only reconnect target) and reset the streak on join.
		Events.CONNECTION_PLAY_READY.register(info -> {
			attempts = 0;
			cancel();
			if (info instanceof ServerData data) {
				lastServer = data;
			}
		});
	}

	@Override
	public void tick() {
		ServerGating.enforceQoL(enabled, ServerGating.AUTO_RECONNECT, LOCK_REASON_KEY);
		if (!counting) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!enabled.get() || client.player != null || attachedScreen == null || client.gui.screen() != attachedScreen) {
			cancel(); // module off, joined somewhere else, or the player moved on — their choice wins
			return;
		}
		updateLabel();
		if (Util.getMillis() >= attachAtMs + delaySeconds.get() * 1000L) {
			fire();
		}
	}

	/** True when the disconnect screen should attach a countdown (module on, target known, attempts left). */
	public boolean wantsCountdown() {
		return enabled.get() && lastServer != null && attempts < maxAttempts.get();
	}

	/**
	 * Called by {@code DisconnectedScreenMixin} when the disconnect screen builds: the label
	 * lives on the screen, the countdown itself starts here. Screens can re-init (resize),
	 * so attaching twice must restart rather than double-count.
	 */
	public void attachCountdown(DisconnectedScreen screen, StringWidget label) {
		attachedScreen = screen;
		countdownLabel = label;
		counting = true;
		attachAtMs = Util.getMillis();
		lastRenderedSeconds = -1;
		updateLabel();
	}

	private void updateLabel() {
		if (countdownLabel == null) {
			return;
		}
		long remainingMs = attachAtMs + delaySeconds.get() * 1000L - Util.getMillis();
		int seconds = Mth.positiveCeilDiv((int) Math.min(Integer.MAX_VALUE, Math.max(0, remainingMs)), 1000);
		if (seconds != lastRenderedSeconds) {
			lastRenderedSeconds = seconds;
			countdownLabel.setMessage(Component.translatable("imperiumclient.autoreconnect.countdown",
				seconds, attempts + 1, maxAttempts.get()));
		}
		// keep the label centered as the text width changes
		Screen screen = attachedScreen;
		if (screen == null) {
			return;
		}
		int width = countdownLabel.getWidth();
		int x = Mth.clamp((screen.width - width) / 2, 0, Math.max(0, screen.width - width));
		countdownLabel.setX(x);
	}

	private void fire() {
		counting = false;
		ServerData target = lastServer;
		Screen parent = attachedScreen;
		if (target == null || parent == null) {
			cancel();
			return;
		}
		attempts++;
		ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(target.ip), target, false,
			new TransferState(Map.of(), Map.of(), false));
	}

	private void cancel() {
		counting = false;
		attachedScreen = null;
		countdownLabel = null;
		lastRenderedSeconds = -1;
	}
}
