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

import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.waypoints.WaypointManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import io.github.axolotlclient.modules.waypoints.Waypoint
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Square/circular chunk-colour minimap HUD (Alpine "minimap" module, parity spec M3).
 *
 * Rendering: [MinimapTexture] keeps a NativeImage-backed dynamic texture of
 * top-block `MapColor`s (vanilla map palette, slope- and water-shaded), drawn as a single
 * scaled blit that rotates with the camera in follow-camera mode. A second small dynamic
 * texture acts as the anti-aliased circular mask ring when the circle shape is selected.
 * Waypoints from the shared WaypointManager (read-only) are projected onto the map as
 * dots, with a label for the nearest one.
 *
 * Out of scope for this pass (follow-up): a fullscreen map view keybind.
 */
class MinimapHud : BoxHudEntry(DEFAULT_SIZE, DEFAULT_SIZE, true), DynamicallyPositionable {

	/**
	 * The map follows the camera every frame, so this entry always re-renders live and is
	 * never served from the `hud_frame_limit` recording.
	 */
	override fun dynamicContent(): Boolean = true

	@JvmField
	val shape = EnumOption("minimap.shape", Shape::class.java, Shape.SQUARE)
	@JvmField
	val rotation = EnumOption("minimap.rotation", Rotation::class.java, Rotation.NORTH_LOCK)
	@JvmField
	val zoom = IntegerOption("minimap.zoom", 2, 1, 8)
	@JvmField
	val mapSize = IntegerOption("minimap.size", DEFAULT_SIZE, 64, 256)
	@JvmField
	val opacity = IntegerOption("minimap.opacity", 100, 10, 100)
	@JvmField
	val showWaypoints = BooleanOption("minimap.show_waypoints", true)
	@JvmField
	val waypointLabels = BooleanOption("minimap.waypoint_labels", true)
	@JvmField
	val showNorth = BooleanOption("minimap.show_north", true)

	private val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(AnchorPoint.TOP_RIGHT, this)
	private val mc: Minecraft = super.client as Minecraft
	val texture: MinimapTexture = MinimapTexture()

	private var maskVersion = 0
	private var maskTexture: DynamicTexture? = null
	private var bakedMaskHash = -1
	private var registered = false

	override fun getId(): Identifier = ID

	override fun init() {
		ClientChunkEvents.CHUNK_LOAD.register { _, chunk ->
			if (isEnabled()) {
				texture.markChunkDirty(chunk.pos)
			}
		}
	}

	override fun tickable(): Boolean = true

	override fun tick() {
		val player: LocalPlayer? = mc.player
		if (player == null) {
			return
		}
		syncBounds()
		texture.zoom = zoom.get()
		val grid = requiredGrid()
		if (grid != texture.gridSize) {
			texture.rebuild(grid)
			registered = false
		}
		texture.tick(player.level() as ClientLevel, player.x, player.z)
	}

	override fun render(ctx: AxoRenderContext, delta: Float) {
		if (shape.get() == Shape.CIRCLE) {
			// Circle mode: circular backing instead of the square background plate;
			// the rim comes from the baked mask ring, so the square outline is skipped.
			ctx.`br$pushMatrix`()
			scale(ctx)
			if (background!!.get() && backgroundColor!!.get().alpha > 0) {
				ctx.`br$fillRectRound`(getBounds(), backgroundColor!!.get(), minOf(getWidth(), getHeight()) / 2f)
			}
			renderComponent(ctx, delta)
			ctx.`br$popMatrix`()
		} else {
			super.render(ctx, delta)
		}
	}

	override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
		syncBounds()
		val player: LocalPlayer? = mc.player
		if (player == null || mc.level == null || texture.texture == null) {
			renderEmpty(ctx)
			return
		}
		ensureRegistered()
		if (texture.isUploadPending()) {
			texture.texture!!.upload()
			texture.clearUploadPending()
		}
		val graphics = ctx as GuiGraphicsExtractor
		val size = getContentWidth()
		val grid = texture.gridSize
		val pxPerTexel = size / grid.toFloat()
		val px = player.x
		val pz = player.z
		val yawDeg = Mth.rotLerp(delta, player.yRotO, player.yRot)
		val follow = rotation.get() == Rotation.FOLLOW_CAMERA
		// MC yaw: 0 = south (+Z). Rotating content by PI - yaw points the view direction up.
		val mapRot = if (follow) (Math.PI - Math.toRadians(yawDeg.toDouble())).toFloat() else 0.0f
		val fracX = (texture.playerOffsetX(px) * pxPerTexel).toFloat()
		val fracZ = (texture.playerOffsetZ(pz) * pxPerTexel).toFloat()

		val pos = contentPos
		val cx = pos.x() + size / 2
		val cy = pos.y() + size / 2
		val tint = ARGB.color((opacity.get() * 2.55f).toInt(), 0xFFFFFF)

		val scissor = shape.get() == Shape.SQUARE && follow
		if (scissor) {
			ctx.`br$pushScissor`(getTrueContentX(), getTrueContentY(), getTrueContentWidth(), getTrueContentHeight())
		}
		ctx.`br$pushMatrix`()
		ctx.`br$translateMatrix`(cx.toFloat(), cy.toFloat())
		ctx.`br$rotateMatrix`(mapRot)
		ctx.`br$translateMatrix`(-fracX, -fracZ)
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, texture.textureId(), -size / 2, -size / 2,
			0.0f, 0.0f, size, size, grid, grid, grid, grid, tint
		)
		if (showWaypoints.get()) {
			drawWaypointDots(ctx, pxPerTexel, (fracX / pxPerTexel).toDouble(), (fracZ / pxPerTexel).toDouble())
		}
		ctx.`br$popMatrix`()
		if (scissor) {
			ctx.`br$popScissor`()
		}

		if (showWaypoints.get() && waypointLabels.get()) {
			drawNearestWaypointLabel(
				ctx, cx, cy, size, pxPerTexel,
				(fracX / pxPerTexel).toDouble(), (fracZ / pxPerTexel).toDouble(), mapRot
			)
		}
		drawPlayerArrow(ctx, cx, cy, follow, yawDeg)
		if (showNorth.get()) {
			drawNorthIndicator(ctx, cx, cy, size, mapRot)
		}
		if (shape.get() == Shape.CIRCLE) {
			drawCircleMask(graphics, cx, cy)
		}
	}

	override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
		renderComponent(ctx, delta)
	}

	private fun renderEmpty(ctx: AxoRenderContext) {
		// No world yet (menu-time HUD editor): a filled placeholder square/circle.
		val size = getContentWidth()
		if (size <= 0) {
			return
		}
		val pos = contentPos
		if (shape.get() == Shape.CIRCLE) {
			ctx.`br$fillRectRound`(pos.x(), pos.y(), size, size, backgroundColor!!.get().toInt(), size / 2f)
		} else {
			ctx.`br$fillRect`(pos.x(), pos.y(), size, size, backgroundColor!!.get().toInt())
		}
	}

	private fun drawWaypointDots(ctx: AxoRenderContext, pxPerTexel: Float, fracTexelX: Double, fracTexelZ: Double) {
		val player = mc.player ?: return
		val dimension = WaypointsHud.currentDimension(player)
		val anchorX = texture.anchorX
		val anchorZ = texture.anchorZ
		val zoom = texture.zoom
		val half = texture.gridSize / 2 - 2
		val limit = if (shape.get() == Shape.CIRCLE) {
			maxOf(1, ((getContentWidth() / 2f) / pxPerTexel).toInt() - 2)
		} else {
			half
		}
		for (wp in WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled) {
				continue
			}
			val tx = (wp.x - anchorX) / zoom.toDouble() - fracTexelX
			val tz = (wp.z - anchorZ) / zoom.toDouble() - fracTexelZ
			if (abs(tx) > limit || abs(tz) > limit) {
				continue
			}
			if (shape.get() == Shape.CIRCLE && tx * tx + tz * tz > limit.toDouble() * limit) {
				continue
			}
			val x = Math.round(tx * pxPerTexel).toInt()
			val y = Math.round(tz * pxPerTexel).toInt()
			ctx.`br$fillRect`(x, y, 3, 3, 0xFF000000.toInt())          // 1px drop shadow
			ctx.`br$fillRect`(x - 1, y - 1, 3, 3, wp.color)
		}
	}

	private fun drawNearestWaypointLabel(
		ctx: AxoRenderContext, cx: Int, cy: Int, size: Int, pxPerTexel: Float,
		fracTexelX: Double, fracTexelZ: Double, mapRot: Float
	) {
		val player = mc.player ?: return
		val dimension = WaypointsHud.currentDimension(player)
		var nearest: Waypoint? = null
		var nearestDistSq = Double.MAX_VALUE
		for (wp in WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled) {
				continue
			}
			val dx = wp.x - player.x
			val dz = wp.z - player.z
			val distSq = dx * dx + dz * dz
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq
				nearest = wp
			}
		}
		nearest ?: return
		val zoom = texture.zoom
		val tx = (nearest.x - texture.anchorX) / zoom.toDouble() - fracTexelX
		val tz = (nearest.z - texture.anchorZ) / zoom.toDouble() - fracTexelZ
		val half = texture.gridSize / 2 - 2
		if (abs(tx) > half || abs(tz) > half) {
			return
		}
		val sx = (tx * pxPerTexel).toFloat()
		val sy = (tz * pxPerTexel).toFloat()
		// the label is drawn outside the rotated pose and scissor: apply the map rotation
		// manually, and keep it inside the visible window
		val cos = Mth.cos(mapRot.toDouble())
		val sin = Mth.sin(mapRot.toDouble())
		val dx = (sx * cos - sy * sin).toInt()
		val dy = (sx * sin + sy * cos).toInt()
		val limit = size / 2 - mc.font.lineHeight
		if (abs(dx) > limit || abs(dy) > limit) {
			return
		}
		val label = nearest.name
		ctx.`br$drawString`(
			label, cx + dx - mc.font.width(label) / 2, cy + dy - mc.font.lineHeight - 2,
			nearest.color, true
		)
	}

	private fun drawPlayerArrow(ctx: AxoRenderContext, cx: Int, cy: Int, follow: Boolean, yawDeg: Float) {
		// The arrow points where the player faces; on a rotating map that is always "up".
		val angle = if (follow) 0.0f else Math.toRadians(yawDeg.toDouble()).toFloat() + Math.PI.toFloat()
		ctx.`br$pushMatrix`()
		ctx.`br$rotateMatrixAround`(angle, cx + 0.5f, cy + 0.5f)
		drawArrowShape(ctx, cx + 1, cy + 1, 0xFF1A1A1A.toInt())
		drawArrowShape(ctx, cx, cy, 0xFFFFFFFF.toInt())
		ctx.`br$popMatrix`()
	}

	private fun drawArrowShape(ctx: AxoRenderContext, cx: Int, cy: Int, color: Int) {
		ctx.`br$fillRect`(cx, cy - 5, 1, 1, color)      // tip
		ctx.`br$fillRect`(cx - 1, cy - 4, 3, 1, color)
		ctx.`br$fillRect`(cx - 2, cy - 3, 5, 1, color)
		ctx.`br$fillRect`(cx - 1, cy - 2, 3, 1, color)
		ctx.`br$fillRect`(cx, cy - 1, 1, 4, color)      // shaft
	}

	private fun drawNorthIndicator(ctx: AxoRenderContext, cx: Int, cy: Int, size: Int, mapRot: Float) {
		// north (-Z) is "up" in content space; project it through the map rotation
		val dirX = Mth.sin(mapRot.toDouble())
		val dirY = -Mth.cos(mapRot.toDouble())
		val radius = size / 2 - 3
		val x = cx + (dirX * radius).toInt()
		val y = cy + (dirY * radius).toInt()
		ctx.`br$drawCenteredString`("N", x, y - mc.font.lineHeight / 2, 0xFFFFFFFF.toInt(), true)
	}

	private fun drawCircleMask(graphics: GuiGraphicsExtractor, cx: Int, cy: Int) {
		val outlineOn = outline!!.get()
		val hash = maskParamsHash(outlineOn)
		if (hash != bakedMaskHash) {
			bakeMask(outlineOn)
			bakedMaskHash = hash
		}
		if (maskTexture == null) {
			return
		}
		val dim = texture.gridSize
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, maskId(), cx - dim / 2, cy - dim / 2,
			0.0f, 0.0f, dim, dim, dim, dim, dim, dim, -1
		)
	}

	private fun maskId(): Identifier =
		Identifier.fromNamespaceAndPath("imperiumclient", "minimap/mask/v$maskVersion")

	private fun maskParamsHash(outlineOn: Boolean): Int {
		var result = texture.gridSize
		result = 31 * result + getContentWidth()
		result = 31 * result + if (outlineOn) outlineWidth!!.get() else 0
		result = 31 * result + if (outlineOn) outlineColor!!.get().toInt() else 0
		return result
	}

	/**
	 * Bakes the circle mask: transparent inside the circle, a ring in the outline colour along
	 * the rim (anti-aliased outer edge), transparent outside — so rotated square content
	 * poking past the rim is fully covered.
	 */
	private fun bakeMask(outlineOn: Boolean) {
		val dim = texture.gridSize
		val radius = getContentWidth() / 2
		val ring = if (outlineOn) minOf(outlineWidth!!.get(), radius - 1) else 0
		val ringColor = if (outlineOn) outlineColor!!.get().toInt() else 0xFFFFFFFF.toInt()
		maskTexture?.close() // DynamicTexture.close() releases its NativeImage as well
		val image = NativeImage(dim, dim, false)
		val center = dim / 2f
		for (y in 0 until dim) {
			for (x in 0 until dim) {
				val dx = x + 0.5f - center
				val dy = y + 0.5f - center
				val dist = sqrt(dx * dx + dy * dy).toFloat()
				var color = 0
				if (dist >= radius - ring && dist < radius) {
					color = ringColor
				} else if (dist >= radius && dist < radius + 1.0f) {
					color = ARGB.multiplyAlpha(ringColor, radius + 1.0f - dist)
				}
				image.setPixel(x, y, color)
			}
		}
		maskVersion++
		val mask = DynamicTexture({ maskId().toString() }, image)
		maskTexture = mask
		mc.textureManager.register(maskId(), mask)
		mask.upload()
	}

	private fun ensureRegistered() {
		if (registered) {
			return
		}
		mc.textureManager.register(texture.textureId(), texture.texture!!)
		texture.texture!!.upload()
		registered = true
	}

	private fun requiredGrid(): Int {
		val size = mapSize.get()
		if (shape.get() == Shape.SQUARE && rotation.get() == Rotation.FOLLOW_CAMERA) {
			// the diagonal of the rotated window must stay inside the sampled grid
			return Mth.ceil(size * ROTATION_OVERSIZE)
		}
		return size
	}

	private fun syncBounds() {
		val size = mapSize.get()
		if (getContentWidth() != size || getContentHeight() != size) {
			setContentWidth(size)
			setContentHeight(size)
			setWidth(size)
			setHeight(size)
			onBoundsUpdate()
		}
	}

	override fun getConfigurationOptions(): MutableList<Option<*>> {
		val options = super.getConfigurationOptions()
		options.add(shape)
		options.add(rotation)
		options.add(zoom)
		options.add(mapSize)
		options.add(opacity)
		options.add(showWaypoints)
		options.add(waypointLabels)
		options.add(showNorth)
		options.add(anchor)
		return options
	}

	override fun getAnchor(): AnchorPoint = anchor.get()

	override fun getDefaultX(): Double = 1.0

	/** True once the map texture has been registered with the texture manager (render path ran). */
	fun isRegistered(): Boolean = registered

	enum class Shape {
		SQUARE,
		CIRCLE;

		override fun toString(): String = "minimap.shape." + name.lowercase(Locale.ROOT)
	}

	enum class Rotation {
		NORTH_LOCK,
		FOLLOW_CAMERA;

		override fun toString(): String = "minimap.rotation." + name.lowercase(Locale.ROOT)
	}

	companion object {
		@JvmField
		val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "minimaphud")

		private const val DEFAULT_SIZE = 128

		/** Extra content sampled beyond the window so a rotating square map has no hollow corners. */
		private const val ROTATION_OVERSIZE = 1.5f
	}
}
