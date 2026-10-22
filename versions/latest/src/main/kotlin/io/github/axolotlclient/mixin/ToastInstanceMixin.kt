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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import io.github.axolotlclient.util.duck.ToastExtension
import io.github.axolotlclient.util.duck.ToastInstanceExtension
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect

@Mixin(ToastManager.ToastInstance::class)
abstract class ToastInstanceMixin<T : Toast> : ToastInstanceExtension {

    @Shadow
    abstract fun getToast(): T

    @Unique
    private var y = 0

    // Intentional @Redirect instead of @WrapOperation as we do not invoke the original method
    @Redirect(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast;yPos(I)F")]
    )
    private fun useCalcYPos(instance: Toast, firstSlotIndex: Int): Float {
        return y.toFloat()
    }

    override fun `axolotlclient$setY`(y: Int) {
        this.y = y
    }

    override fun `axolotlclient$getY`(): Int {
        return y
    }

    @ModifyExpressionValue(method = ["update"], at = [At(value = "CONSTANT", args = ["longValue=600"])])
    private fun customAnimationSpeed(original: Long): Long {
        val opt = (getToast() as ToastExtension).`axolotlclient$animationDuration`()
        return if (opt.isPresent) opt.asLong else original
    }

    @ModifyExpressionValue(method = ["update", "calculateVisiblePortion"], at = [At(value = "CONSTANT", args = ["floatValue=600.0"])])
    private fun customAnimationSpeed(original: Float): Float {
        val opt = (getToast() as ToastExtension).`axolotlclient$animationDuration`()
        return if (opt.isPresent) opt.asLong.toFloat() else original
    }
}
