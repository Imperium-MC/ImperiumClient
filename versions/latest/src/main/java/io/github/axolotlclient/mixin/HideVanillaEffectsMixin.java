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

import io.github.axolotlclient.modules.inventoryTweaks.InventoryTweaks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hide_vanilla_potion_hud (inventory_tweaks): cancels the vanilla potion-effect HUD — the
 * top-right icon stack {@code Hud#extractEffects} draws — when the toggle is on, without
 * requiring the custom PotionsHud. Own mixin class so the existing
 * {@code InGameHudMixin#axolotlclient$renderStatusEffect} injection (PotionsHud's cancel) stays
 * untouched; both injections coexist on the same method.
 */
@Mixin(Hud.class)
public abstract class HideVanillaEffectsMixin {

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$hideVanillaPotionHud(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		if (InventoryTweaks.getInstance().hidesVanillaEffects()) {
			ci.cancel();
		}
	}
}
