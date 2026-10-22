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
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer
import net.minecraft.client.renderer.blockentity.state.SignRenderState
import net.minecraft.world.level.block.entity.SignText
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * `text_render_distance` (performance category, M3): sign text (the lettering on both
 * faces of standing and hanging signs) is skipped once the sign is farther from the camera
 * than the limit. The sign's wood model itself is chunk-meshed and keeps rendering.
 *
 * The distance is read off the pose stack's current translation: block-entity submission
 * starts from a stack already positioned at the block, relative to the camera, so the
 * translation length is the camera-to-sign distance.
 */
@Mixin(AbstractSignRenderer::class)
abstract class SignTextDistanceMixin {

    @Inject(method = ["submitSignText"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$skipDistantSignText`(
        state: SignRenderState, poseStack: PoseStack, collector: SubmitNodeCollector,
        text: SignText, ci: CallbackInfo
    ) {
        if (AxolotlClient.config() == null) {
            return
        }
        val limit = AxolotlClient.config().textRenderDistance.get()
        if (limit <= 0) {
            return
        }
        val pose: Matrix4f = poseStack.last().pose()
        // Column-major JOML: the translation lives in m30/m31/m32.
        val dx = pose.m30()
        val dy = pose.m31()
        val dz = pose.m32()
        if (dx * dx + dy * dy + dz * dz > (limit * limit).toFloat()) {
            PerformanceStats.signTextSkipped()
            ci.cancel()
        }
    }
}
