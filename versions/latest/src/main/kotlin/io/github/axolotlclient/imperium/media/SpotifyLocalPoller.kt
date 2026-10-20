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

package io.github.axolotlclient.imperium.media

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.google.gson.JsonParser
import io.github.axolotlclient.AxolotlClientCommon

/**
 * Best-effort now-playing provider reading the Spotify desktop app's undocumented local status
 * endpoint (W5-B): `GET http://127.0.0.1:<port>/remote/status.json` with an
 * `Origin: https://open.spotify.com` header, polled every 5 seconds while enabled.
 * Loopback only — no OAuth, no cloud, no telemetry; nothing leaves the machine.
 *
 * **Research findings (2026-10-18):** the desktop client binds the local HTTP
 * interface while running, historically on 4381 and shifting up to 4389 when the port is taken;
 * the `Origin` header is required and the response shape matches
 * [SpotifyLocalStatus]. The API is undocumented and Spotify has changed it before, so this
 * poller treats EVERY failure as "hidden, silently": connection refused, wrong shape, timeout —
 * the HUD just stays invisible and one debug line is logged at most once per state change. On
 * the dev machine used for this work Spotify is not installed, so the endpoint could not be
 * exercised live; the guarantee that holds regardless is the [MediaStatusRegistry] shell
 * plus this poller being strictly optional and default-off.
 */
object SpotifyLocalPoller {

	/** Poll cadence pinned by the spec. */
	private const val POLL_INTERVAL_MS = 5_000L

	/** Spotify desktop's local port window (binds 4381, shifts upward when taken). */
	internal const val FIRST_PORT = 4381
	internal const val LAST_PORT = 4389
	private const val STATUS_PATH = "/remote/status.json"
	private const val ORIGIN = "https://open.spotify.com"

	private val running = AtomicBoolean()

	@Volatile
	private var port = -1

	@Volatile
	private var httpClient: HttpClient? = null

	@Volatile
	private var scheduler: ScheduledExecutorService? = null

	@Volatile
	private var lastErrorKind: String? = null

	@JvmStatic
	fun isRunning(): Boolean = running.get()

	/** Idempotent: starts polling iff not already running. Called when the toggle turns on. */
	@Synchronized
	@JvmStatic
	fun start() {
		if (!running.compareAndSet(false, true)) {
			return
		}
		port = -1
		httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(750))
			.executor(pollWorker())
			.build()
		scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
			val thread = Thread(runnable, "imperium-media-poll")
			thread.isDaemon = true
			thread
		}
		scheduler!!.scheduleWithFixedDelay(::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
	}

	/** Idempotent: stops polling and hides the HUD. Called when the toggle turns off. */
	@Synchronized
	@JvmStatic
	fun stop() {
		if (!running.compareAndSet(true, false)) {
			return
		}
		val stopping = scheduler
		scheduler = null
		stopping?.shutdownNow()
		MediaStatusRegistry.clear()
	}

	private fun pollWorker() = Executors.newSingleThreadExecutor { runnable ->
		val thread = Thread(runnable, "imperium-media-http")
		thread.isDaemon = true
		thread
	}

	/** One poll cycle: known port first, then a scan of the window; any failure stays silent. */
	internal fun poll() {
		if (!running.get()) {
			return
		}
		var body = if (port >= 0) fetch(port) else null
		if (body == null) {
			body = scan()
		}
		if (body == null) {
			MediaStatusRegistry.clear()
			failOnce("unreachable")
			return
		}
		val status = parse(body)
		if (status == null) {
			MediaStatusRegistry.clear()
			failOnce("unparseable")
			return
		}
		failOnce(null)
		MediaStatusRegistry.update(status)
	}

	private fun scan(): String? {
		for (candidate in FIRST_PORT..LAST_PORT) {
			val body = fetch(candidate)
			if (body != null) {
				port = candidate
				return body
			}
		}
		return null
	}

	private fun fetch(port: Int): String? {
		val client = httpClient ?: return null
		val request = try {
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$STATUS_PATH"))
				.header("Origin", ORIGIN)
				.timeout(Duration.ofMillis(1500))
				.GET()
				.build()
		} catch (e: IllegalArgumentException) {
			return null // URI misconfiguration would be a bug; fail quiet, not crash
		}
		return try {
			val response = client.send(request, HttpResponse.BodyHandlers.ofString())
			if (response.statusCode() == 200) response.body() else null
		} catch (e: java.io.IOException) {
			null // refused, timed out, reset: hidden, silently
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			null
		}
	}

	/** Parses a status body; `null` on junk. Public for tests. */
	@JvmStatic
	fun parse(body: String?): MediaStatus? {
		return try {
			val document = JsonParser.parseString(body)
			SpotifyLocalStatus.parse(document, System.currentTimeMillis())
		} catch (e: RuntimeException) {
			null // malformed JSON or a parse bug: hide, never crash the poll thread
		}
	}

	/** One debug line per distinct failure kind, so silence in-game but diagnosable in logs. */
	private fun failOnce(kind: String?) {
		val previous = lastErrorKind
		if (if (kind == null) previous == null else kind == previous) {
			return
		}
		lastErrorKind = kind
		if (kind == null) {
			AxolotlClientCommon.getInstance().logger.debug("[media] spotify local status ok on port {}", port)
		} else {
			AxolotlClientCommon.getInstance().logger.debug("[media] spotify local status {} (ports {}-{} checked)", kind, FIRST_PORT, LAST_PORT)
		}
	}
}
