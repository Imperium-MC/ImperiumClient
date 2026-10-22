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
import io.github.axolotlclient.bridge.entity.AxoLivingEntity
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance
import io.github.axolotlclient.bridge.math.Vec3
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(LivingEntity::class)
abstract class LivingEntityMixin : AxoLivingEntity {
	@Shadow
	@field:Final
	private val activeEffects: Map<Holder<MobEffect>, MobEffectInstance>? = null

	private val self: Entity
		get() = this as Any as Entity

	override fun `br$getStatusEffects`(): List<AxoStatusEffectInstance> = activeEffects!!.values.toList()

	override fun `br$getVehicle`(): AxoEntity? = self.vehicle

	override fun `br$getPos`(): Vec3 {
		val pos = self.position()
		return Vec3(pos.x, pos.y, pos.z)
	}

	override fun `br$getVelocity`(): Vec3 {
		val movement = self.getDeltaMovement()
		return Vec3(movement.x, movement.y, movement.z)
	}

	override fun `br$isOnGround`(): Boolean = self.onGround()

	override fun `br$getYaw`(): Float = self.yRot

	override fun `br$getPitch`(): Float = self.xRot

	override fun `br$getRotation`(deltaTick: Float): Vec3 {
		val vec = self.getViewVector(deltaTick)
		return Vec3(vec.x, vec.y, vec.z)
	}

	override fun `br$getUuid`(): java.util.UUID = self.uuid

	override fun `br$getNetId`(): Int = self.id

	override fun `br$getEyePos`(delta: Float): Vec3 {
		val pos = self.getEyePosition(delta)
		return Vec3(pos.x, pos.y, pos.z)
	}

	override fun `br$getHeight`(): Float = self.getEyeHeight()

	override fun `br$getBoundingBoxHalfDimensions`(): Vec3 {
		val box = self.boundingBox
		return Vec3(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).div(2.0)
	}
}
