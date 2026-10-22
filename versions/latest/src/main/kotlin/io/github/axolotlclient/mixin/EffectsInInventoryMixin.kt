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

import io.github.axolotlclient.modules.inventoryTweaks.InventoryTweaks
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.EffectsInInventory
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * keep_centered / hide_vanilla_potion_hud (inventory_tweaks): controls the vanilla effect panel
 * that `EffectsInInventory` draws to the right of an open container screen.
 *
 * When the panel is suppressed (`keep_centered` or `hide_vanilla_potion_hud`),
 * its draw is cancelled and `canSeeEffects()` reports false — so the containing screen's
 * `showsActiveEffects()` is false too and the effect icons fall back to the HUD's top-right
 * corner while a container is open. The container itself keeps its centered position; combined
 * with hide_vanilla_potion_hud the icons vanish entirely (the HUD corner draw is cancelled by
 * [HideVanillaEffectsMixin]).
 */
@Mixin(EffectsInInventory::class)
abstract class EffectsInInventoryMixin {

    @Inject(method = ["extractRenderState"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$suppressEffectPanel`(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ci: CallbackInfo) {
        if (InventoryTweaks.getInstance().suppressesEffectPanel()) {
            ci.cancel()
        }
    }

    @Inject(method = ["canSeeEffects"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$noEffectPanel`(cir: CallbackInfoReturnable<Boolean>) {
        if (InventoryTweaks.getInstance().suppressesEffectPanel()) {
            cir.setReturnValue(false)
        }
    }
}
