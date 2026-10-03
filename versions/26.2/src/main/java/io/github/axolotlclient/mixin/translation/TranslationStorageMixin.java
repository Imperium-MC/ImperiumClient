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

package io.github.axolotlclient.mixin.translation;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientLanguage.class)
public abstract class TranslationStorageMixin {

	@Inject(method = "getOrDefault", at = @At(value = "HEAD"))
	private void specialTranslationKeys(String string, String defaultText, CallbackInfoReturnable<String> cir, @Local(argsOnly = true, ordinal = 0) LocalRef<String> key) {
		if (key.get().startsWith("custom_hud/")) {
			key.set("custom_hud");
		}
	}

	@Inject(method = "has", at = @At(value = "HEAD"))
	private void specialTranslations(String string, CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) LocalRef<String> key) {
		if (key.get().startsWith("custom_hud/")) {
			key.set("custom_hud");
		}
	}
}
