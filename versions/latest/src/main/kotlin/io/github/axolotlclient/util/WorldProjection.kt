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

package io.github.axolotlclient.util

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * World-space rendering foundation: world-to-screen projection for the active camera
 * (the piece the parity sheet kept noting as "no projection math in codebase").
 *
 * The math mirrors exactly what vanilla does per frame: `Camera.getViewRotationProjectionMatrix`
 * builds `JOML Matrix4f.setPerspective(fovY * PI/180, width / height, ...)` on top of the camera
 * rotation, and `GameRenderer.projectPointToScreen` perspective-divides `point - eye` through
 * it. This class re-derives that pipeline from first principles (eye position, yaw/pitch, vertical FOV,
 * aspect) so it is a pure function — unit-testable without a live render state — while still agreeing
 * with the vanilla matrices (the game test pins both against each other).
 *
 * Camera conventions (verified against `Entity#getViewVector` and `Camera#setRotation`):
 * yaw 0 faces +Z, positive yaw turns right (towards -X), positive pitch looks down; the camera has no
 * roll. With `cy = cos(yaw), sy = sin(yaw), cp = cos(pitch), sp = sin(pitch)` the view basis is:
 *  - forward `F = (-sy*cp, -sp, cy*cp)` — the direction the camera looks
 *  - screen-right `R = (-cy, 0, -sy)` — independent of pitch (roll stays 0)
 *  - screen-up `U = R x F = (-sy*sp, cp, cy*sp)`
 *
 * A point at `eye + d` has view coordinates `xv = R.d`, `yv = U.d`,
 * `zv = F.d` (positive in front of the eye). JOML's perspective matrix then yields
 * `ndcX = (xv / aspect) / (zv * tan(fovY/2))` and `ndcY = yv / (zv * tan(fovY/2))`,
 * mapped to GUI pixels as `sx = (ndcX + 1) / 2 * width`, `sy = (1 - ndcY) / 2 * height`
 * (GUI y grows downwards).
 */
class WorldProjection private constructor(
    private val eyeX: Double,
    private val eyeY: Double,
    private val eyeZ: Double,
    rightX: Double,
    rightY: Double,
    rightZ: Double,
    upX: Double,
    upY: Double,
    upZ: Double,
    forwardX: Double,
    forwardY: Double,
    forwardZ: Double,
    fovYDegrees: Double,
    private val aspect: Double,
    private val screenWidth: Int,
    private val screenHeight: Int
) {

    /**
     * A projected position in GUI pixel coordinates. `x`/`y` may lie far outside the
     * screen. For points behind the camera the coordinates are synthetic — placed far off-screen
     * along the direction the player must turn to bring the point into view — so that edge
     * clamping and direction arrows work uniformly (see [project]).
     *
     * @param x            GUI pixel x (0 = left edge of the screen)
     * @param y            GUI pixel y (0 = top edge of the screen)
     * @param behindCamera whether the point is at or behind the camera plane (`zv <= 0`)
     */
    @JvmRecord
    data class ScreenPos(val x: Double, val y: Double, val behindCamera: Boolean) {
        fun onScreen(width: Int, height: Int): Boolean {
            return !behindCamera && x >= 0 && x <= width && y >= 0 && y <= height
        }
    }

    private val rightX: Double
    private val rightY: Double
    private val rightZ: Double
    private val upX: Double
    private val upY: Double
    private val upZ: Double
    private val forwardX: Double
    private val forwardY: Double
    private val forwardZ: Double
    private val tanHalfFovY: Double

    init {
        this.rightX = rightX
        this.rightY = rightY
        this.rightZ = rightZ
        this.upX = upX
        this.upY = upY
        this.upZ = upZ
        this.forwardX = forwardX
        this.forwardY = forwardY
        this.forwardZ = forwardZ
        this.tanHalfFovY = tan(Math.toRadians(fovYDegrees) / 2.0)
    }

    /**
     * Projects a world point to GUI pixel coordinates, or reports it as behind the camera. For
     * behind-camera points the perspective divide would flip both axes and explode towards
     * mirrored infinity, so the position is synthesized instead: far off-screen along the
     * direction the player must turn to see the point (the raw `xv`/`yv` view
     * offsets, which stay meaningful behind the plane). Callers wanting an on-screen answer use
     * [clampToEdges]; [ScreenPos.onScreen] rejects behind points outright.
     */
    fun project(x: Double, y: Double, z: Double): ScreenPos {
        val dx = x - eyeX
        val dy = y - eyeY
        val dz = z - eyeZ
        val zv = forwardX * dx + forwardY * dy + forwardZ * dz
        val xv = rightX * dx + rightY * dy + rightZ * dz
        val yv = upX * dx + upY * dy + upZ * dz
        if (zv <= 0.0) {
            val cx = screenWidth / 2.0
            val cy = screenHeight / 2.0
            val length = hypot(xv, yv)
            if (length < 1.0E-9) {
                return ScreenPos(cx, cy, true)
            }
            val far = 2.0 * hypot(screenWidth.toDouble(), screenHeight.toDouble()) / length
            return ScreenPos(cx + xv * far, cy - yv * far, true)
        }
        val denom = zv * tanHalfFovY
        val ndcX = (xv / aspect) / denom
        val ndcY = yv / denom
        return ScreenPos(
            (ndcX + 1.0) / 2.0 * screenWidth,
            (1.0 - ndcY) / 2.0 * screenHeight,
            false
        )
    }

    fun screenWidth(): Int {
        return screenWidth
    }

    fun screenHeight(): Int {
        return screenHeight
    }

    companion object {
        /**
         * Builds a projection from explicit camera parameters. Pure: identical inputs always yield
         * identical projections, no Minecraft state is touched.
         */
        @JvmStatic
        fun of(
            eyeX: Double, eyeY: Double, eyeZ: Double,
            yawDegrees: Float, pitchDegrees: Float,
            fovYDegrees: Float, aspect: Double,
            screenWidth: Int, screenHeight: Int
        ): WorldProjection {
            val yaw = Math.toRadians(yawDegrees.toDouble())
            val pitch = Math.toRadians(Mth.clamp(pitchDegrees, -90.0f, 90.0f).toDouble())
            val sy = sin(yaw)
            val cy = cos(yaw)
            val sp = sin(pitch)
            val cp = cos(pitch)

            // See the class javadoc for the derivation; the basis is orthonormal by construction.
            return WorldProjection(
                eyeX, eyeY, eyeZ,
                -cy, 0.0, -sy,
                -sy * sp, cp, cy * sp,
                -sy * cp, -sp, cy * cp,
                fovYDegrees.toDouble(), aspect, screenWidth, screenHeight
            )
        }

        /**
         * Captures the projection of the frame's live camera: interpolated eye position and rotation
         * from `Camera`, the camera's own vertical FOV, and the window's aspect ratio (the GUI
         * scale rounds `guiWidth/guiHeight`, the framebuffer does not — the projection matrix is
         * built from the framebuffer).
         */
        @JvmStatic
        fun capture(minecraft: Minecraft): WorldProjection {
            val camera = minecraft.gameRenderer.mainCamera()
            val eye = camera.position()
            val size = windowSize(minecraft)
            return of(
                eye.x, eye.y, eye.z, camera.yRot(), camera.xRot(), camera.fov,
                size.aspect, size.width, size.height
            )
        }

        /**
         * The GUI pixel size plus the exact framebuffer aspect (both derive from the window, but the
         * GUI-scaled size is what the projected pixels are drawn against).
         */
        @JvmRecord
        data class WindowSize(val width: Int, val height: Int, val aspect: Double)

        @JvmStatic
        fun windowSize(minecraft: Minecraft): WindowSize {
            val width = minecraft.window.width
            val height = minecraft.window.height
            val aspect = if (height == 0) 1.0 else width.toDouble() / height
            return WindowSize(
                minecraft.window.guiScaledWidth,
                minecraft.window.guiScaledHeight, aspect
            )
        }

        /**
         * Screen-space direction (radians, GUI convention: 0 = right, positive clockwise because y
         * grows downwards) in which an off-screen or behind-camera point left the screen — for an
         * edge-clamped tracer marker this is the direction its arrow must point. Works uniformly for
         * both because behind-camera positions are already synthesized along the turn direction.
         */
        @JvmStatic
        fun edgeDirection(pos: ScreenPos, width: Int, height: Int): Double {
            val dx = pos.x - width / 2.0
            val dy = pos.y - height / 2.0
            return atan2(dy, dx)
        }

        /**
         * Clamps a projected position onto the screen border with `margin` pixels of inset:
         * the ray from the screen center through the point is scaled until it hits the border
         * rectangle. Pure function, used by the edge-clamped tracer markers. Visible points pass
         * through untouched.
         */
        @JvmStatic
        fun clampToEdges(pos: ScreenPos, width: Int, height: Int, margin: Int): ScreenPos {
            if (pos.onScreen(width, height)) {
                return pos
            }
            val cx = width / 2.0
            val cy = height / 2.0
            val dx = pos.x - cx
            val dy = pos.y - cy
            val minX = margin.toDouble()
            val minY = margin.toDouble()
            val maxX = width - margin.toDouble()
            val maxY = height - margin.toDouble()
            // Degenerate center hit (dead-center behind-camera point): fall back to pointing up.
            if (dx == 0.0 && dy == 0.0) {
                return ScreenPos(cx, minY, pos.behindCamera)
            }
            val limitX = if (dx == 0.0) Double.POSITIVE_INFINITY else minOf((maxX - cx) / kotlin.math.abs(dx), (cx - minX) / kotlin.math.abs(dx))
            val limitY = if (dy == 0.0) Double.POSITIVE_INFINITY else minOf((maxY - cy) / kotlin.math.abs(dy), (cy - minY) / kotlin.math.abs(dy))
            val scale = minOf(limitX, limitY)
            return ScreenPos(cx + dx * scale, cy + dy * scale, pos.behindCamera)
        }
    }
}
