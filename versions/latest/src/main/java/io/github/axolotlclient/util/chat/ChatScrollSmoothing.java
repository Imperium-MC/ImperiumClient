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

package io.github.axolotlclient.util.chat;

import org.jetbrains.annotations.VisibleForTesting;

/**
 * Pure math behind smooth chat (the {@code smooth_chat} option): the chat's scroll offset
 * approaches its target through a frame-time-aware exponential lerp instead of snapping.
 *
 * <p>Kept free of any Minecraft classes so the interpolation is unit-testable: the render
 * hook in {@code ChatHudMixin} feeds wall-clock deltas (the render path runs per frame, not
 * per tick, so partial ticks are inherent in the sampling rate) and renders at the returned
 * fractional position.</p>
 *
 * <p>The approach is exponential: {@code remaining = exp(-dt / tau)} with
 * {@code tau = duration / 3}, so a {@code duration} of 120 ms is ~95% settled at 120 ms and
 * visually finished shortly after. Exponential decay cannot overshoot, so the chat never
 * bounces past its target.</p>
 */
public final class ChatScrollSmoothing {

	/** Below this absolute distance the animation snaps to the target (sub-pixel anyway). */
	@VisibleForTesting
	public static final float SNAP_THRESHOLD = 0.01F;

	/** Clamp for one step's dt: after a pause (menu, lag spike) the chat jumps instead of crawling. */
	@VisibleForTesting
	public static final long MAX_STEP_MILLIS = 250L;

	private ChatScrollSmoothing() {
	}

	/**
	 * Advances the animated scroll position one frame toward the target.
	 *
	 * @param current         the current fractional scroll position (in lines, may be negative
	 *                        while a just-arrived message slides in)
	 * @param target          the scroll position to settle at (vanilla's authoritative value)
	 * @param dtMillis        milliseconds since the previous frame; clamped to {@link #MAX_STEP_MILLIS}
	 * @param durationMillis  the configured settle window (50-400 ms); roughly 95% complete at
	 *                        {@code durationMillis} after a change
	 * @return the new fractional scroll position
	 */
	public static float step(float current, float target, long dtMillis, int durationMillis) {
		if (Math.abs(target - current) <= SNAP_THRESHOLD) {
			return target;
		}
		if (dtMillis <= 0L) {
			return current; // same frame sampled twice (background + text pass): idempotent
		}
		long dt = Math.min(dtMillis, MAX_STEP_MILLIS);
		float tau = Math.max(durationMillis, 1) / 3.0F;
		float remaining = (float) Math.exp(-dt / tau);
		return target + (current - target) * remaining;
	}

	/**
	 * Whether the animation is close enough to be worth rendering fractionally. When false the
	 * render path skips the fractional offset entirely (pure vanilla rendering).
	 */
	public static boolean animating(float current, float target) {
		return Math.abs(target - current) > SNAP_THRESHOLD;
	}

	/**
	 * Splits a fractional scroll position into the integer window base vanilla line selection
	 * uses and the sub-line fraction. {@code fraction} is always in {@code [0, 1)}.
	 */
	public static float fraction(float current) {
		return current - (float) Math.floor(current);
	}

	/**
	 * The integer window base for line selection: the floor of the fractional position, never
	 * below zero (negative positions — a message sliding in at the bottom — select from the
	 * newest line).
	 */
	public static int windowBase(float current) {
		return Math.max(0, (int) Math.floor(current));
	}

	/**
	 * The sub-line pixel offset to translate the rendered chat by, in <em>local</em> (scaled)
	 * units: positive means content sits lower than its slot. At {@code current} exactly on an
	 * integer this is 0; between integers it is the fraction of one line height.
	 */
	public static float pixelOffset(float current, float lineHeightLocal) {
		return fraction(current) * lineHeightLocal;
	}
}
