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

import com.mojang.blaze3d.vertex.PoseStack
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.util.PerformanceStats
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * `disable_enchant_table_books` (performance category, M3): the entire
 * `EnchantTableRenderer` draw is the floating book (the table block itself is
 * chunk-meshed), so cancelling its submit while the toggle is on skips the per-frame book
 * model animation without touching the table.
 */
@Mixin(EnchantTableRenderer::class)
abstract class EnchantTableBooksMixin {

    @Inject(
        method = ["submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun `axolotlclient$skipEnchantTableBook`(
        state: EnchantTableRenderState, poseStack: PoseStack,
        collector: SubmitNodeCollector, camera: CameraRenderState, ci: CallbackInfo
    ) {
        if (AxolotlClient.config().disableEnchantTableBooks.get()) {
            PerformanceStats.enchantBookSkipped()
            ci.cancel()
        }
    }
}
