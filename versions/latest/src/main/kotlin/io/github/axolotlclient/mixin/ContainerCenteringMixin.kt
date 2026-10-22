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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * keep_centered (inventory_tweaks): recomputes the container position after
 * `AbstractContainerScreen#init` so the screen is exactly centered whenever the vanilla
 * effect panel is being suppressed beside it — the case where a screen (vanilla or modded) may
 * have shifted `leftPos` to make room for the icons. The guard replays vanilla's own
 * "would the panel show" test (effects present, at least 32px free right of the container) from
 * the raw fields, because the panel suppression already made `showsActiveEffects()` lie.
 *
 * Own mixin class targeting `AbstractContainerScreen`, additive with the existing
 * `InventorySearchMixin` registration on the same target — both stay intact.
 */
@Mixin(AbstractContainerScreen::class)
abstract class ContainerCenteringMixin {

    @Shadow
    protected var leftPos: Int = 0

    @Shadow
    protected var topPos: Int = 0

    @Shadow
    protected var imageWidth: Int = 0

    @Shadow
    protected var imageHeight: Int = 0

    @Inject(method = ["init"], at = [At("TAIL")])
    private fun `axolotlclient$keepContainerCentered`(ci: CallbackInfo) {
        if (!InventoryTweaks.getInstance().forcesCenteredContainers() || !wouldShowEffectPanel()) {
            return
        }
        // width/height live on Screen (public there) — shadows only reach the target's own
        // fields, so read them through the screen itself.
        val screen = this as Screen
        leftPos = (screen.width - imageWidth) / 2
        topPos = (screen.height - imageHeight) / 2
    }

    /**
     * Vanilla's `EffectsInInventory#canSeeEffects()` condition, read from the fields
     * `init` just laid out: the player has visible effects and at least 32 pixels of space
     * sit between the container's right edge and the screen edge.
     */
    private fun wouldShowEffectPanel(): Boolean {
        val player = Minecraft.getInstance().player
        return player != null && player.activeEffects.isNotEmpty()
            && (this as Screen).width - (leftPos + imageWidth + 2) >= 32
    }
}
