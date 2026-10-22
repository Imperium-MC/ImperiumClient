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

package io.github.axolotlclient.bridge.mixin.world

import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.math.Vec3
import io.github.axolotlclient.bridge.world.AxoWorld
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.Collections

@Mixin(Level::class)
abstract class WorldMixin : AxoWorld, LevelAccessor {
	@Shadow
	abstract fun dimension(): ResourceKey<Level>?

	override fun `br$getTimeOfDay`(): Long = getGameTime()

	override fun `br$getPlayers`(): List<out AxoPlayer> = Collections.unmodifiableList(players())

	override fun `br$getBiomeName`(pos: Vec3): String {
		val biome = getBiome(BlockPos(Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z))).unwrap().left().orElse(null)
			?: return I18n.get("coordshud.unknown_biome")
		return biome.identifier().`br$getAsFriendlyString`()
	}

	override fun `br$isOverworld`(): Boolean = dimension() == Level.OVERWORLD

	override fun `br$isNether`(): Boolean = dimension() == Level.NETHER
}
