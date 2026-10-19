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

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the client-side weather override of the time changer category.
 * Only client levels are affected; the (integrated) server keeps its own weather.
 */
@Mixin(Level.class)
public abstract class WeatherOverrideMixin {

	@Inject(method = "getRainLevel(F)F", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$overrideRainLevel(float partialTicks, CallbackInfoReturnable<Float> cir) {
		AxolotlClientConfigCommon.WeatherOverride weather = AxolotlClient.config().weather.get();
		if (weather != AxolotlClientConfigCommon.WeatherOverride.OFF && (Object) this instanceof ClientLevel) {
			float intensity = AxolotlClient.config().weatherIntensity.get();
			cir.setReturnValue(weather == AxolotlClientConfigCommon.WeatherOverride.CLEAR ? 0.0F : intensity);
		}
	}

	@Inject(method = "getThunderLevel(F)F", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$overrideThunderLevel(float partialTicks, CallbackInfoReturnable<Float> cir) {
		AxolotlClientConfigCommon.WeatherOverride weather = AxolotlClient.config().weather.get();
		if (weather != AxolotlClientConfigCommon.WeatherOverride.OFF && (Object) this instanceof ClientLevel) {
			float intensity = AxolotlClient.config().weatherIntensity.get();
			cir.setReturnValue(weather == AxolotlClientConfigCommon.WeatherOverride.THUNDER ? intensity : 0.0F);
		}
	}
}
