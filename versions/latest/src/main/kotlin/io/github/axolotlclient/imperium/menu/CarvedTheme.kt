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

package io.github.axolotlclient.imperium.menu

import java.util.Locale

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.sprite.SpriteId
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * The carved imperial visual identity (W4-A): chiseled stone panels, bronze hairlines and engraved
 * capital type, so the module menu reads as part of the game world rather than a floating web UI.
 *
 * Everything here is flat pixel-grid work — 1px inner outlines over 2px bevel edges — layered on
 * the vanilla deepslate-tiles block sprite. No glow, no gradients, no rounding: the "carve" comes
 * from light catching the bottom/right edge of a recess and the top/left edge of a raised tile.
 *
 * The brand ramp mirrors the website and launcher: bronze `#cea464`, light `#e0d1b8`,
 * dark bronze `#a8823f`, marble `#ece7df`, muted `#a78b62`.
 */
object CarvedTheme {
	// brand ramp
	const val BRONZE = 0xFFCEA464.toInt()
	const val BRONZE_LIGHT = 0xFFE0D1B8.toInt()
	const val BRONZE_DARK = 0xFFA8823F.toInt()
	const val MARBLE = 0xFFECE7DF.toInt()
	const val MUTED = 0xFFA78B62.toInt()

	// stone
	private const val PANEL_BASE = 0xFF17130F.toInt()
	private const val STONE_OVERLAY = 0xD814110E.toInt()
	private const val EDGE = 0xFF0B0908.toInt()
	private const val INNER_SHADOW = 0xFF080706.toInt()
	private const val STONE_LIGHT = 0xFF3D352A.toInt()
	private const val TILE = 0xFF211C18.toInt()
	private const val TILE_HOVER = 0xFF2B241D.toInt()
	private const val TOGGLE_OFF_TRACK = 0xFF2E2921.toInt()
	private const val TOGGLE_OFF_KNOB = 0xFF9C948A.toInt()

	private val STONE_TILE = Identifier.withDefaultNamespace("block/deepslate_tiles")

	/**
	 * Tiles the vanilla deepslate-tiles block sprite across the area and lays the warm dark stone
	 * overlay on top, so the texture reads as a chiseled panel instead of a bright block texture.
	 * Only whole 16px cells are drawn (a squeezed edge cell would smear), so callers should fill a
	 * base colour first.
	 */
	@JvmStatic
	fun drawStoneFill(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
		val sprite = graphics.getSprite(SpriteId(TextureAtlas.LOCATION_BLOCKS, STONE_TILE))
		var ty = y
		while (ty + 16 <= y + height) {
			var tx = x
			while (tx + 16 <= x + width) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tx, ty, 16, 16)
				tx += 16
			}
			ty += 16
		}
		graphics.fill(x, y, x + width, y + height, STONE_OVERLAY)
	}

	/**
	 * A panel recessed into the stone: dark 1px outline, shadowed top/left inner edge and a faint
	 * catch-light on bottom/right, over the tiled deepslate body.
	 */
	@JvmStatic
	fun carvedPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
		graphics.fill(x, y, x + width, y + height, PANEL_BASE)
		drawStoneFill(graphics, x, y, width, height)
		outline(graphics, x, y, width, height, EDGE)
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, INNER_SHADOW)
		graphics.fill(x + 1, y + 2, x + 2, y + height - 1, INNER_SHADOW)
		graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, STONE_LIGHT)
		graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, STONE_LIGHT)
		// corner studs: 2px bronze squares set into the frame
		graphics.fill(x + 2, y + 2, x + 4, y + 4, BRONZE_DARK)
		graphics.fill(x + width - 4, y + 2, x + width - 2, y + 4, BRONZE_DARK)
		graphics.fill(x + 2, y + height - 4, x + 4, y + height - 2, BRONZE_DARK)
		graphics.fill(x + width - 4, y + height - 4, x + width - 2, y + height - 2, BRONZE_DARK)
	}

	/**
	 * A raised relief tile (the website's plinth pattern): light top/left bevel, dark bottom/right,
	 * flat stone body. Enabled modules get a gilded double frame in the bronze ramp instead of the
	 * plain stone outline.
	 */
	@JvmStatic
	fun raisedTile(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, hovered: Boolean, gilded: Boolean) {
		val body = if (hovered) TILE_HOVER else TILE
		val bevel = if (hovered) 0xFF453B2F.toInt() else STONE_LIGHT
		graphics.fill(x, y, x + width, y + height, body)
		if (gilded) {
			outline(graphics, x, y, width, height, BRONZE_DARK)
			outline(graphics, x + 1, y + 1, width - 2, height - 2, if (hovered) BRONZE else 0xFF8F6F35.toInt())
			graphics.fill(x + 2, y + 2, x + width - 2, y + 3, bevel)
			graphics.fill(x + 2, y + 3, x + 3, y + height - 3, bevel)
			graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, INNER_SHADOW)
			graphics.fill(x + width - 3, y + 3, x + width - 2, y + height - 3, INNER_SHADOW)
		} else {
			outline(graphics, x, y, width, height, EDGE)
			graphics.fill(x + 1, y + 1, x + width - 1, y + 2, bevel)
			graphics.fill(x + 1, y + 2, x + 2, y + height - 2, bevel)
			graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, INNER_SHADOW)
			graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, INNER_SHADOW)
		}
	}

	/**
	 * An empty icon slot: a small square recess so cards without an item mapping keep the same
	 * structured grid instead of collapsing their left column.
	 */
	@JvmStatic
	fun carvedSlot(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int) {
		graphics.fill(x, y, x + size, y + size, 0xFF151210.toInt())
		outline(graphics, x, y, size, size, INNER_SHADOW)
		graphics.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, STONE_LIGHT)
		graphics.fill(x + size - 2, y + 1, x + size - 1, y + size - 1, STONE_LIGHT)
	}

	/**
	 * A two-tone rule separating sections: 1px shadow line under 1px bronze hairline, inset so it
	 * meets the panel frame cleanly.
	 */
	@JvmStatic
	fun carvedRule(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
		graphics.fill(x, y, x + width, y + 1, EDGE)
		graphics.fill(x + 2, y + 1, x + width - 2, y + 2, BRONZE_DARK)
	}

	/**
	 * Engraved text: the dark cut first (offset one pixel down-right), the raised face on top.
	 */
	@JvmStatic
	fun engravedText(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, face: Int) {
		graphics.text(font, text, x + 1, y + 1, 0xFF12100D.toInt(), false)
		graphics.text(font, text, x, y, face, false)
	}

	/**
	 * Engraved capital type with one pixel of letter tracking — the "carved plaque" header voice.
	 */
	@JvmStatic
	fun engravedCaps(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, face: Int) {
		val caps = text.uppercase(Locale.ROOT)
		var cx = x + 1
		for (i in caps.indices) {
			val c = caps[i].toString()
			graphics.text(font, c, cx, y + 1, 0xFF12100D.toInt(), false)
			graphics.text(font, c, cx - 1, y, face, false)
			cx += font.width(c) + 1
		}
	}

	/**
	 * Width of [engravedCaps] output, including tracking, for right-alignment.
	 */
	@JvmStatic
	fun capsWidth(font: Font, text: String): Int {
		val caps = text.uppercase(Locale.ROOT)
		var w = 0
		for (i in caps.indices) {
			w += font.width(caps[i].toString()) + 1
		}
		return maxOf(0, w - 1)
	}

	/**
	 * The bronze-and-marble toggle: a stone track with a marble knob, square pixel corners. The
	 * enabled track is dark bronze with a bronze face line so "on" reads without any glow.
	 */
	@JvmStatic
	fun carvedToggle(graphics: GuiGraphicsExtractor, x: Int, y: Int, on: Boolean) {
		graphics.fill(x, y, x + 22, y + 10, if (on) BRONZE_DARK else TOGGLE_OFF_TRACK)
		outline(graphics, x, y, 22, 10, EDGE)
		if (on) {
			graphics.fill(x + 1, y + 1, x + 21, y + 2, BRONZE)
		}
		val kx = if (on) x + 12 else x + 2
		graphics.fill(kx, y + 2, kx + 8, y + 10, if (on) MARBLE else TOGGLE_OFF_KNOB)
		graphics.fill(kx, y + 2, kx + 8, y + 3, if (on) 0xFFF7F3EC.toInt() else 0xFFABA397.toInt())
		graphics.fill(kx, y + 9, kx + 8, y + 10, EDGE)
	}

	@JvmStatic
	fun outline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
		graphics.fill(x, y, x + width, y + 1, color)
		graphics.fill(x, y + height - 1, x + width, y + height, color)
		graphics.fill(x, y + 1, x + 1, y + height - 1, color)
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color)
	}

	/**
	 * The item sprite shown in a card's icon slot for modules that map naturally onto an item
	 * (fishing rod for auto-fish, map for the minimap, clock for time...). Modules without a solid
	 * mapping get the empty carved slot instead — no filler glyphs.
	 */
	@JvmStatic
	fun iconFor(entry: MenuEntry): ItemStack? {
		val id = (entry.id() + " " + entry.name()).lowercase(Locale.ROOT)
		if (contains(id, "autofish", "auto_fish", "auto fish")) return ItemStack(Items.FISHING_ROD)
		if (contains(id, "minimap")) return ItemStack(Items.FILLED_MAP)
		if (contains(id, "zoom")) return ItemStack(Items.SPYGLASS)
		if (contains(id, "time")) return ItemStack(Items.CLOCK)
		if (contains(id, "weather", "rain", "snow")) return ItemStack(Items.SNOWBALL)
		if (contains(id, "nametag")) return ItemStack(Items.NAME_TAG)
		if (contains(id, "outline")) return ItemStack(Items.SPECTRAL_ARROW)
		if (contains(id, "fullbright", "brightness")) return ItemStack(Items.GLOWSTONE)
		if (contains(id, "waypoint")) return ItemStack(Items.RECOVERY_COMPASS)
		if (contains(id, "keystroke")) return ItemStack(Items.STONE_BUTTON)
		if (contains(id, "compass")) return ItemStack(Items.COMPASS)
		if (contains(id, "crosshair")) return ItemStack(Items.TARGET)
		if (contains(id, "bossbar", "boss bar")) return ItemStack(Items.NETHER_STAR)
		if (contains(id, "coordinate", "coords")) return ItemStack(Items.MAP)
		if (contains(id, "armor")) return ItemStack(Items.IRON_CHESTPLATE)
		if (contains(id, "food", "hunger")) return ItemStack(Items.COOKED_BEEF)
		if (contains(id, "health", "heart")) return ItemStack(Items.GOLDEN_APPLE)
		if (contains(id, "arrow")) return ItemStack(Items.ARROW)
		if (contains(id, "experience", " xp")) return ItemStack(Items.EXPERIENCE_BOTTLE)
		if (contains(id, "tnt")) return ItemStack(Items.TNT)
		if (contains(id, "farming", "crop")) return ItemStack(Items.WHEAT)
		if (contains(id, "inventory")) return ItemStack(Items.HOPPER)
		if (contains(id, "chat")) return ItemStack(Items.WRITABLE_BOOK)
		return null
	}

	private fun contains(haystack: String, vararg needles: String): Boolean {
		for (needle in needles) {
			if (haystack.contains(needle)) return true
		}
		return false
	}
}
