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

package io.github.axolotlclient.mixin

import com.mojang.blaze3d.vertex.PoseStack
import io.github.axolotlclient.util.duck.SubmitNodeCollectorExtension
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(SubmitNodeStorage::class)
abstract class SubmitNodeStorageMixin : SubmitNodeCollectorExtension {

    @Shadow
    abstract fun order(i: Int): SubmitNodeCollection

    override fun `axolotlclient$submitLevelHead`(
        poseStack: PoseStack, nameTagAttachment: Vec3?, offset: Int, name: Component,
        seeThrough: Boolean, lightCoords: Int, camera: CameraRenderState
    ) {
        (order(0) as SubmitNodeCollectorExtension).`axolotlclient$submitLevelHead`(
            poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera
        )
    }

    override fun `axolotlclient$submitBadge`(
        poseStack: PoseStack, nameTagAttachment: Vec3?, offset: Int, name: Component,
        seeThrough: Boolean, lightCoords: Int, camera: CameraRenderState
    ) {
        (order(0) as SubmitNodeCollectorExtension).`axolotlclient$submitBadge`(
            poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera
        )
    }
}
