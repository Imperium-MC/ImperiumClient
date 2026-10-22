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
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.Optional
import java.util.function.BiConsumer

@Mixin(Component::class)
interface ComponentMixin : AxoText {
	@Shadow
	fun copy(): MutableComponent

	@Shadow
	fun getString(): String

	@Shadow
	fun getStyle(): Style

	// T constrained to Any: FormattedText.StyledContentConsumer's type parameter is NullMarked
	// non-null; erasure matches the Java original.
	@Shadow
	fun <T : Any> visit(consumer: FormattedText.StyledContentConsumer<T>, style: Style): Optional<T>

	override fun `br$getRawString`(): String = getString()

	override fun `br$copy`(): AxoText.Mutable = copy()

	override fun `br$visit`(handler: BiConsumer<String, AxoText.Style>) {
		visit(
			FormattedText.StyledContentConsumer<Any> { style: Style, asString: String ->
				handler.accept(asString, style)
				Optional.empty()
			},
			Style.EMPTY
		)
	}

	override fun `br$getStyle`(): AxoText.Style = getStyle()
}
