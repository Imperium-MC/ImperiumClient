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

/**
 * Bookkeeping for the client-side time multiplier of the time changer.
 *
 * <p>While active (time changer enabled with a multiplier other than {@code 1.0}),
 * each passing client tick advances the visual world time by {@code multiplier}
 * ticks. Whole extra ticks are accumulated in {@link #offset()} (which may go
 * negative for multipliers below 1), fractions are carried over between ticks.</p>
 */
public final class TimeChangerSpeed {

	private static boolean active;
	private static float multiplier = 1.0F;
	private static double fractionalOffset;
	private static long offset;
	private static long lastExtra;

	private TimeChangerSpeed() {
	}

	/**
	 * Advance the accumulator by one client tick. Must be called once per world tick,
	 * before the game time is advanced for that tick.
	 *
	 * @param timeChangerActive whether the time changer is currently enabled
	 * @param timeMultiplier    the configured time multiplier
	 */
	public static void tick(boolean timeChangerActive, float timeMultiplier) {
		lastExtra = 0;
		if (!timeChangerActive || timeMultiplier == 1.0F) {
			active = false;
			multiplier = 1.0F;
			fractionalOffset = 0;
			offset = 0;
			return;
		}

		if (!active || timeMultiplier != multiplier) {
			// (Re-)activation or a changed multiplier: start over from the current world time.
			active = true;
			multiplier = timeMultiplier;
			fractionalOffset = 0;
			offset = 0;
		}

		fractionalOffset += timeMultiplier - 1.0F;
		while (fractionalOffset >= 1.0F) {
			offset++;
			lastExtra++;
			fractionalOffset -= 1.0F;
		}
		while (fractionalOffset <= -1.0F) {
			offset--;
			lastExtra--;
			fractionalOffset += 1.0F;
		}
	}

	/**
	 * The accumulated whole extra ticks to add on top of the vanilla game time,
	 * so that world time visually passes at the configured multiplier speed.
	 * This is only applied where the server-provided game time enters the client
	 * (the time packet); the per-tick advancement uses {@link #lastExtra()} instead.
	 */
	public static long offset() {
		return offset;
	}

	/**
	 * The whole extra ticks gained during the most recent {@link #tick} call.
	 * Added to this tick's local game time advancement so that the local game time
	 * (and with it the client clock feed) advances at the multiplier speed.
	 */
	public static long lastExtra() {
		return lastExtra;
	}
}
