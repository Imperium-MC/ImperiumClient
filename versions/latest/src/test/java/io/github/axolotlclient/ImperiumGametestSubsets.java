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

package io.github.axolotlclient;

/**
 * Selects which client gametests run. The fabric client gametest framework discovers tests
 * through the fixed {@code fabric-client-gametest} entrypoint key and can only filter whole
 * mods ({@code fabric.client.gametest.modid}), so a within-mod subset needs the tests'
 * cooperation instead: render-heavy tests early-return while the "ci" subset is active, and
 * the logic/protocol tests always run.
 *
 * <p>The property is set from Gradle by the {@code imperium.fastGametests} project property
 * ({@code ./gradlew :latest:runClientGameTest -Pimperium.fastGametests}); GitHub's GPU-less
 * runners execute the suite that way (the full suite stays the default everywhere else).
 */
public final class ImperiumGametestSubsets {

	public static final String SUBSET_PROPERTY = "imperiumclient.gametest.subset";
	public static final String CI = "ci";

	private ImperiumGametestSubsets() {
	}

	/** True when the given subset is active ({@code -Dimperiumclient.gametest.subset=<subset>}). */
	public static boolean isActive(String subset) {
		return subset.equals(System.getProperty(SUBSET_PROPERTY));
	}

	/**
	 * True while the headless "ci" subset runs: only logic/protocol tests execute, the
	 * GPU-bound world-and-screenshot tests early-return.
	 */
	public static boolean ci() {
		return isActive(CI);
	}

	/** Human-readable form for bootstrap logging. */
	public static String describe() {
		String subset = System.getProperty(SUBSET_PROPERTY);
		return subset == null ? "all" : subset;
	}
}
