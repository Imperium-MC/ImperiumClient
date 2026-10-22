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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import io.github.axolotlclient.util.ClearWater
import io.github.axolotlclient.util.PerformanceHides
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.extract.LevelExtractor
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Entity visibility seam for the performance category's render-hide toggles (M3):
 * `isEntityVisible` is the per-entity gate vanilla calls from
 * `extractVisibleEntities` with the camera position, so answering `false` here
 * skips the entity's render-state extraction and submission entirely.
 */
@Mixin(LevelExtractor::class)
abstract class LevelExtractorMixin {

    @Inject(method = ["isEntityVisible"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$hideForPerformance`(
        entity: Entity, frustum: Frustum, camX: Double, camY: Double,
        camZ: Double, partialTick: Float, seed: Long, cir: CallbackInfoReturnable<Boolean>
    ) {
        if (PerformanceHides.shouldSkipEntity(entity, camX, camZ)) {
            cir.setReturnValue(false)
        }
    }

    /**
     * clear_water/overlay_transparency (Alpine parity): the one `ARGB.colorFromFloat`
     * in `extractPlayerState` builds the underwater overlay quad's colour with
     * vanilla's fixed 0.1 alpha; scale that alpha to the configured percent.
     */
    @WrapOperation(
        method = ["extractPlayerState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I")]
    )
    private fun `axolotlclient$waterOverlayTransparency`(
        alpha: Float, red: Float, green: Float, blue: Float, original: Operation<Int>
    ): Int {
        return original.call(ClearWater.waterOverlayAlpha(alpha), red, green, blue)
    }
}
