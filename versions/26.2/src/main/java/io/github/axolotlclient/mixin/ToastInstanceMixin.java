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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.axolotlclient.util.duck.ToastExtension;
import io.github.axolotlclient.util.duck.ToastInstanceExtension;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ToastManager.ToastInstance.class)
public abstract class ToastInstanceMixin<T extends Toast> implements ToastInstanceExtension {

	@Shadow
	public abstract T getToast();

	@Unique
	private int y;

	// Intentional @Redirect instead of @WrapOperation as we do not invoke the original method
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast;yPos(I)F"))
	private float useCalcYPos(Toast instance, int firstSlotIndex) {
		return y;
	}

	@Override
	public void axolotlclient$setY(int y) {
		this.y = y;
	}

	@Override
	public int axolotlclient$getY() {
		return y;
	}

	@ModifyExpressionValue(method = "update", at = @At(value = "CONSTANT", args = "longValue=600"))
	private long customAnimationSpeed(long original) {
		var opt = ((ToastExtension) getToast()).axolotlclient$animationDuration();
		if (opt.isPresent()) return opt.getAsLong();
		return original;
	}

	@ModifyExpressionValue(method = {"update", "calculateVisiblePortion"}, at = @At(value = "CONSTANT", args = "floatValue=600.0"))
	private float customAnimationSpeed(float original) {
		var opt = ((ToastExtension) getToast()).axolotlclient$animationDuration();
		if (opt.isPresent()) return opt.getAsLong();
		return original;
	}
}
