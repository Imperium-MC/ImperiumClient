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

package io.github.axolotlclient.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * World-space rendering foundation: world-to-screen projection for the active camera
 * (the piece the parity sheet kept noting as "no projection math in codebase").
 *
 * <p>The math mirrors exactly what vanilla does per frame: {@code Camera.getViewRotationProjectionMatrix}
 * builds {@code JOML Matrix4f.setPerspective(fovY * PI/180, width / height, ...)} on top of the camera
 * rotation, and {@code GameRenderer.projectPointToScreen} perspective-divides {@code point - eye} through
 * it. This class re-derives that pipeline from first principles (eye position, yaw/pitch, vertical FOV,
 * aspect) so it is a pure function — unit-testable without a live render state — while still agreeing
 * with the vanilla matrices (the game test pins both against each other).</p>
 *
 * <p>Camera conventions (verified against {@code Entity#getViewVector} and {@code Camera#setRotation}):
 * yaw 0 faces +Z, positive yaw turns right (towards -X), positive pitch looks down; the camera has no
 * roll. With {@code cy = cos(yaw), sy = sin(yaw), cp = cos(pitch), sp = sin(pitch)} the view basis is:</p>
 * <ul>
 *     <li>forward {@code F = (-sy*cp, -sp, cy*cp)} — the direction the camera looks</li>
 *     <li>screen-right {@code R = (-cy, 0, -sy)} — independent of pitch (roll stays 0)</li>
 *     <li>screen-up {@code U = R x F = (-sy*sp, cp, cy*sp)}</li>
 * </ul>
 *
 * <p>A point at {@code eye + d} has view coordinates {@code xv = R.d}, {@code yv = U.d},
 * {@code zv = F.d} (positive in front of the eye). JOML's perspective matrix then yields
 * {@code ndcX = (xv / aspect) / (zv * tan(fovY/2))} and {@code ndcY = yv / (zv * tan(fovY/2))},
 * mapped to GUI pixels as {@code sx = (ndcX + 1) / 2 * width}, {@code sy = (1 - ndcY) / 2 * height}
 * (GUI y grows downwards).</p>
 */
public final class WorldProjection {

	/**
	 * A projected position in GUI pixel coordinates. {@code x}/{@code y} may lie far outside the
	 * screen. For points behind the camera the coordinates are synthetic — placed far off-screen
	 * along the direction the player must turn to bring the point into view — so that edge
	 * clamping and direction arrows work uniformly (see {@link #project}).
	 *
	 * @param x            GUI pixel x (0 = left edge of the screen)
	 * @param y            GUI pixel y (0 = top edge of the screen)
	 * @param behindCamera whether the point is at or behind the camera plane ({@code zv <= 0})
	 */
	public record ScreenPos(double x, double y, boolean behindCamera) {

		public boolean onScreen(int width, int height) {
			return !behindCamera && x >= 0 && x <= width && y >= 0 && y <= height;
		}
	}

	private final double eyeX, eyeY, eyeZ;
	private final double rightX, rightY, rightZ;
	private final double upX, upY, upZ;
	private final double forwardX, forwardY, forwardZ;
	private final double tanHalfFovY;
	private final double aspect;
	private final int screenWidth, screenHeight;

	private WorldProjection(double eyeX, double eyeY, double eyeZ,
							double rightX, double rightY, double rightZ,
							double upX, double upY, double upZ,
							double forwardX, double forwardY, double forwardZ,
							double fovYDegrees, double aspect, int screenWidth, int screenHeight) {
		this.eyeX = eyeX;
		this.eyeY = eyeY;
		this.eyeZ = eyeZ;
		this.rightX = rightX;
		this.rightY = rightY;
		this.rightZ = rightZ;
		this.upX = upX;
		this.upY = upY;
		this.upZ = upZ;
		this.forwardX = forwardX;
		this.forwardY = forwardY;
		this.forwardZ = forwardZ;
		this.tanHalfFovY = Math.tan(Math.toRadians(fovYDegrees) / 2.0);
		this.aspect = aspect;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	/**
	 * Builds a projection from explicit camera parameters. Pure: identical inputs always yield
	 * identical projections, no Minecraft state is touched.
	 *
	 * @param eyeX          camera eye x
	 * @param eyeY          camera eye y
	 * @param eyeZ          camera eye z
	 * @param yawDegrees    Minecraft camera yaw (0 = facing +Z, positive turns right)
	 * @param pitchDegrees  camera pitch (positive looks down, clamped internally to +-90)
	 * @param fovYDegrees   vertical field of view in degrees, as {@code Camera#getFov()} reports it
	 * @param aspect        framebuffer width / height (the perspective matrix's aspect)
	 * @param screenWidth   GUI pixel width the result is expressed in
	 * @param screenHeight  GUI pixel height the result is expressed in
	 */
	public static WorldProjection of(double eyeX, double eyeY, double eyeZ,
									 float yawDegrees, float pitchDegrees,
									 float fovYDegrees, double aspect,
									 int screenWidth, int screenHeight) {
		double yaw = Math.toRadians(yawDegrees);
		double pitch = Math.toRadians(Mth.clamp(pitchDegrees, -90.0F, 90.0F));
		double sy = Math.sin(yaw), cy = Math.cos(yaw);
		double sp = Math.sin(pitch), cp = Math.cos(pitch);

		// See the class javadoc for the derivation; the basis is orthonormal by construction.
		return new WorldProjection(eyeX, eyeY, eyeZ,
			-cy, 0.0, -sy,
			-sy * sp, cp, cy * sp,
			-sy * cp, -sp, cy * cp,
			fovYDegrees, aspect, screenWidth, screenHeight);
	}

	/**
	 * Captures the projection of the frame's live camera: interpolated eye position and rotation
	 * from {@link Camera}, the camera's own vertical FOV, and the window's aspect ratio (the GUI
	 * scale rounds {@code guiWidth/guiHeight}, the framebuffer does not — the projection matrix is
	 * built from the framebuffer).
	 */
	public static WorldProjection capture(Minecraft minecraft) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		WindowSize size = windowSize(minecraft);
		return of(eye.x, eye.y, eye.z, camera.yRot(), camera.xRot(), camera.getFov(),
			size.aspect(), size.width(), size.height());
	}

	/**
	 * The GUI pixel size plus the exact framebuffer aspect (both derive from the window, but the
	 * GUI-scaled size is what the projected pixels are drawn against).
	 */
	public record WindowSize(int width, int height, double aspect) {
	}

	public static WindowSize windowSize(Minecraft minecraft) {
		int width = minecraft.getWindow().getWidth();
		int height = minecraft.getWindow().getHeight();
		double aspect = height == 0 ? 1.0 : (double) width / height;
		return new WindowSize(minecraft.getWindow().getGuiScaledWidth(),
			minecraft.getWindow().getGuiScaledHeight(), aspect);
	}

	/**
	 * Projects a world point to GUI pixel coordinates, or reports it as behind the camera. For
	 * behind-camera points the perspective divide would flip both axes and explode towards
	 * mirrored infinity, so the position is synthesized instead: far off-screen along the
	 * direction the player must turn to see the point (the raw {@code xv}/{@code yv} view
	 * offsets, which stay meaningful behind the plane). Callers wanting an on-screen answer use
	 * {@link #clampToEdges}; {@link #onScreen} rejects behind points outright.
	 */
	public ScreenPos project(double x, double y, double z) {
		double dx = x - eyeX;
		double dy = y - eyeY;
		double dz = z - eyeZ;
		double zv = forwardX * dx + forwardY * dy + forwardZ * dz;
		double xv = rightX * dx + rightY * dy + rightZ * dz;
		double yv = upX * dx + upY * dy + upZ * dz;
		if (zv <= 0.0) {
			double cx = screenWidth / 2.0;
			double cy = screenHeight / 2.0;
			double length = Math.hypot(xv, yv);
			if (length < 1.0E-9) {
				return new ScreenPos(cx, cy, true);
			}
			double far = 2.0 * Math.hypot(screenWidth, screenHeight) / length;
			return new ScreenPos(cx + xv * far, cy - yv * far, true);
		}
		double denom = zv * tanHalfFovY;
		double ndcX = (xv / aspect) / denom;
		double ndcY = yv / denom;
		return new ScreenPos((ndcX + 1.0) / 2.0 * screenWidth,
			(1.0 - ndcY) / 2.0 * screenHeight,
			false);
	}

	public int screenWidth() {
		return screenWidth;
	}

	public int screenHeight() {
		return screenHeight;
	}

	/**
	 * Screen-space direction (radians, GUI convention: 0 = right, positive clockwise because y
	 * grows downwards) in which an off-screen or behind-camera point left the screen — for an
	 * edge-clamped tracer marker this is the direction its arrow must point. Works uniformly for
	 * both because behind-camera positions are already synthesized along the turn direction.
	 */
	public static double edgeDirection(ScreenPos pos, int width, int height) {
		double dx = pos.x() - width / 2.0;
		double dy = pos.y() - height / 2.0;
		return Math.atan2(dy, dx);
	}

	/**
	 * Clamps a projected position onto the screen border with {@code margin} pixels of inset:
	 * the ray from the screen center through the point is scaled until it hits the border
	 * rectangle. Pure function, used by the edge-clamped tracer markers. Visible points pass
	 * through untouched.
	 */
	public static ScreenPos clampToEdges(ScreenPos pos, int width, int height, int margin) {
		if (pos.onScreen(width, height)) {
			return pos;
		}
		double cx = width / 2.0;
		double cy = height / 2.0;
		double dx = pos.x() - cx;
		double dy = pos.y() - cy;
		double minX = margin;
		double minY = margin;
		double maxX = width - margin;
		double maxY = height - margin;
		// Degenerate center hit (dead-center behind-camera point): fall back to pointing up.
		if (dx == 0.0 && dy == 0.0) {
			return new ScreenPos(cx, minY, pos.behindCamera());
		}
		double limitX = dx == 0.0 ? Double.POSITIVE_INFINITY : Math.min((maxX - cx) / Math.abs(dx), (cx - minX) / Math.abs(dx));
		double limitY = dy == 0.0 ? Double.POSITIVE_INFINITY : Math.min((maxY - cy) / Math.abs(dy), (cy - minY) / Math.abs(dy));
		double scale = Math.min(limitX, limitY);
		return new ScreenPos(cx + dx * scale, cy + dy * scale, pos.behindCamera());
	}
}
