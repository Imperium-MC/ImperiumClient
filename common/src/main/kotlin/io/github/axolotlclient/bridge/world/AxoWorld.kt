/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.bridge.world

import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.math.Vec3

interface AxoWorld {
	@RequiresImpl
	fun `br$getTimeOfDay`(): Long {
		throw BridgeUtil.noImpl()
	}

	// @JvmWildcard keeps the Java-generic signature List<? extends AxoPlayer> the mixins
	// override with (a bare `out` projection is dropped from JVM signatures in return position).
	@RequiresImpl
	fun `br$getPlayers`(): List<@JvmWildcard AxoPlayer> {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getBiomeName`(pos: Vec3): String {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$isOverworld`(): Boolean {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$isNether`(): Boolean {
		throw BridgeUtil.noImpl()
	}
}
