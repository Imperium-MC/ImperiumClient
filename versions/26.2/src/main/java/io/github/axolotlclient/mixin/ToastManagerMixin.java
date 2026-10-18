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

package io.github.axolotlclient.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import io.github.axolotlclient.bridge.render.AxoWindow;
import io.github.axolotlclient.util.duck.ToastInstanceExtension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

	@Shadow
	@Final
	private List<ToastManager.ToastInstance<?>> visibleToasts;

	@Shadow
	@Final
	private static int SLOT_COUNT;

	// Intentional @Redirect as we do not want to invoke the original method
	@Redirect(method = "lambda$update$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;findFreeSlotsIndex(I)I"))
	private int limitDisplayToScreenSize(ToastManager instance, int slots, @Local(argsOnly = true) Toast toast) {

		var toastHeight = toast.height();
		var y = nextY();
		var wHeight = AxoWindow.getWindow().br$getScaledHeight();
		if (visibleToasts.size() < SLOT_COUNT && y <= wHeight / 2f && y + toastHeight < wHeight) {
			return 0;
		}

		return -1;
	}

	@Unique
	private int nextY() {
		return visibleToasts.stream().map(ToastManager.ToastInstance::getToast).mapToInt(Toast::height).sum();
	}

	@Inject(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;visibleToasts:Ljava/util/List;", ordinal = 1, opcode = Opcodes.GETFIELD))
	private void preRenderSetup(GuiGraphicsExtractor guiGraphicsExtractor, CallbackInfo ci, @Share("currentY") LocalIntRef y) {
		y.set(0); // in theory unnecessary but good practice
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager$ToastInstance;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", ordinal = 0))
	private void preRender(ToastManager.ToastInstance<?> instance, GuiGraphicsExtractor guiGraphicsExtractor, int guiWidth, Operation<Void> original, @Share("currentY") LocalIntRef y) {
		var ex = ((ToastInstanceExtension) instance);
		var lerpY = y.get() + (ex.axolotlclient$getY() - y.get()) / 2;
		ex.axolotlclient$setY(lerpY);
		y.set(y.get() + instance.getToast().height());
		original.call(instance, guiGraphicsExtractor, guiWidth);
	}
}
