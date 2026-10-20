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

package io.github.axolotlclient.util;

import io.github.axolotlclient.AxolotlClientConfigCommon.FrameLimiterMode;

/**
 * Pure state machine behind the {@code performance} category's frame limiter (M3), which
 * replaces Dynamic FPS. Kept free of Minecraft types so the cap decision is unit-testable.
 *
 * <p>Semantics:
 * <ul>
 * <li>{@link FrameLimiterMode#OFF} disables every cap — nothing of ours touches the framerate.</li>
 * <li>{@link FrameLimiterMode#MINIMIZED} only ever caps while the window is minimized;
 * {@link FrameLimiterMode#UNFOCUSED} caps while the window is minimized or unfocused.</li>
 * <li>While the mode's window condition holds, the applicable cap is the <em>strictest</em> of:
 * the background cap (always applicable), the AFK cap (idle ≥ {@code afkDelay} seconds) and the
 * long-AFK cap (idle ≥ {@code longAfkDelay} seconds). Idle time accumulates without input,
 * using the same input signals vanilla's inactivity tracker uses.</li>
 * <li>The result is never below 1 FPS.</li>
 * </ul>
 *
 * <p>Vanilla interplay: {@link #applyCap} only lowers a limit, never raises it, so vanilla's
 * own inactivity throttle (or the user's max framerate) always wins when it is stricter —
 * ours applies exactly when the vanilla option is off or our cap is stricter, and the two
 * never race.
 */
public final class FrameLimiter {

	/**
	 * Sentinel for "no cap of ours applies".
	 */
	public static final int NO_CAP = 0;

	private static volatile long lastInputMillis;
	private static volatile boolean hasInput;

	private FrameLimiter() {
	}

	/**
	 * Record an input event (any mouse move / key press, i.e. the same signals vanilla's
	 * inactivity tracker consumes). The clock is the caller's: always pass readings from
	 * one and the same monotonic source (the game uses {@code Util.getMillis()}, which is
	 * nanoTime-based, not epoch time).
	 */
	public static void noteInput(long nowMillis) {
		lastInputMillis = nowMillis;
		hasInput = true;
	}

	/**
	 * Milliseconds elapsed since the last {@link #noteInput}, never negative. Before any
	 * input was ever noted the idle time starts at zero, so a freshly started game is
	 * never considered AFK right away.
	 */
	public static long idleMillis(long nowMillis) {
		if (!hasInput) {
			lastInputMillis = nowMillis;
			hasInput = true;
			return 0L;
		}
		return Math.max(0L, nowMillis - lastInputMillis);
	}

	/**
	 * The cap this limiter wants, in FPS, or {@link #NO_CAP} when none applies.
	 *
	 * @param mode             the limiter mode (OFF gates everything)
	 * @param minimized        whether the window is currently iconified/minimized
	 * @param focused          whether the window currently has focus
	 * @param idleMillis       input idle time in milliseconds (see {@link #idleMillis})
	 * @param backgroundFps    cap applied while the mode's window condition holds
	 * @param afkFps           cap applied once idle reaches {@code afkDelaySeconds}
	 * @param afkDelaySeconds  idle time before the AFK cap applies
	 * @param longAfkFps       cap applied once idle reaches {@code longAfkDelaySeconds}
	 * @param longAfkDelaySeconds idle time before the long-AFK cap applies
	 */
	public static int capFor(FrameLimiterMode mode, boolean minimized, boolean focused, long idleMillis,
		int backgroundFps, int afkFps, int afkDelaySeconds, int longAfkFps, int longAfkDelaySeconds) {
		if (mode == null || !mode.applies(minimized, focused)) {
			return NO_CAP;
		}
		// The background cap is the baseline; the AFK tiers only ever tighten it further.
		int cap = atLeastOne(backgroundFps);
		if (idleMillis >= secondsToMillis(afkDelaySeconds)) {
			cap = Math.min(cap, atLeastOne(afkFps));
		}
		if (idleMillis >= secondsToMillis(longAfkDelaySeconds)) {
			cap = Math.min(cap, atLeastOne(longAfkFps));
		}
		return cap;
	}

	/**
	 * Combine a limit computed by the game (vanilla inactivity throttle, the user's max
	 * framerate, menu throttle...) with our own cap. Ours applies only when it is stricter;
	 * the result never drops below 1 FPS and never exceeds {@code vanillaLimit}, so the
	 * built-in limiter can only ever lower a framerate vanilla would already allow.
	 */
	public static int applyCap(int vanillaLimit, int ownCap) {
		if (vanillaLimit <= 0 || ownCap <= NO_CAP || ownCap >= vanillaLimit) {
			return vanillaLimit;
		}
		return Math.max(1, ownCap);
	}

	private static int atLeastOne(int fps) {
		return Math.max(1, fps);
	}

	private static long secondsToMillis(int seconds) {
		return seconds * 1000L;
	}
}
