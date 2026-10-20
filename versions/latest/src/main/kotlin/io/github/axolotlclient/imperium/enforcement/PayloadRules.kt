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

package io.github.axolotlclient.imperium.enforcement

/**
 * Exploit-payload rules (docs/wave4-roadmap.md W4-C): outgoing sign and book payloads are
 * checked locally against the same limits the ImperiumMC plugin enforces server-side, so a
 * player with a broken or misbehaving mod in their folder cannot emit the ban-signature
 * payload in the first place. Constants mirror the plugin's checks exactly (read-only
 * reference, ImperiumMC-Plugin sources):
 *
 *  - `AntiCheatExpansion.MAX_SIGN_LINE_LENGTH = 50` and
 *    `CheatPreventionListener.onSignChange` (line length > 50 cancels + VL,
 *    1 edit / 2 s rate limit).
 *  - `ClientFingerprintListener` UPDATE_SIGN packet layer (joined line length > 500
 *    cancels + VL 12 — the crash-exploit signature).
 *  - `DupePreventionListener.onBookEdit` — title ≤ 16 chars, ≤ 100 pages,
 *    ≤ 8192 chars/page, ≤ 100 non-ASCII chars/page (unicode book-dupe signature).
 */
object PayloadRules {

	/** AntiCheatExpansion.MAX_SIGN_LINE_LENGTH / CheatPreventionListener sign line check. */
	const val SIGN_LINE_MAX = 50

	/** ClientFingerprintListener packet-layer sign payload ceiling (crash-exploit signature). */
	const val SIGN_TOTAL_MAX = 500

	/** CheatPreventionListener.onSignChange rate limit: one sign edit per 2 seconds. */
	const val SIGN_RATE_LIMIT_MS = 2_000L

	/** DupePreventionListener.onBookEdit title limit. */
	const val BOOK_TITLE_MAX = 16

	/** DupePreventionListener.onBookEdit page-count limit. */
	const val BOOK_PAGES_MAX = 100

	/** DupePreventionListener.onBookEdit per-page character limit. */
	const val BOOK_PAGE_CHARS_MAX = 8192

	/** DupePreventionListener.onBookEdit per-page non-ASCII character limit. */
	const val BOOK_PAGE_NON_ASCII_MAX = 100

	/**
	 * Verdict for an outgoing sign packet. Returns null when the payload is legal, otherwise the
	 * rule id that refused it (used for the once-per-session log).
	 */
	@JvmStatic
	fun signViolation(lines: List<String>?): String? {
		if (lines == null || lines.isEmpty()) {
			return null
		}
		var total = 0
		for (line in lines) {
			if (line == null) {
				continue
			}
			val length = line.length
			total += length
			if (length > SIGN_LINE_MAX) {
				return "sign_line_oversize"
			}
		}
		if (total > SIGN_TOTAL_MAX) {
			return "sign_payload_oversize"
		}
		return null
	}

	/**
	 * Rate check mirroring the server's 1-per-2-seconds sign-edit limit: the server cancels a
	 * faster edit's SignChangeEvent anyway, so refusing it locally produces the same outcome for
	 * the player without the server-side bookkeeping.
	 *
	 * @param lastAcceptedMs timestamp of the last sign packet that was allowed through (0 = none)
	 * @param nowMs          current time
	 */
	@JvmStatic
	fun signWithinRate(lastAcceptedMs: Long, nowMs: Long): Boolean =
		lastAcceptedMs <= 0 || nowMs - lastAcceptedMs >= SIGN_RATE_LIMIT_MS

	/**
	 * Verdict for an outgoing book-edit packet (`ServerboundEditBookPacket`). Returns null
	 * when the payload is legal, otherwise the rule id that refused it.
	 */
	@JvmStatic
	fun bookViolation(pages: List<String>?, title: String?): String? {
		if (title != null && title.length > BOOK_TITLE_MAX) {
			return "book_title_oversize"
		}
		if (pages != null) {
			if (pages.size > BOOK_PAGES_MAX) {
				return "book_pages_oversize"
			}
			for (page in pages) {
				if (page == null) {
					continue
				}
				if (page.length > BOOK_PAGE_CHARS_MAX) {
					return "book_page_oversize"
				}
				var nonAscii = 0
				for (i in page.indices) {
					if (page[i].code > 127 && ++nonAscii > BOOK_PAGE_NON_ASCII_MAX) {
						return "book_non_ascii"
					}
				}
			}
		}
		return null
	}
}
