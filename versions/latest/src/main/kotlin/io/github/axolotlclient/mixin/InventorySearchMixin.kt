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

import io.github.axolotlclient.modules.inventorySearch.InventorySearch
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Wires the inventory search overlay into every container screen: the bound key opens it, typed
 * characters feed the search field, clicks on the bar focus it and the overlay is drawn on top of
 * the slot contents (but below carried item and tooltip).
 */
@Mixin(AbstractContainerScreen::class)
abstract class InventorySearchMixin {

    @Shadow
    private var leftPos = 0

    @Shadow
    private var topPos = 0

    @Shadow
    private var imageWidth = 0

    @Shadow
    private var imageHeight = 0

    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$inventorySearchKey`(event: KeyEvent, cir: CallbackInfoReturnable<Boolean>) {
        if (InventorySearch.getInstance().onKeyPressed(event)) {
            cir.setReturnValue(true)
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$inventorySearchClick`(event: MouseButtonEvent, doubleClick: Boolean, cir: CallbackInfoReturnable<Boolean>) {
        if (InventorySearch.getInstance().onMouseClicked(event, doubleClick)) {
            cir.setReturnValue(true)
        }
    }

    @Inject(method = ["extractContents"], at = [At("TAIL")])
    private fun `axolotlclient$inventorySearchOverlay`(
        graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float, ci: CallbackInfo
    ) {
        InventorySearch.getInstance().renderOverlay(
            this as AbstractContainerScreen<*>, graphics, mouseX, mouseY, partialTick,
            leftPos, topPos, imageWidth, imageHeight
        )
    }

    @Inject(method = ["removed"], at = [At("HEAD")])
    private fun `axolotlclient$inventorySearchScreenClosed`(ci: CallbackInfo) {
        InventorySearch.getInstance().onScreenRemoved()
    }

    /**
     * Container screens do not override charTyped, so typed characters normally resolve to the
     * interface default (forwarded to the focused listener). While the search overlay is up they
     * are routed to its edit box instead; otherwise the vanilla behaviour is replicated.
     */
    open fun charTyped(event: CharacterEvent): Boolean {
        val search = InventorySearch.getInstance()
        if (search.isActive()) {
            search.onCharTyped(event)
            return true
        }
        val focused = (this as AbstractContainerScreen<*>).focused as GuiEventListener?
        return focused != null && focused.charTyped(event)
    }
}
