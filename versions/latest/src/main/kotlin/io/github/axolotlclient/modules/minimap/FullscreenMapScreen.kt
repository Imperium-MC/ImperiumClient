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

package io.github.axolotlclient.modules.minimap

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.imperium.menu.CarvedTheme
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud
import io.github.axolotlclient.modules.waypoints.WaypointManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * The fullscreen map (Alpine "minimap" large view): the whole screen is a north-locked
 * chunk-colour map, pannable by dragging, zoomable with the scroll wheel, with waypoint
 * dots + labels and the player marker.
 *
 * The terrain is sampled by the module's shared [MinimapTexture] (a second instance
 * of the same sampler the HUD minimap uses, at a coarser zoom) — this screen never samples
 * blocks itself. The screen owns only view state: the world-space centre of the view, the
 * drag, and the overlay chrome (carved info panel, per `ImperiumMenuScreen`
 * conventions).
 *
 * Geometry: the sampler grid is a square of texels anchored at a zoom-quantised world
 * position; the screen maps world coordinates to pixels as
 * `px = width/2 + (wx - centerX) * pixelsPerBlock`, with
 * `pixelsPerBlock = screenScale / zoom` (screen scale upscales each texel so the fixed
 * grid always covers the window). Dragging and scrolling only move the view centre / zoom;
 * the texture recentres itself on the next tick.
 */
class FullscreenMapScreen : Screen(Component.translatable("imperiumclient.fullscreenmap.title")) {

	val mapTexture: MinimapTexture = FullscreenMap.getInstance().mapTexture

	/** World-space centre of the view (starts on the player, moves while dragging). */
	var viewCenterX: Double = 0.0
		private set
	var viewCenterZ: Double = 0.0
		private set

	private var registeredTextureId = ""

	/** Waypoint dots drawn by the most recent frame (gametest evidence). */
	var drawnWaypoints: Int = 0
		private set

	/** A left-button drag is in progress (set by mouseClicked, cleared by mouseReleased). */
	private var dragging = false

	init {
		val player = Minecraft.getInstance().player
		if (player != null) {
			viewCenterX = player.x
			viewCenterZ = player.z
		}
	}

	override fun init() {
		mapTexture.rebuild(GRID_SIZE)
	}

	override fun tick() {
		val client = Minecraft.getInstance()
		val level = client.level ?: return
		mapTexture.zoom = FullscreenMap.getInstance().zoom.get()
		if (mapTexture.gridSize != GRID_SIZE) {
			mapTexture.rebuild(GRID_SIZE)
		}
		// The view centre plays the player's role for the sampler: the grid recentres on it.
		mapTexture.tick(level, viewCenterX, viewCenterZ)
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractBackground(graphics, mouseX, mouseY, delta)
		// Dark backdrop behind the (partly unloaded) map area, like every large-map client.
		graphics.fill(0, 0, width, height, 0xF0100E0C.toInt())
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		val client = Minecraft.getInstance()
		val player: LocalPlayer? = client.player
		if (player == null || client.level == null) {
			return
		}
		val registered: DynamicTexture = mapTexture.texture ?: return
		ensureRegistered(client, registered)

		val grid = mapTexture.gridSize
		val anchorX = mapTexture.anchorX
		val anchorZ = mapTexture.anchorZ
		val zoom = mapTexture.zoom.toDouble()
		val scale = max(1.0, ceil(max(width, height) / grid.toDouble()))
		val drawSize = ceil(grid * scale).toInt()
		// Screen position of the grid's top-left texel: the grid centre sits at the anchor,
		// and fractional anchor-vs-view-centre offset is absorbed here so panning is smooth.
		val blitX = floor(width / 2.0 - (grid / 2.0 + (viewCenterX - anchorX) / zoom) * scale).toInt()
		val blitY = floor(height / 2.0 - (grid / 2.0 + (viewCenterZ - anchorZ) / zoom) * scale).toInt()
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, mapTexture.textureId(), blitX, blitY,
			0.0f, 0.0f, drawSize, drawSize, grid, grid, grid, grid, -1
		)

		drawWaypoints(graphics, player, anchorX, anchorZ, zoom, scale, blitX, blitY)
		drawPlayerMarker(graphics, player, anchorX, anchorZ, zoom, scale, blitX, blitY)
		drawInfoPanel(graphics, player)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Accepting the click starts a map drag; drags are only delivered for buttons the
			// screen accepted (same contract the HUD editor relies on).
			dragging = true
			return true
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
			dragging = false
			return true
		}
		return super.mouseReleased(event)
	}

	override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Dragging the map content right means looking further west; scale is px/texel.
			val scale = max(1.0, ceil(max(width, height) / mapTexture.gridSize.toDouble()))
			val pixelsPerBlock = scale / mapTexture.zoom
			viewCenterX -= deltaX * pixelsPerBlock
			viewCenterZ -= deltaY * pixelsPerBlock
			return true
		}
		return super.mouseDragged(event, deltaX, deltaY)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (scrollY != 0.0) {
			val zoomOption = FullscreenMap.getInstance().zoom
			zoomOption.set(
				Mth.clamp(zoomOption.get() + (if (scrollY > 0) -1 else 1), zoomOption.min, zoomOption.max)
			)
			return true
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() == InputConstants.KEY_M) {
			onClose()
			return true
		}
		return super.keyPressed(event)
	}

	override fun onClose() {
		Minecraft.getInstance().gui.setScreen(null)
	}

	override fun isPauseScreen(): Boolean = false

	private fun ensureRegistered(client: Minecraft, registered: DynamicTexture) {
		val id = mapTexture.textureId().toString()
		if (id != registeredTextureId) {
			client.textureManager.register(mapTexture.textureId(), registered)
			registeredTextureId = id
		}
		if (mapTexture.isUploadPending()) {
			mapTexture.texture!!.upload()
			mapTexture.clearUploadPending()
		}
	}

	private fun drawWaypoints(
		graphics: GuiGraphicsExtractor, player: LocalPlayer, anchorX: Long, anchorZ: Long,
		zoom: Double, scale: Double, blitX: Int, blitY: Int
	) {
		drawnWaypoints = 0
		if (!FullscreenMap.getInstance().showWaypoints.get()) {
			return
		}
		val labels = FullscreenMap.getInstance().waypointLabels.get()
		val dimension = WaypointsHud.currentDimension(player)
		for (wp in WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled) {
				continue
			}
			val x = worldToScreenX(wp.x, anchorX, zoom, scale, blitX)
			val y = worldToScreenZ(wp.z, anchorZ, zoom, scale, blitY)
			if (x < -8 || y < -8 || x > width + 8 || y > height + 8) {
				continue
			}
			// dot with a 1px drop shadow, like the HUD minimap's dots
			graphics.fill(x, y, x + 4, y + 4, 0x99000000.toInt())
			graphics.fill(x - 1, y - 1, x + 3, y + 3, wp.color)
			if (labels) {
				val dist = hypot(wp.x - player.x, wp.z - player.z).toInt()
				val label = wp.name + " (" + dist + "m)"
				graphics.text(font, label, x + 6, y - 3, wp.color, true)
			}
			drawnWaypoints++
		}
	}

	private fun drawPlayerMarker(
		graphics: GuiGraphicsExtractor, player: LocalPlayer, anchorX: Long, anchorZ: Long,
		zoom: Double, scale: Double, blitX: Int, blitY: Int
	) {
		val x = worldToScreenX(player.x, anchorX, zoom, scale, blitX)
		val y = worldToScreenZ(player.z, anchorZ, zoom, scale, blitY)
		// MC yaw: 0 = south (+Z = screen down); the arrow shape points north (up) at rest.
		val angle = (Math.PI - Math.toRadians(player.yRot.toDouble())).toFloat()
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(x + 1.5f, y + 1.5f)
		pose.rotate(angle)
		graphics.fill(-1, -6, 0, -5, 0xFF1A1A1A.toInt())
		graphics.fill(-2, -5, 1, -4, 0xFF1A1A1A.toInt())
		graphics.fill(-3, -4, 2, -3, 0xFF1A1A1A.toInt())
		graphics.fill(-1, -3, 0, 3, 0xFF1A1A1A.toInt())
		graphics.fill(0, -7, 1, -6, 0xFFFFFFFF.toInt())
		graphics.fill(-1, -6, 2, -5, 0xFFFFFFFF.toInt())
		graphics.fill(-2, -5, 3, -4, 0xFFFFFFFF.toInt())
		graphics.fill(0, -4, 1, 4, 0xFFFFFFFF.toInt())
		pose.popMatrix()
	}

	private fun drawInfoPanel(graphics: GuiGraphicsExtractor, player: LocalPlayer) {
		val panelW = 170
		val panelH = 52
		CarvedTheme.carvedPanel(graphics, 8, 8, panelW, panelH)
		CarvedTheme.engravedCaps(graphics, font, title.getString(), 18, 16, CarvedTheme.BRONZE_LIGHT)
		val coords = floor(viewCenterX).toInt().toString() + " / " + floor(player.y).toInt() + " / " +
			floor(viewCenterZ).toInt()
		graphics.text(font, coords, 18, 28, CarvedTheme.MARBLE, false)
		graphics.text(
			font,
			Component.translatable("imperiumclient.fullscreenmap.hint", FullscreenMap.getInstance().zoom.get()),
			18, 40, CarvedTheme.MUTED, false
		)
	}

	/** Screen x of a world x, in the current frame's grid/scale parameters. */
	@VisibleForTesting
	internal fun worldToScreenX(worldX: Double, anchorX: Long, zoom: Double, scale: Double, blitX: Int): Int =
		Math.round(blitX + ((worldX - anchorX) / zoom + mapTexture.gridSize / 2.0) * scale).toInt()

	/** Screen y of a world z, in the current frame's grid/scale parameters. */
	@VisibleForTesting
	internal fun worldToScreenZ(worldZ: Double, anchorZ: Long, zoom: Double, scale: Double, blitY: Int): Int =
		Math.round(blitY + ((worldZ - anchorZ) / zoom + mapTexture.gridSize / 2.0) * scale).toInt()

	companion object {
		/** Sampler grid side in texels; 256x256 fills within the per-tick sampling budget. */
		private const val GRID_SIZE = 256
	}
}
