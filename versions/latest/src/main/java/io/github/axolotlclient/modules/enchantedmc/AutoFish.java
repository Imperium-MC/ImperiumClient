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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.imperium.ServerGating;
import io.github.axolotlclient.mixin.FishingHookAccessor;
import io.github.axolotlclient.mixin.MinecraftClientAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Auto-Fish (Alpine "enchantedmc/fishing" parity) — <strong>server-gated</strong> per
 * docs/server-gated-features.md. Ships disabled; it stays locked with a tooltip reason
 * ("This server has not enabled auto-fishing") until the current server grants
 * {@code auto_fish} via the {@code imperiumclient:features} channel. Singleplayer counts
 * as granted.
 *
 * <p>Bite detection: the bobber's synced {@code DATA_BITING} flag — the exact signal the
 * vanilla client sees (the same update dips the bobber). Polled every tick from
 * {@code player.fishing}, so a module enabled mid-bite still catches it. Reeling and
 * recasting both go through the vanilla use-item path ({@code Minecraft#startUseItem} via
 * an invoker), i.e. the packets a physical right-click sends. The recast waits a
 * randomized delay (uniform in [delay_min, delay_max], then ±20% jitter) so it doesn't
 * metronome.</p>
 */
public class AutoFish extends AbstractModule {

	private static final AutoFish Instance = new AutoFish();

	public static final String LOCK_REASON_KEY = "imperiumclient.gate.auto_fish";

	/** Locked-off by default: the server gate (via {@link ServerGating}) decides availability. */
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	/** Recast delay bounds in milliseconds; each recast draws from this range. */
	public final IntegerOption delayMin = new IntegerOption("delay_min", 500, 100, 5000);
	public final IntegerOption delayMax = new IntegerOption("delay_max", 2000, 100, 5000);
	public final BooleanOption autoRecast = new BooleanOption("auto_recast", true);

	// Alpine profile settings (M6): whether an open screen pauses the automation (the
	// pre-M6 behaviour, still the default) and a keybind flipping the module toggle.
	// The keybind cannot bypass the server gate — every tick re-runs
	// ServerGating#enforce, which re-locks a denied module immediately.
	public final BooleanOption pauseInMenus = new BooleanOption("pause_in_menus", true);
	public final AxoKeybinding toggleKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.autofish.toggle");

	private final OptionCategory category = OptionCategory.create("autofish");
	private final Random random = new Random();

	/** True once the reel use was sent for the current bobber; cleared when the bobber is gone. */
	private boolean reelSent;
	/** Ticks until the recast; negative when no recast is scheduled. */
	private int recastCountdown = -1;

	public static AutoFish getInstance() {
		return Instance;
	}

	private AutoFish() {
	}

	@Override
	public void init() {
		category.add(enabled, delayMin, delayMax, autoRecast, pauseInMenus);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		toggleKey.br$registerOnConsumeClick(() -> {
			if (ServerGating.granted(ServerGating.AUTO_FISH)) {
				enabled.toggle();
			}
		});
		// Server-denyable through the block_mods channel too (FeatureDisabler pattern).
		FeatureDisablerCommon.registerDeniable(ServerGating.AUTO_FISH, enabled);
		Events.DISCONNECT.register(this::resetState);
	}

	@Override
	public void tick() {
		// The gate is re-evaluated before EVERY automation step: a grant revoked mid-session
		// (replacement features packet, or a deny) stops the module on the next tick.
		ServerGating.enforce(enabled, ServerGating.AUTO_FISH, LOCK_REASON_KEY);
		if (!enabled.get()) {
			resetState();
			return;
		}

		LocalPlayer player = client.player;
		if (player == null) {
			resetState();
			return;
		}
		if (pauseInMenus.get() && client.gui.screen() != null) {
			return; // menu/chat open: hold state, do not click through screens
		}

		FishingHook hook = player.fishing;
		if (hook != null) {
			recastCountdown = -1;
			boolean biting = hook.getEntityData().get(FishingHookAccessor.axolotlclient$dataBiting());
			if (biting && !reelSent) {
				((MinecraftClientAccessor) client).axolotlclient$startUseItem();
				reelSent = true;
			} else if (!biting && reelSent) {
				// The bite ended without the bobber being removed (fish escaped or the reel
				// was ignored): re-arm so the next bite on this cast still triggers.
				reelSent = false;
			}
		} else if (reelSent) {
			// The bobber entity is gone: the reel completed (catch pulled or line lost).
			reelSent = false;
			recastCountdown = autoRecast.get() ? ticks(nextRecastDelayMs()) : -1;
		} else if (recastCountdown > 0) {
			recastCountdown--;
			if (recastCountdown == 0) {
				recastCountdown = -1;
				if (holdingRod(player)) {
					((MinecraftClientAccessor) client).axolotlclient$startUseItem();
				}
			}
		}
	}

	private static boolean holdingRod(LocalPlayer player) {
		return player.getMainHandItem().getItem() instanceof FishingRodItem
			|| player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof FishingRodItem;
	}

	/** Uniform in [min, max], then ±20% jitter (clamped back into range) so fixed bounds don't metronome. */
	@VisibleForTesting
	public int nextRecastDelayMs() {
		int min = Math.min(delayMin.get(), delayMax.get());
		int max = Math.max(delayMin.get(), delayMax.get());
		int base = min + random.nextInt(max - min + 1);
		double jittered = base * (0.8 + random.nextDouble() * 0.4);
		return Mth.clamp((int) Math.round(jittered), min, max);
	}

	private static int ticks(int ms) {
		return Math.max(1, (ms + 49) / 50);
	}

	private void resetState() {
		reelSent = false;
		recastCountdown = -1;
	}
}
