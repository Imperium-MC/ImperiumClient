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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * compact_item_counts (inventory_tweaks): routes the stack-count string every GUI item
 * decoration draws through the compact formatter. {@code GuiGraphicsExtractor#itemCount} is the
 * single funnel — container slots, the hotbar and the carried item all end here — and it only
 * stringifies the count itself when the caller did not pass an override text (vanilla's
 * quick-craft "over max stack" hint keeps its own yellow text).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class CompactItemCountMixin {

	@Redirect(method = "itemCount", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"))
	private String axolotlclient$compactCount(int count) {
		return InventoryTweaks.getInstance().formatCount(count);
	}
}
