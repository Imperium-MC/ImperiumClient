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

package io.github.axolotlclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.util.PerformanceStats;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.blockentity.TrialSpawnerRenderer;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code optimize_spawners} (performance category, M3): the spinning mob inside a monster
 * spawner is a full entity render-state extraction per spawner per frame (the display entity
 * plus {@code TrialSpawnerRenderer.extractSpawnerData}). With the toggle on, neither runs:
 * the render state keeps a null display entity, so the submit side is a no-op and the
 * spawner cage (chunk-meshed) keeps rendering untouched. Vanilla spawners only — trial
 * spawners use their own renderer and are not affected.
 */
@Mixin(SpawnerRenderer.class)
public abstract class SpawnerOptimizeMixin {

	@WrapOperation(
		method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SpawnerBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SpawnerRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BaseSpawner;getOrCreateDisplayEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/entity/Entity;"))
	private Entity axolotlclient$skipSpawnerDisplayEntity(BaseSpawner spawner, Level level, BlockPos pos, Operation<Entity> original) {
		if (AxolotlClient.config().optimizeSpawners.get()) {
			PerformanceStats.spawnerEntitySkipped();
			return null;
		}
		return original.call(spawner, level, pos);
	}

	@WrapWithCondition(
		method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SpawnerBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SpawnerRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/TrialSpawnerRenderer;extractSpawnerData(Lnet/minecraft/client/renderer/blockentity/state/SpawnerRenderState;FLnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;DD)V"))
	private boolean axolotlclient$skipSpawnerEntityExtraction(SpawnerRenderState state, float tickDelta, Entity entity,
															  EntityRenderDispatcher dispatcher, double oSpin, double spin) {
		return !AxolotlClient.config().optimizeSpawners.get();
	}
}
