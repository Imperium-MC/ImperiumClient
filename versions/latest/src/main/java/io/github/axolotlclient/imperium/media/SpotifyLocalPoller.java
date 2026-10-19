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

package io.github.axolotlclient.imperium.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.axolotlclient.AxolotlClientCommon;
import org.jetbrains.annotations.Nullable;

/**
 * Best-effort now-playing provider reading the Spotify desktop app's undocumented local status
 * endpoint (W5-B): {@code GET http://127.0.0.1:<port>/remote/status.json} with an
 * {@code Origin: https://open.spotify.com} header, polled every 5 seconds while enabled.
 * Loopback only — no OAuth, no cloud, no telemetry; nothing leaves the machine.
 *
 * <p><strong>Research findings (2026-10-18):</strong> the desktop client binds the local HTTP
 * interface while running, historically on 4381 and shifting up to 4389 when the port is taken;
 * the {@code Origin} header is required and the response shape matches
 * {@link SpotifyLocalStatus}. The API is undocumented and Spotify has changed it before, so this
 * poller treats EVERY failure as "hidden, silently": connection refused, wrong shape, timeout —
 * the HUD just stays invisible and one debug line is logged at most once per state change. On
 * the dev machine used for this work Spotify is not installed, so the endpoint could not be
 * exercised live; the guarantee that holds regardless is the {@link MediaStatusRegistry} shell
 * plus this poller being strictly optional and default-off.</p>
 */
public final class SpotifyLocalPoller {

	/** Poll cadence pinned by the spec. */
	private static final long POLL_INTERVAL_MS = 5_000;
	/** Spotify desktop's local port window (binds 4381, shifts upward when taken). */
	static final int FIRST_PORT = 4381, LAST_PORT = 4389;
	private static final String STATUS_PATH = "/remote/status.json";
	private static final String ORIGIN = "https://open.spotify.com";

	private static final AtomicBoolean running = new AtomicBoolean();

	private static volatile int port = -1;
	private static volatile HttpClient httpClient;
	private static volatile ScheduledExecutorService scheduler;

	@Nullable
	private static volatile String lastErrorKind;

	private SpotifyLocalPoller() {
	}

	public static boolean isRunning() {
		return running.get();
	}

	/** Idempotent: starts polling iff not already running. Called when the toggle turns on. */
	public static synchronized void start() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		port = -1;
		httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(750))
			.executor(pollWorker())
			.build();
		scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "imperium-media-poll");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleWithFixedDelay(SpotifyLocalPoller::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	/** Idempotent: stops polling and hides the HUD. Called when the toggle turns off. */
	public static synchronized void stop() {
		if (!running.compareAndSet(true, false)) {
			return;
		}
		ScheduledExecutorService stopping = scheduler;
		scheduler = null;
		if (stopping != null) {
			stopping.shutdownNow();
		}
		MediaStatusRegistry.clear();
	}

	private static ExecutorService pollWorker() {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "imperium-media-http");
			thread.setDaemon(true);
			return thread;
		});
	}

	/** One poll cycle: known port first, then a scan of the window; any failure stays silent. */
	static void poll() {
		if (!running.get()) {
			return;
		}
		String body = port >= 0 ? fetch(port) : null;
		if (body == null) {
			body = scan();
		}
		if (body == null) {
			MediaStatusRegistry.clear();
			failOnce("unreachable");
			return;
		}
		MediaStatus status = parse(body);
		if (status == null) {
			MediaStatusRegistry.clear();
			failOnce("unparseable");
			return;
		}
		failOnce(null);
		MediaStatusRegistry.update(status);
	}

	@Nullable
	private static String scan() {
		for (int candidate = FIRST_PORT; candidate <= LAST_PORT; candidate++) {
			String body = fetch(candidate);
			if (body != null) {
				port = candidate;
				return body;
			}
		}
		return null;
	}

	@Nullable
	private static String fetch(int port) {
		HttpClient client = httpClient;
		if (client == null) {
			return null;
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + STATUS_PATH))
			.header("Origin", ORIGIN)
			.timeout(Duration.ofMillis(1500))
			.GET()
			.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200 ? response.body() : null;
		} catch (IOException e) {
			return null; // refused, timed out, reset: hidden, silently
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (IllegalArgumentException e) {
			return null; // URI misconfiguration would be a bug; fail quiet, not crash
		}
	}

	/** Parses a status body; {@code null} on junk. Public for tests. */
	@Nullable
	public static MediaStatus parse(String body) {
		try {
			JsonElement document = JsonParser.parseString(body);
			return SpotifyLocalStatus.parse(document, System.currentTimeMillis());
		} catch (RuntimeException e) {
			return null; // malformed JSON or a parse bug: hide, never crash the poll thread
		}
	}

	/** One debug line per distinct failure kind, so silence in-game but diagnosable in logs. */
	private static void failOnce(@Nullable String kind) {
		String previous = lastErrorKind;
		if (kind == null ? previous == null : kind.equals(previous)) {
			return;
		}
		lastErrorKind = kind;
		if (kind == null) {
			AxolotlClientCommon.getInstance().getLogger().debug("[media] spotify local status ok on port {}", port);
		} else {
			AxolotlClientCommon.getInstance().getLogger().debug("[media] spotify local status {} (ports {}-{} checked)", kind, FIRST_PORT, LAST_PORT);
		}
	}
}
