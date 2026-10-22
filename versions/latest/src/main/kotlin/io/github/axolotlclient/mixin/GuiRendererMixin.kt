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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import io.github.axolotlclient.util.IdentifiablePiPRenderState
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(GuiRenderer::class)
abstract class GuiRendererMixin {

    @WrapOperation(
        method = ["preparePictureInPictureState"],
        at = [At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")]
    )
    private fun improvePiPRenderers(
        instance: Map<Class<out PictureInPictureRenderState>, PictureInPictureRenderer<*>>, o: Any,
        original: Operation<PictureInPictureRenderer<*>>, state: PictureInPictureRenderState
    ): Any {
        if (state is IdentifiablePiPRenderState<*>) {
            return state.renderer()
        }
        return original.call(instance, o)
    }
}
