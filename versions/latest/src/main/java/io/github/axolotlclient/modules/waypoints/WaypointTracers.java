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

package io.github.axolotlclient.modules.waypoints;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud;
import io.github.axolotlclient.util.WorldProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;

/**
 * 3D waypoint tracers (Alpine "waypoints" parity, the row the parity sheet kept open because the
 * codebase had no projection math): every enabled waypoint of the current dimension is projected
 * through {@link WorldProjection} and drawn as an on-screen marker — a diamond in the waypoint's
 * colour plus a name/distance label at the projected position, clamped to the screen edges with an
 * arrow pointing the way when the waypoint is off-screen or behind the camera. The death point
 * keeps its distinct red colour ({@link Waypoint#DEATH_COLOR}) like every other waypoint
 * rendering.
 *
 * <p>{@link WaypointManager} is used strictly read-only; the tracer owns no waypoint state. The
 * toggle ({@code waypoints.tracers}) and the label options live on {@link WaypointsHud}, the
 * module's existing option surface, so the tracer is on exactly when the waypoints module is.</p>
 */
public final class WaypointTracers {

	/** Vertical lift of the marker over the stored (feet) position, so labels clear the ground. */
	private static final double MARKER_Y_OFFSET = 1.0;
	/** Inset of edge-clamped markers from the screen border, in GUI pixels. */
	private static final int EDGE_MARGIN = 16;
	/** Half-height of the diamond/arrow shapes (they are 2*SHAPE_HALF+1 px tall). */
	private static final int SHAPE_HALF = 3;
	private static final int WHITE = 0xFFFFFFFF;

	private WaypointTracers() {
	}

	/**
	 * Draws one marker per enabled waypoint of the player's dimension. Called from the HUD render
	 * path (after the crosshair) so the camera holds this frame's position, rotation and FOV.
	 */
	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (minecraft.level == null || player == null) {
			return;
		}
		WaypointsHud entry = (WaypointsHud) HudManager.getInstance().get(WaypointsHud.ID);
		if (entry == null || !entry.isEnabled() || !entry.tracers.get()) {
			return;
		}

		AxoRenderContext context = graphics;
		Font font = minecraft.font;
		WorldProjection projection = WorldProjection.capture(minecraft);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean showDistance = entry.showDistance.get();

		for (Waypoint waypoint : WaypointManager.getInstance().getForDimension(WaypointsHud.currentDimension(player))) {
			if (!waypoint.enabled()) {
				continue;
			}
			double dx = waypoint.x() - player.getX();
			double dy = waypoint.y() - player.getY();
			double dz = waypoint.z() - player.getZ();
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

			WorldProjection.ScreenPos pos =
				projection.project(waypoint.x(), waypoint.y() + MARKER_Y_OFFSET, waypoint.z());
			if (pos.onScreen(width, height)) {
				int cx = (int) Math.round(pos.x());
				int cy = (int) Math.round(pos.y());
				drawDiamond(context, cx, cy, waypoint.color());
				drawLabel(graphics, font, cx, cy + SHAPE_HALF + 2, waypoint, distance, showDistance, true);
			} else {
				WorldProjection.ScreenPos clamped = WorldProjection.clampToEdges(pos, width, height, EDGE_MARGIN);
				double angle = WorldProjection.edgeDirection(pos, width, height);
				int cx = (int) Math.round(clamped.x());
				int cy = (int) Math.round(clamped.y());
				drawArrow(context, cx, cy, angle, waypoint.color());
				// Offset the label towards the screen centre so it never leaves the screen with
				// the marker: left edge labels go right of the arrow, top labels below it, etc.
				int lx = cx + (int) Math.round(Math.cos(angle) * -(SHAPE_HALF + 4));
				int ly = cy + (int) Math.round(Math.sin(angle) * -(SHAPE_HALF + 4));
				drawLabel(graphics, font, lx, ly, waypoint, distance, showDistance, false);
			}
		}
	}

	/**
	 * Name in the waypoint colour, distance in white, drawn as one line. Centered under on-screen
	 * markers; edge markers lead with the line away from the border.
	 */
	private static void drawLabel(GuiGraphicsExtractor graphics, Font font, int cx, int cy,
								  Waypoint waypoint, double distance, boolean showDistance, boolean centered) {
		String name = waypoint.name();
		String distanceText = showDistance ? " " + WaypointsHud.formatDistance(distance) : "";
		int nameWidth = font.width(name);
		int distanceWidth = distanceText.isEmpty() ? 0 : font.width(distanceText);
		int x = centered ? cx - (nameWidth + distanceWidth) / 2 : cx;
		graphics.text(font, name, x, cy, waypoint.color(), true);
		if (!distanceText.isEmpty()) {
			graphics.text(font, distanceText, x + nameWidth, cy, dim(waypoint.color()), true);
		}
	}

	/**
	 * A filled diamond of stacked 1px rows, 7x7: row widths 1/3/5/7/5/3/1.
	 */
	private static void drawDiamond(AxoRenderContext context, int cx, int cy, int color) {
		for (int dy = -SHAPE_HALF; dy <= SHAPE_HALF; dy++) {
			int half = SHAPE_HALF - Math.abs(dy);
			context.br$fillRect(cx - half, cy + dy, half * 2 + 1, 1, color);
		}
	}

	/**
	 * A filled triangle arrow pointing along {@code angleRad} (0 = right, positive clockwise in
	 * GUI coordinates), drawn as stacked rows in a local frame rotated around ({@code cx},
	 * {@code cy}).
	 */
	private static void drawArrow(AxoRenderContext context, int cx, int cy, double angleRad, int color) {
		context.br$pushMatrix();
		context.br$rotateMatrixAround((float) angleRad, cx, cy);
		for (int dy = -SHAPE_HALF; dy <= SHAPE_HALF; dy++) {
			int length = (SHAPE_HALF - Math.abs(dy)) * 2 + 3;
			context.br$fillRect(cx - length / 2, cy + dy, length, 1, color);
		}
		context.br$popMatrix();
	}

	/** The distance text at 70% brightness of the name colour, or plain white for pure white names. */
	private static int dim(int color) {
		if (color == WHITE) {
			return 0xFFAAAAAA;
		}
		return ARGB.color(ARGB.alpha(color),
			(int) (ARGB.red(color) * 0.7),
			(int) (ARGB.green(color) * 0.7),
			(int) (ARGB.blue(color) * 0.7));
	}
}
