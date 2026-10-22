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

import com.mojang.authlib.GameProfile
import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoPlayerInventory
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(Player::class)
abstract class PlayerMixin : AxoPlayer {
	@Shadow
	@field:Final
	private val gameProfile: GameProfile? = null

	@Shadow
	@field:Final
	private val inventory: Inventory? = null

	@Shadow
	abstract fun getProjectile(weaponStack: ItemStack): ItemStack

	@Shadow
	var experienceLevel: Int = 0

	@Shadow
	var experienceProgress: Float = 0f

	override fun `br$getInventory`(): AxoPlayerInventory = inventory!!

	override fun `br$getProjectileItem`(): AxoItem? = getProjectile(Items.BOW.defaultInstance).item

	override fun `br$getName`(): String = gameProfile!!.name

	override fun `br$getExperienceLevel`(): Int = experienceLevel

	override fun `br$getExperienceProgress`(): Float = experienceProgress
}
