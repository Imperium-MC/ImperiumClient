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

package io.github.axolotlclient.bridge.entity

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.math.Vec3
import java.util.UUID

interface AxoEntity {
	@RequiresImpl
	fun `br$getVehicle`(): AxoEntity? {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getPos`(): Vec3 {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getVelocity`(): Vec3 {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$isOnGround`(): Boolean {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getYaw`(): Float {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getPitch`(): Float {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getNetId`(): Int {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getRotation`(deltaTick: Float): Vec3 {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getUuid`(): UUID {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getEyePos`(delta: Float): Vec3 {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getHeight`(): Float {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Get the radii of this entity's bounding box.
	 *
	 * @return a vector of half the box's side lengths. The origin of the vector is (0, height/2, 0) relative to the Entity's position.
	 */
	@RequiresImpl
	fun `br$getBoundingBoxHalfDimensions`(): Vec3 {
		throw BridgeUtil.noImpl()
	}
}
