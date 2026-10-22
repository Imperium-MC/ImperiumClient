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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.util.FarmingProtections;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The farming render-hides (Alpine parity, wave 3): {@code SectionCompiler.compile} walks
 * every block of a chunk section and asks its state for the {@link RenderShape} — anything
 * that is not {@code MODEL} never reaches the tessellator. Wrapping exactly that call and
 * answering {@code INVISIBLE} for cactus/tripwire removes them from the baked chunk mesh,
 * and ONLY from it: item-held cactus, item-frame contents, the targeted block's outline
 * and collision all stay vanilla because they never ask this call site.
 *
 * <p>The compile runs off the main thread on the chunk-build workers; a stale boolean
 * read there is harmless (the config-UI flip re-meshes the world through
 * {@code FarmingProtections.remeshWorld}, re-running every section with the new value).</p>
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {

	@WrapOperation(
		method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
	private RenderShape axolotlclient$hideFarmingRenderBlocks(BlockState state, Operation<RenderShape> original) {
		if (FarmingProtections.shouldHideBlockState(state)) {
			return RenderShape.INVISIBLE;
		}
		return original.call(state);
	}
}
