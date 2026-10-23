/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.waypoints

import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud
import io.github.axolotlclient.util.WorldProjection
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D waypoint tracers (Alpine "waypoints" parity, the row the parity sheet kept open because the
 * codebase had no projection math): every enabled waypoint of the current dimension is projected
 * through [WorldProjection] and drawn as an on-screen marker — a diamond in the waypoint's
 * colour plus a name/distance label at the projected position, clamped to the screen edges with an
 * arrow pointing the way when the waypoint is off-screen or behind the camera. The death point
 * keeps its distinct red colour ([Waypoint.DEATH_COLOR]) like every other waypoint rendering.
 *
 * [WaypointManager] is used strictly read-only; the tracer owns no waypoint state. The
 * toggle (`waypoints.tracers`) and the label options live on [WaypointsHud],
 * the module's existing option surface, so the tracer is on exactly when the waypoints module is.
 */
object WaypointTracers {

    /** Vertical lift of the marker over the stored (feet) position, so labels clear the ground. */
    private const val MARKER_Y_OFFSET = 1.0

    /** Inset of edge-clamped markers from the screen border, in GUI pixels. */
    private const val EDGE_MARGIN = 16

    /** Half-height of the diamond/arrow shapes (they are 2*SHAPE_HALF+1 px tall). */
    private const val SHAPE_HALF = 3
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Draws one marker per enabled waypoint of the player's dimension. Called from the HUD render
     * path (after the crosshair) so the camera holds this frame's position, rotation and FOV.
     */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (minecraft.level == null || player == null) {
            return
        }
        val entry = HudManager.getInstance().get(WaypointsHud.ID) as? WaypointsHud ?: return
        if (!entry.isEnabled() || !entry.tracers.get()) {
            return
        }

        val context: AxoRenderContext = graphics
        val font: Font = minecraft.font
        val projection = WorldProjection.capture(minecraft)
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val showDistance = entry.showDistance.get()

        for (waypoint in WaypointManager.getInstance().getForDimension(WaypointsHud.currentDimension(player))) {
            if (!waypoint.enabled) {
                continue
            }
            val dx = waypoint.x - player.x
            val dy = waypoint.y - player.y
            val dz = waypoint.z - player.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)

            val pos = projection.project(waypoint.x, waypoint.y + MARKER_Y_OFFSET, waypoint.z)
            if (pos.onScreen(width, height)) {
                val cx = pos.x.roundToInt()
                val cy = pos.y.roundToInt()
                drawDiamond(context, cx, cy, waypoint.color)
                drawLabel(graphics, font, cx, cy + SHAPE_HALF + 2, waypoint, distance, showDistance, true)
            } else {
                val clamped = WorldProjection.clampToEdges(pos, width, height, EDGE_MARGIN)
                val angle = WorldProjection.edgeDirection(pos, width, height)
                val cx = clamped.x.roundToInt()
                val cy = clamped.y.roundToInt()
                drawArrow(context, cx, cy, angle, waypoint.color)
                // Offset the label towards the screen centre so it never leaves the screen with
                // the marker: left edge labels go right of the arrow, top labels below it, etc.
                val lx = cx + (cos(angle) * -(SHAPE_HALF + 4)).roundToInt()
                val ly = cy + (sin(angle) * -(SHAPE_HALF + 4)).roundToInt()
                drawLabel(graphics, font, lx, ly, waypoint, distance, showDistance, false)
            }
        }
    }

    /**
     * Name in the waypoint colour, distance in white, drawn as one line. Centered under on-screen
     * markers; edge markers lead with the line away from the border.
     */
    private fun drawLabel(
        graphics: GuiGraphicsExtractor, font: Font, cx: Int, cy: Int,
        waypoint: Waypoint, distance: Double, showDistance: Boolean, centered: Boolean
    ) {
        val name = waypoint.name
        val distanceText = if (showDistance) " " + WaypointsHud.formatDistance(distance) else ""
        val nameWidth = font.width(name)
        val distanceWidth = if (distanceText.isEmpty()) 0 else font.width(distanceText)
        val x = if (centered) cx - (nameWidth + distanceWidth) / 2 else cx
        graphics.text(font, name, x, cy, waypoint.color, true)
        if (distanceText.isNotEmpty()) {
            graphics.text(font, distanceText, x + nameWidth, cy, dim(waypoint.color), true)
        }
    }

    /**
     * A filled diamond of stacked 1px rows, 7x7: row widths 1/3/5/7/5/3/1.
     */
    private fun drawDiamond(context: AxoRenderContext, cx: Int, cy: Int, color: Int) {
        for (dy in -SHAPE_HALF..SHAPE_HALF) {
            val half = SHAPE_HALF - abs(dy)
            context.`br$fillRect`(cx - half, cy + dy, half * 2 + 1, 1, color)
        }
    }

    /**
     * A filled triangle arrow pointing along `angleRad` (0 = right, positive clockwise in
     * GUI coordinates), drawn as stacked rows in a local frame rotated around (`cx`,
     * `cy`).
     */
    private fun drawArrow(context: AxoRenderContext, cx: Int, cy: Int, angleRad: Double, color: Int) {
        context.`br$pushMatrix`()
        context.`br$rotateMatrixAround`(angleRad.toFloat(), cx.toFloat(), cy.toFloat())
        for (dy in -SHAPE_HALF..SHAPE_HALF) {
            val length = (SHAPE_HALF - abs(dy)) * 2 + 3
            context.`br$fillRect`(cx - length / 2, cy + dy, length, 1, color)
        }
        context.`br$popMatrix`()
    }

    /** The distance text at 70% brightness of the name colour, or plain white for pure white names. */
    private fun dim(color: Int): Int {
        if (color == WHITE) {
            return 0xFFAAAAAA.toInt()
        }
        return ARGB.color(
            ARGB.alpha(color),
            (ARGB.red(color) * 0.7).toInt(),
            (ARGB.green(color) * 0.7).toInt(),
            (ARGB.blue(color) * 0.7).toInt()
        )
    }
}
