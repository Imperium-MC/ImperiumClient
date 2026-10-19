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

import io.github.axolotlclient.modules.inventorySearch.InventorySearch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wires the inventory search overlay into every container screen: the bound key opens it, typed
 * characters feed the search field, clicks on the bar focus it and the overlay is drawn on top of
 * the slot contents (but below carried item and tooltip).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventorySearchMixin {

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Shadow
	protected int imageWidth;

	@Shadow
	protected int imageHeight;

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$inventorySearchKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (InventorySearch.getInstance().onKeyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$inventorySearchClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (InventorySearch.getInstance().onMouseClicked(event, doubleClick)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void axolotlclient$inventorySearchOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		InventorySearch.getInstance().renderOverlay((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY, partialTick,
			leftPos, topPos, imageWidth, imageHeight);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void axolotlclient$inventorySearchScreenClosed(CallbackInfo ci) {
		InventorySearch.getInstance().onScreenRemoved();
	}

	/**
	 * Container screens do not override charTyped, so typed characters normally resolve to the
	 * interface default (forwarded to the focused listener). While the search overlay is up they
	 * are routed to its edit box instead; otherwise the vanilla behaviour is replicated.
	 */
	public boolean charTyped(CharacterEvent event) {
		InventorySearch search = InventorySearch.getInstance();
		if (search.isActive()) {
			search.onCharTyped(event);
			return true;
		}
		GuiEventListener focused = ((AbstractContainerScreen<?>) (Object) this).getFocused();
		return focused != null && focused.charTyped(event);
	}
}
