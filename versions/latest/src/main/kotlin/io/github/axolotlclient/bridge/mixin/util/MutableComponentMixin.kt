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

package io.github.axolotlclient.bridge.mixin.util

import io.github.axolotlclient.bridge.util.AxoText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(MutableComponent::class)
abstract class MutableComponentMixin : Component, AxoText.Mutable {
	@Shadow
	abstract fun append(sibling: Component): MutableComponent

	@Shadow
	abstract fun setStyle(style: Style): MutableComponent

	override fun `br$append`(child: AxoText): AxoText.Mutable = append(child as Component)

	override fun `br$setStyle`(style: AxoText.Style): AxoText.Mutable {
		// can't shadow setStyle because of stupid mapping bug...
		return setStyle(style as Style)
	}
}
