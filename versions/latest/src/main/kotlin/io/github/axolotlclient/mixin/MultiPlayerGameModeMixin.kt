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

package io.github.axolotlclient.mixin

import io.github.axolotlclient.modules.autoutils.AutoTool
import io.github.axolotlclient.util.FarmingProtections
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Auto-Tool's swap point: the head of both vanilla destroy-block paths, i.e. before a
 * break starts or continues on a block. Swapping the hotbar selection here means vanilla's
 * own `ensureHasSentCarriedItem` (called inside these methods, before any break
 * packet) announces the tool that is about to be used — exactly the ordering a physical
 * hotbar switch produces.
 *
 * The farming break-veto (wave 3) rides the same heads: cancelling before the vanilla
 * body runs means no START/ABORT/STOP destroy packet is ever sent and no client-side
 * break prediction starts, while the "true" return value tells the input code that the
 * (pretend) mining is in progress so it neither falls through to another action nor spams
 * re-tries. `destroyBlock` (the instant-break path some callers use directly) is
 * cancelled the same way and reports "not destroyed".
 */
@Mixin(MultiPlayerGameMode::class)
abstract class MultiPlayerGameModeMixin {

    @Inject(method = ["startDestroyBlock"], at = [At("HEAD")])
    private fun `axolotlclient$autoToolBeforeStart`(pos: BlockPos, direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
        AutoTool.getInstance().beforeDestroy(pos)
    }

    @Inject(method = ["continueDestroyBlock"], at = [At("HEAD")])
    private fun `axolotlclient$autoToolBeforeContinue`(pos: BlockPos, direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
        AutoTool.getInstance().beforeDestroy(pos)
    }

    @Inject(method = ["startDestroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$farmingVetoStart`(pos: BlockPos, direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
        if (FarmingProtections.shouldVetoBreak(pos)) {
            cir.setReturnValue(true)
        }
    }

    @Inject(method = ["continueDestroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$farmingVetoContinue`(pos: BlockPos, direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
        if (FarmingProtections.shouldVetoBreak(pos)) {
            cir.setReturnValue(true)
        }
    }

    @Inject(method = ["destroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$farmingVetoInstant`(pos: BlockPos, cir: CallbackInfoReturnable<Boolean>) {
        if (FarmingProtections.shouldVetoBreak(pos)) {
            cir.setReturnValue(false)
        }
    }
}
