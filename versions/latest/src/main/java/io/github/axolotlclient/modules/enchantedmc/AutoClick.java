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

package io.github.axolotlclient.modules.enchantedmc;

import java.util.Random;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.mixin.MinecraftClientAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.util.Util;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Clicker (Alpine "enchantedmc/clicking" parity) — <strong>server-gated</strong> per
 * docs/server-gated-features.md. Ships disabled and locked ("This server has not enabled
 * auto-clicking") until the current server grants {@code auto_click} via the
 * {@code imperiumclient:features} channel; singleplayer counts as granted.
 *
 * <p>Hold-to-click: while the bind is held, left-clicks fire at a CPS drawn uniformly from
 * [cps_min, cps_max] (each click re-rolls, ±20% interval jitter). Attack-only — every click
 * goes through the vanilla {@code Minecraft#startAttack} path, so guards, packets and hand
 * swing match a physical left click. Clicking stops the moment any screen (menu, chat,
 * inventory) is open, and resumes fresh (no burst) when it closes.</p>
 */
public class AutoClick extends AbstractModule {

	private static final AutoClick Instance = new AutoClick();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_click";

	private static final AxoKeybinding HOLD_KEY = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autoclick.hold");

	/** Locked-off by default: the server gate (via {@link ServerGating}) decides availability. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	public final IntegerOption cpsMin = new IntegerOption("cps_min", 8, 4, 16);
	public final IntegerOption cpsMax = new IntegerOption("cps_max", 12, 4, 16);

	private final OptionCategory category = OptionCategory.create("autoclick");
	private final Random random = new Random();

	/** Timestamp (Util.getMillis) of the next scheduled click; 0 while idle. */
	private long nextClickAt;

	public static AutoClick getInstance() {
		return Instance;
	}

	private AutoClick() {
	}

	@Override
	public void init() {
		category.add(enabled, cpsMin, cpsMax);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		// Server-denyable through the block_mods channel too (FeatureDisabler pattern).
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_CLICK, enabled);
		Events.DISCONNECT.register(this::stop);
	}

	@Override
	public void tick() {
		// The gate is re-evaluated before EVERY click: a grant revoked mid-session stops
		// the clicker on the next tick.
		ServerGating.enforce(enabled, ServerGating.AUTO_CLICK, LOCK_REASON_KEY);
		boolean holding = enabled.get() && client.player != null
			&& client.gui.screen() == null
			&& HOLD_KEY.br$isPressed();
		if (!holding) {
			stop();
			return;
		}

		long now = Util.getMillis();
		if (nextClickAt == 0) {
			nextClickAt = now + nextIntervalMs();
		}
		while (now >= nextClickAt) {
			((MinecraftClientAccessor) client).axolotlclient$startAttack();
			long interval = nextIntervalMs();
			nextClickAt += interval;
			if (nextClickAt < now) {
				// catch-up guard after a lag spike: never fire a burst
				nextClickAt = now + interval;
			}
		}
	}

	private void stop() {
		nextClickAt = 0;
	}

	/** Interval for one click: uniform CPS in [min, max], ±20% jitter, at least one tick. */
	@VisibleForTesting
	public long nextIntervalMs() {
		int min = Math.min(cpsMin.get(), cpsMax.get());
		int max = Math.max(cpsMin.get(), cpsMax.get());
		int cps = min + random.nextInt(max - min + 1);
		double jittered = (1000.0 / cps) * (0.8 + random.nextDouble() * 0.4);
		return Math.max(50, Math.round(jittered));
	}
}
