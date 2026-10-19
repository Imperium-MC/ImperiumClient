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

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.imperium.ImperiumClientSession;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Walk (wave 4 native utility) — <strong>limited tier</strong> per
 * docs/server-gated-features.md. Ships disabled like the other gated modules; the current
 * server must grant it via the {@code imperiumclient:features} channel, either as a plain
 * allow (v1/v2 {@code allow}) or — the ImperiumMC default — in the v2 {@code limited} tier,
 * where every walk <em>session start</em> spends one token from the server-set rate budget
 * ({"auto_walk":{"per_hour":N}}); once the budget is spent the module force-offs with the
 * rate tooltip until the bucket refills. Singleplayer counts as granted.
 *
 * <p>Movement itself is key-state simulation, exactly the ToggleSprint route: the forward
 * flag is OR-ed into the {@code Input} record {@code KeyboardInput#tick} builds (see
 * {@code KeyboardInputMixin}), so walking uses the ordinary movement input pipeline — no
 * packets are injected, no positions forged; the server sees (and can stop) a player
 * holding W. Hold mode walks while the key is down; toggle mode walks until toggled off,
 * any screen open pauses the input the way a released key would.</p>
 */
public class AutoWalk extends AbstractModule {

	private static final AutoWalk Instance = new AutoWalk();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_walk";
	public static final String RATE_REASON_KEY = "imperiumclient.gate.auto_walk.rate";

	private static final AxoKeybinding WALK_KEY = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autowalk.toggle");

	/** Locked-off by default: the server gate decides availability (grant or limited tier). */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	/** Hold: walk while the key is down. Toggle: walk from one press until the next. */
	public final EnumOption<Mode> mode = new EnumOption<>("mode", Mode.class, Mode.TOGGLE);

	private final OptionCategory category = OptionCategory.create("autowalk");

	/** True while a walk session is paid for and active. */
	private boolean walking;

	public static AutoWalk getInstance() {
		return Instance;
	}

	private AutoWalk() {
	}

	@Override
	public void init() {
		category.add(enabled, mode);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_WALK, enabled);

		WALK_KEY.br$registerOnConsumeClick(() -> {
			if (mode.get() == Mode.TOGGLE && enabled.get()) {
				toggleWalking();
			}
		});
		Events.DISCONNECT.register(this::stopWalking);
	}

	@Override
	public void tick() {
		// Tier is re-evaluated before every activation step: a grant revoked mid-session
		// (replacement features packet, deny, or a spent rate budget) stops walking next tick.
		ServerGating.enforce(enabled, ServerGating.AUTO_WALK, LOCK_REASON_KEY, RATE_REASON_KEY);
		if (!enabled.get()) {
			stopWalking();
			return;
		}
		if (client.player == null) {
			stopWalking();
			return;
		}

		boolean want = mode.get() == Mode.HOLD ? WALK_KEY.br$isPressed() : walking;
		if (want && !walking) {
			// Limited tier: each session start spends one token; no budget, no session.
			want = ImperiumClientSession.getInstance().tryConsumeBudget(ServerGating.AUTO_WALK);
		}
		walking = want;
	}

	/**
	 * The forward-key state the {@code KeyboardInputMixin} ORs into the vanilla input — only
	 * while the module is enabled (gate re-checked here so a stale flag can never feed the
	 * input after a force-off), a world is up, and no screen holds the input, mirroring
	 * ToggleSprint's guard.
	 */
	public boolean isWalking() {
		return walking && enabled.get() && client.player != null && client.gui.screen() == null;
	}

	@VisibleForTesting
	void toggleWalking() {
		walking = !walking;
		if (walking && !ImperiumClientSession.getInstance().tryConsumeBudget(ServerGating.AUTO_WALK)) {
			walking = false;
		}
	}

	private void stopWalking() {
		walking = false;
	}

	public enum Mode {
		HOLD, TOGGLE;

		@Override
		public String toString() {
			return "autowalk.mode." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
