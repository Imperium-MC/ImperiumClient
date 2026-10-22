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

import io.github.axolotlclient.bridge.render.AxoRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

/**
 * The Imperium theme (UI v3): every colour token from `docs/design/mockups/imperium-ui.css`,
 * as ARGB ints ready for `fill`. The spec's measurements are CSS pixels in a 1920x1080 mockup,
 * which equals GUI pixels at 1080p / GUI scale 2 — so every size in the v3 shell is the CSS
 * value halved, and item icons land on their native 16px sprite size.
 *
 * CARVED and CLASSIC remain selectable fallbacks; this object only serves the IMPERIUM theme.
 * Sizes live next to their components (see ImperiumMenuScreen); only colours and the small
 * pixel-glyph set (the spec's Material Symbols shapes, hand-drawn because no icon font may
 * ship) live here.
 */
object ImperiumTheme {
	// brand
	const val ACCENT = 0xFFCCAF83.toInt()
	const val ACCENT_HOT = 0xFFFCEEBF.toInt()

	// surfaces
	const val SURFACE = 0xE0101B36.toInt()          // rgba(11, 9, 7, .88)
	const val LAYER = 0x6B1E2B4E.toInt()            // rgba(23, 18, 12, .42)
	const val PANEL_BASE = 0xFF182647.toInt()
	const val PANEL_RAISED = 0xFF24365F.toInt()
	const val FIELD = 0xFF0B1526.toInt()
	const val CHIP = 0xFF2A3C66.toInt()

	// component ramp
	const val COMPONENT_BASE = 0xFF8E96A6.toInt()
	const val COMPONENT = 0x338E96A6.toInt()        // rgba(154, 132, 102, .2)
	const val COMPONENT_BG = 0x8C24365F.toInt()     // rgba(42, 33, 24, .55)
	const val TRACK = 0x99414E6E.toInt()            // rgba(84, 69, 47, .6)

	// primary / accent fills
	const val PRIMARY = 0x40CCAF83.toInt()          // accent at 25 %
	const val PRIMARY_HOVER = 0x59CCAF83.toInt()    // accent at 35 %
	const val SECONDARY = 0x26CCAF83.toInt()        // accent at 15 %
	const val ACCENT_SOFT = 0x4DCCAF83.toInt()      // accent at 30 %
	const val ACCENT_HALF = 0x80CCAF83.toInt()      // accent at 50 %

	// text
	const val TEXT = 0xFFFAFBFD.toInt()
	const val TEXT_SECONDARY = 0xC7D9DEE8.toInt()   // rgba(232, 220, 200, .78)
	const val TEXT_TERTIARY = 0xB3B8C2CE.toInt()    // rgba(216, 204, 184, .7)
	const val TEXT_DISABLED = 0xBF9AA4B2.toInt()    // rgba(176, 162, 140, .75)
	const val TEXT_ACCENT = 0xFFD9C39A.toInt()

	// overlay controls (title screen, HUD editor)
	const val OVERLAY_TEXT = 0xFFFFFFFF.toInt()
	const val OVERLAY_TEXT_SECONDARY = 0xC7DFE5EE.toInt()
	const val OVERLAY_CONTROL = 0x6B1E2B4E.toInt()
	const val OVERLAY_CONTROL_HOVER = 0x40CCAF83.toInt()
	const val OVERLAY_BORDER = 0x29FCEEBF.toInt()
	const val OVERLAY_BORDER_HOVER = 0x73CCAF83.toInt()
	const val OVERLAY_GLOW = 0x59CCAF83.toInt()
	const val OVERLAY_SHADOW = 0x80000000.toInt()

	// status
	const val SUCCESS = 0xFF5BD06E.toInt()
	const val ERROR = 0xFFE2603A.toInt()

	// module cards (docs/design/mockups/imperium-ui.css .card*)
	const val CARD_BG = 0x52182647.toInt()          // rgba(21, 17, 12, .32)
	const val CARD_HOVER_BG = 0xA624365F.toInt()    // rgba(34, 26, 17, .65)
	const val CARD_ON_BG = 0x21CCAF83.toInt()       // accent at 13 %
	const val CARD_ON_BORDER = 0x8CCCAF83.toInt()   // accent at 55 %
	const val CARD_BLOCKED_BG = 0x1AE2603A.toInt()  // error at 10 %
	const val CARD_BLOCKED_BORDER = 0x73E2603A.toInt() // error at 45 %
	const val CARD_BORDER = 0x298E96A6.toInt()      // component at 16 %
	const val ICON_WELL = 0x248E96A6.toInt()        // component at 14 %
	const val ICON_WELL_ON = 0x38CCAF83.toInt()     // accent at 22 %
	const val ICON_WELL_BLOCKED = 0x29E2603A.toInt() // error at 16 %
	const val BLOCKED_TAG = 0xFFF0A088.toInt()

	// setting rows (.row*)
	const val ROW_BG = 0x61182647.toInt()           // rgba(21, 17, 12, .38)
	const val ROW_HOVER_BG = 0x9924365F.toInt()     // rgba(34, 26, 17, .6)
	const val ROW_FOUND_HALO = 0x26CCAF83.toInt()   // secondary ring

	/** The IMC emblem shipped as the mod icon, drawn scaled for the sidebar and title screen. */
	val EMBLEM: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "icon.png")

	// ── drawing helpers ─────────────────────────────────────────────────────────────────────

	/** Fills a rounded rectangle with smooth corners (bridge renderer, not fill-built). */
	@JvmStatic
	fun roundRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int, radius: Float) {
		(graphics as AxoRenderContext).`br$fillRectRound`(x, y, width, height, color, radius)
	}

	/** Outlines a rounded rectangle with a 1px smooth stroke. */
	@JvmStatic
	fun roundOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int, radius: Float) {
		(graphics as AxoRenderContext).`br$outlineRectRound`(x, y, width, height, color, radius)
	}

	/**
	 * Uppercase text with one pixel of letter tracking — the stand-in for the Cinzel wordmark:
	 * the Minecraft font in small caps reads closest to the mockup's serif capitals without
	 * shipping a new TTF (see the spec's font provisioning note).
	 */
	@JvmStatic
	fun trackedCaps(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, tracking: Int = 1) {
		val caps = text.uppercase()
		var cx = x
		for (i in caps.indices) {
			val c = caps[i].toString()
			if (c != " ") {
				graphics.text(font, c, cx, y, color, false)
			}
			cx += font.width(c) + tracking
		}
	}

	/** Width of [trackedCaps] output, for centring and right-alignment. */
	@JvmStatic
	fun trackedWidth(font: Font, text: String, tracking: Int = 1): Int {
		val caps = text.uppercase()
		var w = 0
		for (i in caps.indices) {
			w += font.width(caps[i].toString()) + tracking
		}
		return maxOf(0, w - tracking)
	}

	// ── pixel glyphs ────────────────────────────────────────────────────────────────────────

	/**
	 * A hand-drawn pixel glyph: one GUI pixel per `#` cell, drawn in the given colour. The v3
	 * spec calls for Material Symbols Rounded icons; those cannot ship as a font or baked sprites
	 * here, so the ~20 shapes the shell needs are drawn as pixel patterns instead — the same
	 * nearest-neighbour look the mockup's 24px icons have at GUI scale 2.
	 */
	@JvmStatic
	fun drawGlyph(graphics: GuiGraphicsExtractor, glyph: Array<String>, x: Int, y: Int, color: Int) {
		for (row in glyph.indices) {
			val line = glyph[row]
			for (col in line.indices) {
				if (line[col] == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color)
				}
			}
		}
	}

	/** Glyph height in GUI pixels, for vertical centring. */
	@JvmStatic
	fun glyphHeight(glyph: Array<String>): Int = glyph.size

	// Sidebar and segment icons, one per menu category.
	val GLYPH_ALL = arrayOf(
		".##..##.",
		".##..##.",
		"........",
		".##..##.",
		".##..##."
	)
	val GLYPH_HUD = arrayOf(
		"########",
		"#......#",
		"#......#",
		"#......#",
		"########",
		"...##...",
		"...##..."
	)
	val GLYPH_RENDER = arrayOf(
		"..######..",
		".#......#.",
		"#...##...#",
		"#...##...#",
		".#......#.",
		"..######.."
	)
	val GLYPH_CHAT = arrayOf(
		"..######..",
		".#......#.",
		"#........#",
		"#........#",
		".#......#.",
		"..###.....",
		"..##......"
	)
	val GLYPH_WORLD = arrayOf(
		"...####...",
		"..##..##..",
		".#..##..#.",
		"#...##...#",
		"####..####",
		"#...##...#",
		".#..##..#.",
		"..##..##..",
		"...####..."
	)
	val GLYPH_UTILITY = arrayOf(
		"...##.....",
		"##########",
		"......##..",
		"##########",
		"..##......",
		"##########"
	)
	val GLYPH_AUTOMATION = arrayOf(
		"..#....#..",
		".########.",
		"..######..",
		".###..###.",
		"###....###",
		"###....###",
		".###..###.",
		"..######..",
		".########.",
		"..#....#.."
	)
	val GLYPH_SERVER = arrayOf(
		"##########",
		"#.##......",
		"##########",
		"..........",
		"##########",
		"#.##......",
		"##########"
	)
	val GLYPH_PERSON = arrayOf(
		"..##..",
		"..##..",
		".####.",
		"######",
		".####.",
		".####."
	)

	// Card action glyphs (power / pin / settings) and state marks.
	val GLYPH_POWER = arrayOf(
		"..##..##..",
		".#..##..#.",
		"#...##...#",
		"#...##...#",
		"#...##...#",
		"#...##...#",
		"#........#",
		"#........#",
		".#......#.",
		"..######.."
	)
	val GLYPH_PIN = arrayOf(
		".#####.",
		".#####.",
		"..###..",
		"...#...",
		"...#...",
		"...#..."
	)
	val GLYPH_GEAR = GLYPH_AUTOMATION
	val GLYPH_BLOCK = arrayOf(
		"..#####..",
		".#.....#.",
		"#.#.....#",
		"#..#....#",
		"#...#...#",
		"#....#..#",
		"#.....#.#",
		".#.....#.",
		"..#####.."
	)
	val GLYPH_SEARCH = arrayOf(
		".#####...",
		"#.....#..",
		"#.....#..",
		"#.....#..",
		".#####...",
		"......#..",
		".......#.",
		"........#",
		"........#"
	)
	val GLYPH_BOLT = arrayOf(
		"..###",
		".###.",
		"###..",
		"####.",
		"..##.",
		".##..",
		"##...",
		"#...."
	)
	val GLYPH_RESET = arrayOf(
		"..#####..",
		".#....###",
		"#.....###",
		"#.......#",
		"#.......#",
		"#.......#",
		"#.......#",
		".#.....#.",
		"..#####.."
	)
	val GLYPH_BACK = arrayOf(
		"....#....",
		"...##....",
		"..###....",
		".#####..#",
		"#########",
		".#####..#",
		"..###....",
		"...##....",
		"....#...."
	)
	val GLYPH_STORE = arrayOf(
		"..####..",
		".#....#.",
		"##....##",
		"#......#",
		"#..##..#",
		"#..##..#",
		"#......#",
		".######."
	)
	val GLYPH_MENU = arrayOf(
		"########",
		"########",
		"........",
		"########",
		"########",
		"........",
		"########",
		"########"
	)
	val GLYPH_CHEVRON = arrayOf(
		"#######.",
		".#####..",
		"..###...",
		"...#...."
	)
}
