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

import io.github.axolotlclient.modules.autoutils.AutoTool;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Auto-Tool's swap point: the head of both vanilla destroy-block paths, i.e. before a
 * break starts or continues on a block. Swapping the hotbar selection here means vanilla's
 * own {@code ensureHasSentCarriedItem} (called inside these methods, before any break
 * packet) announces the tool that is about to be used — exactly the ordering a physical
 * hotbar switch produces.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void axolotlclient$autoToolBeforeStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		AutoTool.getInstance().beforeDestroy(pos);
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"))
	private void axolotlclient$autoToolBeforeContinue(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		AutoTool.getInstance().beforeDestroy(pos);
	}
}
