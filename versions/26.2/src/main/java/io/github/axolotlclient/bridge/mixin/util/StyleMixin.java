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

package io.github.axolotlclient.bridge.mixin.util;

import io.github.axolotlclient.bridge.util.AxoText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Style.class)
public abstract class StyleMixin implements AxoText.Style {
	@Shadow
	public abstract Style withColor(TextColor par1);

	@Shadow
	public abstract Style withHoverEvent(HoverEvent par1);

	@Shadow
	public abstract Style withColor(@Nullable ChatFormatting formatting);

	@Override
	public AxoText.Style br$color(AxoText.Color color) {
		return withColor(switch (color) {
			case BLACK -> ChatFormatting.BLACK;
			case DARK_BLUE -> ChatFormatting.DARK_BLUE;
			case DARK_GREEN -> ChatFormatting.DARK_GREEN;
			case DARK_AQUA -> ChatFormatting.DARK_AQUA;
			case DARK_RED -> ChatFormatting.DARK_RED;
			case DARK_PURPLE -> ChatFormatting.DARK_PURPLE;
			case GOLD -> ChatFormatting.GOLD;
			case GRAY -> ChatFormatting.GRAY;
			case DARK_GRAY -> ChatFormatting.DARK_GRAY;
			case BLUE -> ChatFormatting.BLUE;
			case GREEN -> ChatFormatting.GREEN;
			case AQUA -> ChatFormatting.AQUA;
			case RED -> ChatFormatting.RED;
			case LIGHT_PURPLE -> ChatFormatting.LIGHT_PURPLE;
			case YELLOW -> ChatFormatting.YELLOW;
			case WHITE -> ChatFormatting.WHITE;
		});
	}

	@Override
	public AxoText.Style br$color(int color) {
		return withColor(TextColor.fromRgb(color));
	}

	@Override
	public AxoText.Style br$tooltip(AxoText text) {
		return withHoverEvent(new HoverEvent.ShowText((Component) text));
	}
}
