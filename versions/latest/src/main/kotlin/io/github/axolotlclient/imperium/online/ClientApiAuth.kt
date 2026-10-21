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

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.concurrent.withLock

/**
 * The client half of the Mojang-session-check sign-in against api.imperiummc.net
 * (backend issue #2): challenge -> the GAME's own session service joins the issued
 * serverId -> verify -> a bearer JWT for the client-token-authorized player routes.
 *
 * The exact join call is the one vanilla itself makes during server login
 * (ClientHandshakePacketListenerImpl.authenticateServer on 26.3):
 *
 * `Minecraft.getInstance().services().sessionService()`
 * `.joinServer(user.getProfileId(), user.getAccessToken(), serverId)`
 *
 * Design rules (owner-set, ImperiumClient#4):
 *  - The token lives IN MEMORY ONLY. It is never written to config/disk and never
 *    logged (not even partially); failure reasons are opaque codes, not bodies.
 *  - ANY failure means "online features unavailable" and nothing else. Auth never
 *    blocks play, throws into game code, or retries in a loop.
 *  - Offline/dev accounts ([GameSession.hasOnlineSession] false) skip gracefully.
 *  - [invalidate] is the refresh seam: called on 401 (token expired) and when the
 *    game session changes (account switch / relog). The next [authenticateAsync]
 *    mints a fresh token.
 *
 * Testability: both external effects (HTTP and joinServer) are interfaces, so the
 * whole state machine runs in the gametest against in-memory fakes — see
 * ClientAuthGameTest. This class itself touches no Minecraft or network code.
 *
 * Threading: all public entry points may be called from any thread. The flow runs
 * on the injected [Executor]; the state field is guarded by [lock]. Virtual-thread
 * friendly (no `synchronized`, no main-thread joins).
 */
class ClientApiAuth(
	private val transport: HttpTransport,
	private val session: GameSession,
	private val executor: Executor,
) {

	companion object {

		/**
		 * ROMANICA CUTOVER: at the domain flip this becomes https://api.romanica.net.
		 * Until then every online call stays on imperiummc.net (cutover-gated).
		 */
		const val API_BASE = "https://api.imperiummc.net"

		/** POST /api/client/auth/challenge -> {"serverId":..,"expiresIn":..} (backend #2). */
		internal const val PATH_CHALLENGE = "/api/client/auth/challenge"

		/** POST /api/client/auth/verify {"username":..,"serverId":..} -> {"token":..}. */
		internal const val PATH_VERIFY = "/api/client/auth/verify"
	}

	/** The HTTP seam. Synchronous; implementations must be safe to call on a worker thread. */
	interface HttpTransport {

		/**
		 * POST JSON. [bearerToken] is null for the auth endpoints themselves (challenge/
		 * verify are unauthenticated) and the raw JWT for authorized calls — it must
		 * never be logged.
		 */
		fun post(url: String, body: String, bearerToken: String?): HttpResult

		/** GET, optionally bearer-authorized. */
		fun get(url: String, bearerToken: String?): HttpResult
	}

	/** One HTTP exchange outcome; [TransportError] carries a cause code, never a body. */
	sealed interface HttpResult {
		data class Response(val status: Int, val body: String?) : HttpResult
		data class TransportError(val cause: String) : HttpResult
	}

	/**
	 * The game's session, as the state machine needs it. The production adapter reads
	 * `Minecraft.getInstance()`; tests fake it. [joinServer] runs the game's own
	 * `SessionService.joinServer(profileId, accessToken, serverId)` and throws whatever
	 * authlib throws (AuthenticationUnavailableException, ...).
	 */
	interface GameSession {
		/** True only for a real Microsoft session; offline/dev accounts skip auth. */
		val hasOnlineSession: Boolean
		val profileId: UUID?
		val username: String?
		val accessToken: String?
		fun joinServer(serverId: String)
	}

	/** Observable machine state. [Failed.reason] and [Authenticated.token] are codes/values, safe to expose. */
	sealed class State {
		object NotStarted : State()
		object OfflineSession : State()
		object Authenticating : State()
		class Authenticated internal constructor(val token: String) : State()
		class Failed internal constructor(val reason: String) : State()
	}

	private val lock = ReentrantLock()

	/** Bumped by [invalidate]; a flow from an older generation never publishes. */
	private val generation = AtomicLong()

	private var state: State = State.NotStarted

	/** The (profileId, accessToken) pair the current token was minted for. */
	private var mintedFor: Pair<UUID?, String?>? = null

	private var inFlight: CompletableFuture<State>? = null

	/** Current state snapshot (any thread). */
	fun state(): State = lock.withLock { state }

	/** The bearer token, or null when not authenticated. Memory-only by contract. */
	fun token(): String? = (state() as? State.Authenticated)?.token

	/**
	 * The (profileId, accessToken) the token was minted for, or null. Callers compare
	 * this against the live session to detect account switches / token refreshes.
	 */
	fun authenticatedFor(): Pair<UUID?, String?>? = lock.withLock {
		if (state is State.Authenticated) mintedFor else null
	}

	/**
	 * Drops the token (401/expiry or session change) so the next [authenticateAsync]
	 * re-mints. A flow already in flight from the old generation is discarded: its
	 * late result is returned to its own caller but never published as state.
	 */
	fun invalidate() {
		lock.withLock {
			generation.incrementAndGet()
			state = State.NotStarted
			mintedFor = null
			inFlight = null
		}
	}

	/**
	 * Runs (or joins) the challenge -> join -> verify flow on the executor.
	 * Single-flight: concurrent callers share one attempt. Returns the terminal
	 * state of THIS attempt (which may be superseded by a later invalidate).
	 */
	fun authenticateAsync(): CompletableFuture<State> {
		var started: CompletableFuture<State>? = null
		var gen = -1L
		val joined = lock.withLock {
			when (val current = state) {
				is State.Authenticated -> return CompletableFuture.completedFuture(current)
				is State.Authenticating -> inFlight
				else -> {
					state = State.Authenticating
					gen = generation.get()
					val pending = CompletableFuture<State>()
					inFlight = pending
					started = pending
					null
				}
			}
		}
		started?.let { flow ->
			// Started OUTSIDE the lock on purpose: a synchronous (direct) executor runs
			// the flow right here, and running it inside withLock would hold the lock
			// for the whole flow — invalidate() from another thread could then never
			// win against an in-flight flow.
			flow.completeAsync({ runFlow(gen) }, executor)
			return flow
		}
		return joined ?: CompletableFuture.completedFuture(State.NotStarted)
	}

	/**
	 * Blocking form of the token, for worker threads only (never the client thread):
	 * returns the current token, minting one first when absent. Null when the session
	 * is offline or auth failed — callers degrade, they do not retry-loop.
	 */
	fun awaitToken(): String? {
		val existing = token()
		if (existing != null) {
			return existing
		}
		return try {
			(authenticateAsync().join() as? State.Authenticated)?.token
		} catch (ignored: Exception) {
			null
		}
	}

	private fun runFlow(gen: Long): State {
		val outcome = try {
			doFlow()
		} catch (e: Exception) {
			FlowOutcome(State.Failed("unexpected"), null)
		}
		return lock.withLock {
			if (gen != generation.get()) {
				// Superseded by invalidate() mid-flow: never publish (or keep) this token.
				State.Failed("superseded")
			} else {
				state = outcome.state
				mintedFor = outcome.mintedFor
				inFlight = null
				outcome.state
			}
		}
	}

	/** Terminal state plus the session pair a successful mint must be recorded against. */
	private data class FlowOutcome(val state: State, val mintedFor: Pair<UUID?, String?>?)

	private fun doFlow(): FlowOutcome {
		val profileId = session.profileId
		val accessToken = session.accessToken
		if (!session.hasOnlineSession) {
			return FlowOutcome(State.OfflineSession, null)
		}
		val username = session.username ?: return FlowOutcome(State.Failed("no-username"), null)

		// 1. Challenge: the API issues a one-shot serverId for this sign-in.
		val challenge = when (val r = transport.post(API_BASE + PATH_CHALLENGE, "{}", null)) {
			is HttpResult.TransportError -> return FlowOutcome(State.Failed("challenge-network"), null)
			is HttpResult.Response -> r
		}
		if (challenge.status != 200) {
			return FlowOutcome(State.Failed("challenge-http-" + challenge.status), null)
		}
		val serverId = runCatching { parseJsonObject(challenge.body) }
			.getOrNull()
			 ?.get("serverId")
			?.takeIf { it.isJsonPrimitive }
			?.asString
			?.takeIf { it.isNotBlank() }
			?: return FlowOutcome(State.Failed("challenge-shape"), null)

		// 2. joinServer on the game's own session service. Vanilla replaces the Mojang
		//    join record on the next real server login, so this never disturbs play
		//    (and the game's own login overwrites ours, never the reverse in-flight).
		try {
			session.joinServer(serverId)
		} catch (e: Exception) {
			return FlowOutcome(State.Failed("join-server"), null)
		}

		// 3. Verify: the backend checks hasJoinedServer(username, serverId) against
		//    Mojang's session servers and mints the bearer JWT.
		val verifyBody = JsonObject().apply {
			addProperty("username", username)
			addProperty("serverId", serverId)
		}
		val verify = when (val r = transport.post(API_BASE + PATH_VERIFY, verifyBody.toString(), null)) {
			is HttpResult.TransportError -> return FlowOutcome(State.Failed("verify-network"), null)
			is HttpResult.Response -> r
		}
		if (verify.status != 200) {
			return FlowOutcome(State.Failed("verify-http-" + verify.status), null)
		}
		val token = runCatching { parseJsonObject(verify.body) }
			.getOrNull()
			?.get("token")
			?.takeIf { it.isJsonPrimitive }
			?.asString
			?.takeIf { it.isNotBlank() }
			?: return FlowOutcome(State.Failed("verify-shape"), null)

		return FlowOutcome(State.Authenticated(token), Pair(profileId, accessToken))
	}

	private fun parseJsonObject(body: String?): JsonObject? {
		if (body == null) {
			return null
		}
		val parsed = JsonParser.parseString(body)
		return if (parsed.isJsonObject) parsed.asJsonObject else null
	}
}
