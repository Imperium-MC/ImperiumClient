/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.special.ShieldSpecialRenderer
import net.minecraft.core.component.DataComponentMap
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ShieldSpecialRenderer::class)
abstract class ShieldSpecialRendererMixin {

    @Inject(
        method = ["submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V",
            ordinal = 0
        )]
    )
    private fun `axolotlclient$lowShield`(
        components: DataComponentMap, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int, overlayCoords: Int, hasFoil: Boolean, outlineColor: Int, ci: CallbackInfo
    ) {
        if (AxolotlClient.config().lowShield.get()
            && Minecraft.getInstance().options.getCameraType().isFirstPerson
        ) {
            // shield_height (Alpine grab-bag, wave 3): the nudge distance is configurable;
            // 0.2 (the default) is the offset the old fixed version used.
            poseStack.translate(0.0, AxolotlClient.config().shieldHeight.get().toDouble(), 0.0)
        }
    }
}
