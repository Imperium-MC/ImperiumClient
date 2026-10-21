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

package io.github.axolotlclient.imperium.online

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The fetch layer over api.imperiummc.net's client-token-authorized routes
 * (ImperiumClient#4 endpoint map). Bearer header from [ClientApiAuth], JSON via the
 * existing Gson usage, every call on a worker executor (never the client thread),
 * simple result types — no exceptions cross the API boundary.
 *
 * Route shapes are read from the real backend (imperium-backend/src/api), not guessed:
 *  - GET /api/player/legion (routes.ts) -> {"legion":{name,displayName,level,
 *    ownerUuid,xp,maxMembers}|null,"members":[{uuid,username,role}],"projects":{...}}
 *  - GET /api/player/cosmetics -> {unlocked:[..],activeTrail,activeHat,
 *    activeKillEffect,activeMineEffect} (the nullables really are nullable).
 *  - GET /api/player/balances -> {uuid,balances:{denarius,tokens,beacons,goldenCoins}}.
 *  - GET /api/v2/changelog/list?limit=N (v2/dms.ts) -> {posts:[{title,lead,postedAt,
 *    sections:[{name,changes:[..]}]}]}.
 *
 * 401 handling: one transparent re-auth (invalidate -> re-mint -> single retry),
 * then the failure is reported as [FetchResult.HttpError]; callers never loop.
 */
class ClientApi(
	private val auth: ClientApiAuth,
	private val transport: ClientApiAuth.HttpTransport,
	private val executor: Executor,
) {

	/** One fetch outcome. [HttpError.status] is the last HTTP status seen. */
	sealed interface FetchResult<out T> {
		data class Ok<T>(val value: T) : FetchResult<T>
		data class HttpError(val status: Int) : FetchResult<Nothing>
		object NetworkError : FetchResult<Nothing>
		object AuthUnavailable : FetchResult<Nothing>
		object Malformed : FetchResult<Nothing>
	}

	companion object {
		internal const val PATH_LEGION = "/api/player/legion"
		internal const val PATH_COSMETICS = "/api/player/cosmetics"
		internal const val PATH_BALANCES = "/api/player/balances"
		internal const val PATH_CHANGELOG = "/api/v2/changelog/list"
	}

	// ── data types (backend shapes; parsing is lenient, missing fields default) ────────────

	/** /api/player/legion's `legion` object plus the real member count from `members`. */
	data class LegionSnapshot(
		val name: String,
		val displayName: String?,
		val level: Int,
		val xp: Long,
		val maxMembers: Int,
		val ownerUuid: String?,
		val memberCount: Int,
	)

	/** /api/player/cosmetics (shape contract in routes.ts cosmeticsPayload). */
	data class CosmeticsSnapshot(
		val unlocked: List<String>,
		val activeTrail: String?,
		val activeHat: String?,
		val activeKillEffect: String?,
		val activeMineEffect: String?,
	)

	/** /api/player/balances' `balances` map (denarius/tokens/beacons/goldenCoins). */
	data class BalancesSnapshot(
		val denarius: Double,
		val tokens: Double,
		val beacons: Double,
		val goldenCoins: Double,
	)

	/**
	 * /api/v2/changelog/list post entry. Ready-to-use surface only — the title screen's
	 * imperiummc.net/news.rss stays THE news display (do not duplicate it this pass).
	 */
	data class ChangelogPost(
		val title: String,
		val lead: String?,
		val postedAt: String?,
		val sections: List<Section>,
	) {
		data class Section(val name: String, val changes: List<String>)
	}

	// ── endpoints ───────────────────────────────────────────────────────────────────────────

	/**
	 * The player's legion per the API. Consumer: [ImperiumOnline] feeds it into
	 * LegionStateRegistry as a FALLBACK only when the legion channel does not own the
	 * session (not connected to ImperiumMC, registry empty).
	 */
	fun fetchLegion(): CompletableFuture<FetchResult<LegionSnapshot>> =
		supply(PATH_LEGION) { parseLegion(it) }

	/** Cosmetic ownership snapshot. Ready-to-use authenticated GET — the cosmetics
	 * channel design is owner-gated, so no consumer/UI is wired this pass. */
	fun fetchCosmetics(): CompletableFuture<FetchResult<CosmeticsSnapshot>> =
		supply(PATH_COSMETICS) { parseCosmetics(it) }

	/** Wallet balances. Fetch function only — deliberately nothing visual this pass. */
	fun fetchBalances(): CompletableFuture<FetchResult<BalancesSnapshot>> =
		supply(PATH_BALANCES) { parseBalances(it) }

	/** The posted changelog record. See [ChangelogPost] — nothing displays it this pass. */
	fun fetchChangelog(limit: Int = 5): CompletableFuture<FetchResult<List<ChangelogPost>>> =
		supply(PATH_CHANGELOG + "?limit=" + limit.coerceIn(1, 25)) { parseChangelog(it) }

	// ── plumbing ────────────────────────────────────────────────────────────────────────────

	private fun <T> supply(
		path: String,
		parse: (String) -> T?,
	): CompletableFuture<FetchResult<T>> = CompletableFuture.supplyAsync({
		when (val raw = authorizedGetSync(path)) {
			is RawFetch.Body -> {
				val parsed = try {
					parse(raw.body)
				} catch (ignored: Exception) {
					null
				}
				if (parsed == null) {
					FetchResult.Malformed
				} else {
					FetchResult.Ok(parsed)
				}
			}
			is RawFetch.HttpError -> FetchResult.HttpError(raw.status)
			RawFetch.NetworkError -> FetchResult.NetworkError
			RawFetch.AuthUnavailable -> FetchResult.AuthUnavailable
		}
	}, executor)

	/**
	 * Authorized GET with the 401 refresh: mint/wait token -> GET -> on 401 invalidate,
	 * re-mint once, retry once. Runs on the executor (blocking [ClientApiAuth.awaitToken]
	 * is a worker-thread join, never the client thread).
	 */
	private fun authorizedGetSync(path: String): RawFetch {
		val url = ClientApiAuth.API_BASE + path
		val token = auth.awaitToken() ?: return RawFetch.AuthUnavailable
		return when (val first = transport.get(url, token)) {
			is ClientApiAuth.HttpResult.TransportError -> RawFetch.NetworkError
			is ClientApiAuth.HttpResult.Response -> when {
				first.status == 200 -> RawFetch.Body(first.body ?: "")
				first.status == 401 -> {
					auth.invalidate()
					val fresh = auth.awaitToken() ?: return RawFetch.AuthUnavailable
					when (val second = transport.get(url, fresh)) {
						is ClientApiAuth.HttpResult.Response ->
							if (second.status == 200) {
								RawFetch.Body(second.body ?: "")
							} else {
								RawFetch.HttpError(second.status)
							}
						is ClientApiAuth.HttpResult.TransportError -> RawFetch.NetworkError
					}
				}
				else -> RawFetch.HttpError(first.status)
			}
		}
	}

	/** Non-generic raw outcome of one authorized GET (the generic [FetchResult] is built above). */
	private sealed interface RawFetch {
		data class Body(val body: String) : RawFetch
		data class HttpError(val status: Int) : RawFetch
		object NetworkError : RawFetch
		object AuthUnavailable : RawFetch
	}

	// ── parsers (lenient like ImperiumChannels: wrong shape -> null -> Malformed) ─────────

	private fun parseLegion(body: String): LegionSnapshot? {
		val root = obj(body) ?: return null
		val legion = root.get("legion")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
		val name = legion.string("name") ?: return null
		val members = root.get("members")?.takeIf { it.isJsonArray }?.asJsonArray
		return LegionSnapshot(
			name = name,
			displayName = legion.string("displayName"),
			level = legion.intOr("level", 0),
			xp = legion.longOr("xp", 0L),
			maxMembers = legion.intOr("maxMembers", 0),
			ownerUuid = legion.string("ownerUuid"),
			memberCount = members?.size() ?: 0,
		)
	}

	private fun parseCosmetics(body: String): CosmeticsSnapshot? {
		val root = obj(body) ?: return null
		val unlocked = ArrayList<String>()
		root.get("unlocked")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
			if (element.isJsonPrimitive) {
				unlocked.add(element.asString)
			}
		}
		return CosmeticsSnapshot(
			unlocked = unlocked,
			activeTrail = root.string("activeTrail"),
			activeHat = root.string("activeHat"),
			activeKillEffect = root.string("activeKillEffect"),
			activeMineEffect = root.string("activeMineEffect"),
		)
	}

	private fun parseBalances(body: String): BalancesSnapshot? {
		val root = obj(body) ?: return null
		val balances = root.get("balances")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
		return BalancesSnapshot(
			denarius = balances.doubleOr("denarius", 0.0),
			tokens = balances.doubleOr("tokens", 0.0),
			beacons = balances.doubleOr("beacons", 0.0),
			goldenCoins = balances.doubleOr("goldenCoins", 0.0),
		)
	}

	private fun parseChangelog(body: String): List<ChangelogPost>? {
		val root = obj(body) ?: return null
		val posts = root.get("posts")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
		val out = ArrayList<ChangelogPost>(posts.size())
		for (element in posts) {
			if (!element.isJsonObject) {
				continue
			}
			val post = element.asJsonObject
			val title = post.string("title") ?: continue
			val sections = ArrayList<ChangelogPost.Section>()
			post.get("sections")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { sectionElement ->
				if (!sectionElement.isJsonObject) {
					return@forEach
				}
				val section = sectionElement.asJsonObject
				val sectionName = section.string("name") ?: return@forEach
				val changes = ArrayList<String>()
				section.get("changes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { change ->
					if (change.isJsonPrimitive) {
						changes.add(change.asString)
					}
				}
				sections.add(ChangelogPost.Section(sectionName, changes))
			}
			out.add(ChangelogPost(title, post.string("lead"), post.string("postedAt"), sections))
		}
		return out
	}

	private fun obj(body: String): JsonObject? = try {
		val parsed = JsonParser.parseString(body)
		if (parsed.isJsonObject) parsed.asJsonObject else null
	} catch (ignored: Exception) {
		null
	}

	private fun JsonObject.string(key: String): String? =
		get(key)?.takeIf { it.isJsonPrimitive }?.asString

	private fun JsonObject.intOr(key: String, default: Int): Int =
		get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: default

	private fun JsonObject.longOr(key: String, default: Long): Long =
		get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: default

	private fun JsonObject.doubleOr(key: String, default: Double): Double =
		get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() } ?: default
}
