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

package io.github.axolotlclient.bridge.mixin.entity

import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.math.Vec3
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.UUID

@Mixin(Entity::class)
abstract class EntityMixin : AxoEntity {
	@Shadow
	private val vehicle: Entity? = null

	@Shadow
	private val onGround: Boolean = false

	@Shadow
	abstract fun position(): net.minecraft.world.phys.Vec3

	@Shadow
	abstract fun getDeltaMovement(): net.minecraft.world.phys.Vec3

	@Shadow
	private val yRot: Float = 0f

	@Shadow
	abstract fun getViewVector(partialTicks: Float): net.minecraft.world.phys.Vec3

	@Shadow
	abstract fun getUUID(): UUID

	@Shadow
	private val id: Int = 0

	@Shadow
	private val xRot: Float = 0f

	@Shadow
	abstract fun getEyePosition(partialTick: Float): net.minecraft.world.phys.Vec3

	@Shadow
	abstract fun getEyeHeight(): Float

	@Shadow
	abstract fun getBoundingBox(): AABB

	override fun `br$getVehicle`(): AxoEntity? = vehicle

	override fun `br$getPos`(): Vec3 = Vec3(position().x, position().y, position().z)

	override fun `br$getVelocity`(): Vec3 = Vec3(getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z)

	override fun `br$isOnGround`(): Boolean = onGround

	override fun `br$getYaw`(): Float = yRot

	override fun `br$getRotation`(deltaTick: Float): Vec3 {
		val vec = getViewVector(deltaTick)
		return Vec3(vec.x, vec.y, vec.z)
	}

	override fun `br$getUuid`(): UUID = getUUID()

	override fun `br$getPitch`(): Float = xRot

	override fun `br$getNetId`(): Int = id

	override fun `br$getEyePos`(delta: Float): Vec3 {
		val pos = getEyePosition(delta)
		return Vec3(pos.x, pos.y, pos.z)
	}

	override fun `br$getHeight`(): Float = getEyeHeight()

	override fun `br$getBoundingBoxHalfDimensions`(): Vec3 {
		val box = getBoundingBox()
		return Vec3(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).div(2.0)
	}
}
