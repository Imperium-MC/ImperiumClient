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

package io.github.axolotlclient.imperium.theme

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

/**
 * The UI font (docs/design/README.md, "Type and icons"): Barlow (OFL) for client-drawn UI labels,
 * shipped as TTF font providers under `assets/imperiumclient/font/` with the vanilla font as the
 * fallback provider, so any glyph Barlow lacks (translations, symbols) renders normally.
 *
 * Text is drawn by styling a component with the font id ([net.minecraft.network.chat.Style.withFont]);
 * the game's font renderer resolves the id per glyph, so plain `Font.width(String)` does NOT see
 * Barlow metrics - always measure through [width]/[truncate] here.
 *
 * HUD text, keybind chips and in-world numbers stay on the Minecraft font (spec); the wordmark
 * keeps its tracked-caps treatment.
 */
object ImperiumFonts {
	/** Barlow Regular: body labels (nav items, card names, setting rows, descriptions). */
	@JvmField
	val BARLOW: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "barlow")

	/** Barlow SemiBold: headings, chips and button labels. */
	@JvmField
	val BARLOW_SEMIBOLD: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "barlow_semibold")

	@JvmStatic
	@JvmOverloads
	fun label(text: String, font: Identifier = BARLOW): Component =
		Component.literal(text).withStyle { style -> style.withFont(FontDescription.Resource(font)) }

	@JvmStatic
	fun semibold(text: String): Component = label(text, BARLOW_SEMIBOLD)

	/** Width of [text] in the given font's metrics (not the default font's). */
	@JvmStatic
	@JvmOverloads
	fun width(font: Font, text: String, fontId: Identifier = BARLOW): Int =
		font.width(label(text, fontId))

	/**
	 * The longest prefix of [text] that fits [maxWidth] in the given font. `Font.plainSubstrByWidth`
	 * slices by the default font's metrics, which drifts on wider UI glyphs, so slice here.
	 */
	@JvmStatic
	@JvmOverloads
	fun truncate(font: Font, text: String, maxWidth: Int, fontId: Identifier = BARLOW): String {
		if (maxWidth <= 0) return ""
		if (width(font, text, fontId) <= maxWidth) return text
		var fit = text
		while (fit.isNotEmpty() && width(font, fit, fontId) > maxWidth) {
			fit = fit.dropLast(1)
		}
		return fit
	}
}
