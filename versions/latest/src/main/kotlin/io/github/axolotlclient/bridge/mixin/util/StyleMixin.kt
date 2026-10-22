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
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(Style::class)
abstract class StyleMixin : AxoText.Style {
	@Shadow
	abstract fun withColor(textColor: TextColor): Style

	@Shadow
	abstract fun withHoverEvent(hoverEvent: HoverEvent): Style

	@Shadow
	abstract fun withColor(formatting: ChatFormatting?): Style

	override fun `br$color`(color: AxoText.Color): AxoText.Style = withColor(
		when (color) {
			AxoText.Color.BLACK -> ChatFormatting.BLACK
			AxoText.Color.DARK_BLUE -> ChatFormatting.DARK_BLUE
			AxoText.Color.DARK_GREEN -> ChatFormatting.DARK_GREEN
			AxoText.Color.DARK_AQUA -> ChatFormatting.DARK_AQUA
			AxoText.Color.DARK_RED -> ChatFormatting.DARK_RED
			AxoText.Color.DARK_PURPLE -> ChatFormatting.DARK_PURPLE
			AxoText.Color.GOLD -> ChatFormatting.GOLD
			AxoText.Color.GRAY -> ChatFormatting.GRAY
			AxoText.Color.DARK_GRAY -> ChatFormatting.DARK_GRAY
			AxoText.Color.BLUE -> ChatFormatting.BLUE
			AxoText.Color.GREEN -> ChatFormatting.GREEN
			AxoText.Color.AQUA -> ChatFormatting.AQUA
			AxoText.Color.RED -> ChatFormatting.RED
			AxoText.Color.LIGHT_PURPLE -> ChatFormatting.LIGHT_PURPLE
			AxoText.Color.YELLOW -> ChatFormatting.YELLOW
			AxoText.Color.WHITE -> ChatFormatting.WHITE
		}
	)

	override fun `br$color`(color: Int): AxoText.Style = withColor(TextColor.fromRgb(color))

	override fun `br$tooltip`(text: AxoText): AxoText.Style = withHoverEvent(HoverEvent.ShowText(text as Component))
}
