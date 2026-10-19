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

package io.github.axolotlclient.imperium;

import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.Minecraft;

/**
 * The enforcement half of docs/server-gated-features.md, shared by the gated automation
 * modules (auto_fish, auto_click, auto_walk). Read-only consumer of {@link ImperiumClientSession}:
 * a gated module is usable only when
 *
 * <ul>
 *   <li>the player is in a singleplayer world (rule 2 — singleplayer counts as granted), or</li>
 *   <li>the CURRENT server sent an {@code imperiumclient:features} grant for it this session.</li>
 * </ul>
 *
 * <p>Protocol v2 adds the tiered channel: an id may be <em>allowed</em> (plain grant),
 * <em>limited</em> (usable, but the module must spend from its per-id rate budget — see
 * {@link ImperiumClientSession#tryConsumeBudget(String)}), or <em>denied</em> (force-off).
 * {@link Tier} resolves an id's current tier; {@link #enforce} maps it onto the module's
 * {@code enabled} option.</p>
 *
 * <p>{@link #enforce} is called from the module's {@code tick()}, i.e. before every automation
 * activation, and keeps the module's {@code enabled} option force-off (with a lock-reason
 * tooltip) while the gate is closed. It only clears a force-off it set itself — one applied
 * by the {@code block_mods} channel (ban_reason) survives a grant, as it should.</p>
 *
 * <p>QoL modules (auto_eat, auto_tool, auto_armor, auto_reconnect) are a different tier
 * family: they are <strong>allowed by default</strong> (singleplayer-style default ON, v1
 * servers included) and only a server deny force-offs them — see {@link #enforceQoL}.</p>
 */
public final class ServerGating {

	/** Gated module ids from the protocol's table (docs/server-gated-features.md). */
	public static final String AUTO_FISH = "auto_fish";
	public static final String AUTO_CLICK = "auto_click";
	/** Limited-tier module id (v2): usable while the server-set rate budget lasts. */
	public static final String AUTO_WALK = "auto_walk";
	/** QoL module ids (v2): on by default, only a deny force-offs them. */
	public static final String AUTO_EAT = "auto_eat";
	public static final String AUTO_TOOL = "auto_tool";
	public static final String AUTO_ARMOR = "auto_armor";
	public static final String AUTO_RECONNECT = "auto_reconnect";
	/** QoL tier id (W5): the quick-phrases keybind macros (chat-send path only). */
	public static final String QUICK_PHRASES = "quick_phrases";

	/** Generic rate-budget lock text for modules that do not carry their own key. */
	public static final String RATE_LIMITED_KEY = "imperiumclient.gate.rate_limited";

	/** The three v2 tiers an id can resolve to. */
	public enum Tier {
		/** Usable (explicit allow, or singleplayer). */
		GRANTED,
		/** Usable but rate-budgeted — the module must check {@code tryConsumeBudget} per activation. */
		LIMITED,
		/** Force-off (deny, or gated and ungranted). */
		LOCKED
	}

	private ServerGating() {
	}

	/** Resolves the id's current tier against the live session. */
	public static Tier tier(String moduleId) {
		ImperiumClientSession session = ImperiumClientSession.getInstance();
		if (session.isDenied(moduleId)) {
			return Tier.LOCKED;
		}
		if (Minecraft.getInstance().hasSingleplayerServer() || session.isAllowed(moduleId)) {
			return Tier.GRANTED;
		}
		return session.isLimited(moduleId) ? Tier.LIMITED : Tier.LOCKED;
	}

	/** True when the current session permits the gated module (singleplayer or explicit grant). */
	public static boolean granted(String moduleId) {
		return tier(moduleId) == Tier.GRANTED;
	}

	/**
	 * Applies the gate to a gated module's {@code enabled} option. Call every tick before
	 * acting: a mid-session re-grant opens the module, a replacement packet without the
	 * grant (or a deny) closes it again. A limited-tier id stays enabled while its rate
	 * budget lasts and force-offs (with {@code rateReasonKey}) once it is spent.
	 *
	 * @param option          the module's enabled option
	 * @param moduleId        protocol id ({@code auto_fish} / {@code auto_click} / {@code auto_walk})
	 * @param lockReasonKey   translation key shown as the option's tooltip while locked
	 * @param rateReasonKey   translation key shown while the limited-tier budget is spent
	 */
	public static void enforce(ForceableBooleanOption option, String moduleId, String lockReasonKey, String rateReasonKey) {
		ImperiumClientSession session = ImperiumClientSession.getInstance();
		boolean locked;
		String reason = lockReasonKey;
		switch (tier(moduleId)) {
			case GRANTED -> locked = false;
			case LIMITED -> {
				locked = !session.hasBudget(moduleId);
				if (locked) {
					reason = rateReasonKey;
				}
			}
			default -> locked = true;
		}
		// LIMITED with budget left: same clearing path as GRANTED.
		boolean ours = lockReasonKey.equals(option.getReason()) || rateReasonKey.equals(option.getReason());
		if (locked) {
			// Also re-set when the reason changes (lock <-> rate): the option may already be
			// force-off with our OTHER key from a previous tier.
			if (!option.isForceOff() || !ours || !reason.equals(option.getReason())) {
				option.setForceOff(true, reason);
			}
		} else if (option.isForceOff() && ours) {
			option.setForceOff(false, "");
		}
	}

	/** Three-arg form kept for the v1 gated modules; the rate-budget lock text is the generic key. */
	public static void enforce(ForceableBooleanOption option, String moduleId, String lockReasonKey) {
		enforce(option, moduleId, lockReasonKey, RATE_LIMITED_KEY);
	}

	/**
	 * QoL enforcement: these modules ship enabled (the singleplayer-style default) and stay
	 * that way on v1 servers; a v2/v1 deny is the only thing that force-offs them. The same
	 * "only clear our own lock" rule as {@link #enforce} applies.
	 */
	public static void enforceQoL(ForceableBooleanOption option, String moduleId, String lockReasonKey) {
		ImperiumClientSession session = ImperiumClientSession.getInstance();
		boolean denied = session.isDenied(moduleId);
		boolean ours = lockReasonKey.equals(option.getReason());
		if (denied) {
			if (!option.isForceOff() || !ours) {
				option.setForceOff(true, lockReasonKey);
			}
		} else if (option.isForceOff() && ours) {
			option.setForceOff(false, "");
		}
	}
}
