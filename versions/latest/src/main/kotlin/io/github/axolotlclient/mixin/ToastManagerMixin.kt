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
import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.sugar.Share
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.util.duck.ToastInstanceExtension
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ToastManager::class)
abstract class ToastManagerMixin {

    @Shadow
    @Final
    private val visibleToasts: MutableList<ToastManager.ToastInstance<*>>? = null

    private companion object {
        @Shadow
        @Final
        @JvmField
        val SLOT_COUNT: Int = 0
    }

    @Unique
    private fun nextY(): Int {
        return visibleToasts!!.stream().map { obj -> obj.toast }.mapToInt { obj -> obj.height() }.sum()
    }

    // Intentional @Redirect as we do not want to invoke the original method
    @Redirect(
        method = ["lambda\$update\$1"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;findFreeSlotsIndex(I)I")]
    )
    private fun limitDisplayToScreenSize(instance: ToastManager, slots: Int, @Local(argsOnly = true) toast: Toast): Int {
        val toasts = visibleToasts!!
        val toastHeight = toast.height()
        val y = nextY()
        val wHeight = AxoWindow.getWindow()!!.`br$getScaledHeight`()
        if (toasts.size < SLOT_COUNT && y <= wHeight / 2f && y + toastHeight < wHeight) {
            return 0
        }

        return -1
    }

    @Inject(
        method = ["extractRenderState"],
        at = [At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;visibleToasts:Ljava/util/List;",
            ordinal = 1,
            opcode = Opcodes.GETFIELD
        )]
    )
    private fun preRenderSetup(guiGraphicsExtractor: GuiGraphicsExtractor, ci: CallbackInfo, @Share("currentY") y: LocalIntRef) {
        y.set(0) // in theory unnecessary but good practice
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager\$ToastInstance;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V",
            ordinal = 0
        )]
    )
    private fun preRender(
        instance: ToastManager.ToastInstance<*>, guiGraphicsExtractor: GuiGraphicsExtractor, guiWidth: Int,
        original: Operation<Void>, @Share("currentY") y: LocalIntRef
    ) {
        val ex = instance as ToastInstanceExtension
        val lerpY = y.get() + (ex.`axolotlclient$getY`() - y.get()) / 2
        ex.`axolotlclient$setY`(lerpY)
        y.set(y.get() + instance.toast.height())
        original.call(instance, guiGraphicsExtractor, guiWidth)
    }
}
