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

import io.github.axolotlclient.AxolotlClient
import net.minecraft.client.Minecraft
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LivingEntityRenderer::class)
abstract class LivingEntityRendererMixin<T : LivingEntity, S : LivingEntityRenderState, M : EntityModel<in S>> protected constructor(ctx: EntityRendererProvider.Context) :
    EntityRenderer<T, S>(ctx),
    RenderLayerParent<S, M> {

    @Inject(method = ["shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$showOwnNametag`(entity: T, d: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (AxolotlClient.config().showOwnNametag.get()
            && entity === Minecraft.getInstance().getCameraEntity()
        ) {
            cir.setReturnValue(true)
        }
    }
}
