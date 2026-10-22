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

package io.github.axolotlclient.imperium

import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.Minecraft

/**
 * The enforcement half of docs/server-gated-features.md, shared by the gated automation
 * modules (auto_fish, auto_click, auto_walk). Read-only consumer of [ImperiumClientSession]:
 * a gated module is usable only when
 *
 *  - the player is in a singleplayer world (rule 2 — singleplayer counts as granted), or
 *  - the CURRENT server sent an `imperiumclient:features` grant for it this session.
 *
 * Protocol v2 adds the tiered channel: an id may be *allowed* (plain grant),
 * *limited* (usable, but the module must spend from its per-id rate budget — see
 * [ImperiumClientSession.tryConsumeBudget]), or *denied* (force-off).
 * [Tier] resolves an id's current tier; [enforce] maps it onto the module's
 * `enabled` option.
 *
 * [enforce] is called from the module's `tick()`, i.e. before every automation
 * activation, and keeps the module's `enabled` option force-off (with a lock-reason
 * tooltip) while the gate is closed. It only clears a force-off it set itself — one applied
 * by the `block_mods` channel (ban_reason) survives a grant, as it should.
 *
 * QoL modules (auto_eat, auto_tool, auto_armor, auto_reconnect) are a different tier
 * family: they are **allowed by default** (singleplayer-style default ON, v1
 * servers included) and only a server deny force-offs them — see [enforceQoL].
 */
object ServerGating {

	/** Gated module ids from the protocol's table (docs/server-gated-features.md). */
	const val AUTO_FISH = "auto_fish"
	const val AUTO_CLICK = "auto_click"

	/** Limited-tier module id (v2): usable while the server-set rate budget lasts. */
	const val AUTO_WALK = "auto_walk"

	/** QoL module ids (v2): on by default, only a deny force-offs them. */
	const val AUTO_EAT = "auto_eat"
	const val AUTO_TOOL = "auto_tool"
	const val AUTO_ARMOR = "auto_armor"
	const val AUTO_RECONNECT = "auto_reconnect"

	/** QoL tier id (W5): the quick-phrases keybind macros (chat-send path only). */
	const val QUICK_PHRASES = "quick_phrases"

	/** Generic rate-budget lock text for modules that do not carry their own key. */
	const val RATE_LIMITED_KEY = "imperiumclient.gate.rate_limited"

	/** The three v2 tiers an id can resolve to. */
	enum class Tier {
		/** Usable (explicit allow, or singleplayer). */
		GRANTED,

		/** Usable but rate-budgeted — the module must check `tryConsumeBudget` per activation. */
		LIMITED,

		/** Force-off (deny, or gated and ungranted). */
		LOCKED
	}

	/** Resolves the id's current tier against the live session. */
	@JvmStatic
	fun tier(moduleId: String): Tier {
		val session = ImperiumClientSession.getInstance()
		if (session.isDenied(moduleId)) {
			return Tier.LOCKED
		}
		if (Minecraft.getInstance().hasSingleplayerServer() || session.isAllowed(moduleId)) {
			return Tier.GRANTED
		}
		return if (session.isLimited(moduleId)) Tier.LIMITED else Tier.LOCKED
	}

	/** True when the current session permits the gated module (singleplayer or explicit grant). */
	@JvmStatic
	fun granted(moduleId: String): Boolean = tier(moduleId) == Tier.GRANTED

	/**
	 * Applies the gate to a gated module's `enabled` option. Call every tick before
	 * acting: a mid-session re-grant opens the module, a replacement packet without the
	 * grant (or a deny) closes it again. A limited-tier id stays enabled while its rate
	 * budget lasts and force-offs (with `rateReasonKey`) once it is spent.
	 *
	 * @param option        the module's enabled option
	 * @param moduleId      protocol id (`auto_fish` / `auto_click` / `auto_walk`)
	 * @param lockReasonKey translation key shown as the option's tooltip while locked
	 * @param rateReasonKey translation key shown while the limited-tier budget is spent
	 */
	@JvmStatic
	@JvmOverloads
	fun enforce(option: ForceableBooleanOption, moduleId: String, lockReasonKey: String, rateReasonKey: String = RATE_LIMITED_KEY) {
		val session = ImperiumClientSession.getInstance()
		val locked: Boolean
		var reason = lockReasonKey
		when (tier(moduleId)) {
			Tier.GRANTED -> locked = false
			Tier.LIMITED -> {
				locked = !session.hasBudget(moduleId)
				if (locked) {
					reason = rateReasonKey
				}
			}
			Tier.LOCKED -> locked = true
		}
		// LIMITED with budget left: same clearing path as GRANTED.
		val ours = lockReasonKey == option.reason || rateReasonKey == option.reason
		if (locked) {
			// Also re-set when the reason changes (lock <-> rate): the option may already be
			// force-off with our OTHER key from a previous tier.
			if (!option.isForceOff || !ours || reason != option.reason) {
				option.setForceOff(true, reason)
			}
		} else if (option.isForceOff && ours) {
			option.setForceOff(false, "")
		}
	}

	/**
	 * QoL enforcement: these modules ship enabled (the singleplayer-style default) and stay
	 * that way on v1 servers; a v2/v1 deny is the only thing that force-offs them. The same
	 * "only clear our own lock" rule as [enforce] applies.
	 */
	@JvmStatic
	fun enforceQoL(option: ForceableBooleanOption, moduleId: String, lockReasonKey: String) {
		val session = ImperiumClientSession.getInstance()
		val denied = session.isDenied(moduleId)
		val ours = lockReasonKey == option.reason
		if (denied) {
			if (!option.isForceOff || !ours) {
				option.setForceOff(true, lockReasonKey)
			}
		} else if (option.isForceOff && ours) {
			option.setForceOff(false, "")
		}
	}
}
