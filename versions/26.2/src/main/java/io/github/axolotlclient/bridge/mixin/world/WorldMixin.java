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

package io.github.axolotlclient.bridge.mixin.world;

import java.util.Collections;
import java.util.List;

import io.github.axolotlclient.bridge.entity.AxoPlayer;
import io.github.axolotlclient.bridge.math.Vec3;
import io.github.axolotlclient.bridge.world.AxoWorld;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Level.class)
public abstract class WorldMixin implements AxoWorld, LevelAccessor {
	@Shadow
	public abstract ResourceKey<Level> dimension();

	@Override
	public long br$getTimeOfDay() {
		return getGameTime();
	}

	@Override
	public List<? extends AxoPlayer> br$getPlayers() {
		return Collections.unmodifiableList(players());
	}

	@Override
	public String br$getBiomeName(Vec3 pos) {
		var biome = getBiome(new BlockPos(Mth.floor(pos.x()), Mth.floor(pos.y()), Mth.floor(pos.z()))).unwrap().left().orElse(null);
		if (biome == null) {
			return I18n.get("coordshud.unknown_biome");
		}
		return biome.identifier().br$getAsFriendlyString();
	}

	@Override
	public boolean br$isOverworld() {
		return dimension() == Level.OVERWORLD;
	}

	@Override
	public boolean br$isNether() {
		return dimension() == Level.NETHER;
	}
}
