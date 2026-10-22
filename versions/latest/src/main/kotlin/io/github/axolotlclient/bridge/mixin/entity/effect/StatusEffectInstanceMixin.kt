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

package io.github.axolotlclient.bridge.mixin.entity.effect

import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffect
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance
import io.github.axolotlclient.bridge.util.AxoText
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffectUtil
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(MobEffectInstance::class)
abstract class StatusEffectInstanceMixin : AxoStatusEffectInstance {
	@Shadow
	@field:Final
	private val effect: Holder<MobEffect>? = null

	@Shadow
	private val amplifier: Int = 0

	override fun `br$getType`(): AxoStatusEffect = effect!!.value()

	override fun `br$getAmplifier`(): Int = amplifier

	override fun `br$getDuration`(): Int = (this as MobEffectInstance).duration

	override fun `br$formatDuration`(): AxoText {
		val client = Minecraft.getInstance()
		val tickRate = if (client.level != null) client.level!!.tickRateManager().tickrate() else 1
		return MobEffectUtil.formatDuration(this as MobEffectInstance, 1f, tickRate.toFloat())
	}
}
