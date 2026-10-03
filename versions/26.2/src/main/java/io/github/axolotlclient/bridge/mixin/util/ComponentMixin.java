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

import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.axolotlclient.bridge.util.AxoText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Component.class)
public interface ComponentMixin extends AxoText {
	@Shadow
	MutableComponent copy();

	@Shadow
	String getString();

	@Shadow
	net.minecraft.network.chat.Style getStyle();

	@Shadow
	<T> Optional<T> visit(FormattedText.StyledContentConsumer<T> par1, net.minecraft.network.chat.Style par2);

	@Override
	default String br$getRawString() {
		return getString();
	}

	@Override
	default Mutable br$copy() {
		return copy();
	}

	@Override
	default void br$visit(BiConsumer<String, Style> handler) {
		visit((style, asString) -> {
			handler.accept(asString, style);
			return Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
	}

	@Override
	default Style br$getStyle() {
		return getStyle();
	}
}
