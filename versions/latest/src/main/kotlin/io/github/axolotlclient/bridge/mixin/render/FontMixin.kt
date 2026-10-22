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

package io.github.axolotlclient.bridge.mixin.render

import io.github.axolotlclient.bridge.render.AxoFont
import io.github.axolotlclient.bridge.util.AxoText
import net.minecraft.network.chat.FormattedText
import net.minecraft.client.gui.Font
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(Font::class)
abstract class FontMixin : AxoFont {
	@Shadow
	abstract fun width(str: String): Int

	@Shadow
	@field:Final
	val lineHeight: Int = 0

	@Shadow
	abstract fun width(text: FormattedText): Int

	override fun `br$getWidth`(text: AxoText): Int = width(text as FormattedText)

	override fun `br$getWidth`(text: String): Int = width(text)

	override fun `br$getFontHeight`(): Int = lineHeight
}
