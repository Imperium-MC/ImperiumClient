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

package io.github.axolotlclient.modules.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.imperium.menu.CarvedTheme;
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud;
import io.github.axolotlclient.modules.waypoints.Waypoint;
import io.github.axolotlclient.modules.waypoints.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The fullscreen map (Alpine "minimap" large view): the whole screen is a north-locked
 * chunk-colour map, pannable by dragging, zoomable with the scroll wheel, with waypoint
 * dots + labels and the player marker.
 *
 * <p>The terrain is sampled by the module's shared {@link MinimapTexture} (a second instance
 * of the same sampler the HUD minimap uses, at a coarser zoom) — this screen never samples
 * blocks itself. The screen owns only view state: the world-space centre of the view, the
 * drag, and the overlay chrome (carved info panel, per {@code ImperiumMenuScreen}
 * conventions).</p>
 *
 * <p>Geometry: the sampler grid is a square of texels anchored at a zoom-quantised world
 * position; the screen maps world coordinates to pixels as
 * {@code px = width/2 + (wx - centerX) * pixelsPerBlock}, with
 * {@code pixelsPerBlock = screenScale / zoom} (screen scale upscales each texel so the fixed
 * grid always covers the window). Dragging and scrolling only move the view centre / zoom;
 * the texture recentres itself on the next tick.</p>
 */
public class FullscreenMapScreen extends Screen {

	/** Sampler grid side in texels; 256x256 fills within the per-tick sampling budget. */
	private static final int GRID_SIZE = 256;

	private final MinimapTexture texture;

	/** World-space centre of the view (starts on the player, moves while dragging). */
	private double centerX;
	private double centerZ;

	private String registeredTextureId = "";
	/** Waypoint dots drawn by the most recent frame (gametest evidence). */
	private int drawnWaypoints;
	/** A left-button drag is in progress (set by mouseClicked, cleared by mouseReleased). */
	private boolean dragging;

	public FullscreenMapScreen() {
		super(Component.translatable("imperiumclient.fullscreenmap.title"));
		this.texture = FullscreenMap.getInstance().getMapTexture();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			this.centerX = player.getX();
			this.centerZ = player.getZ();
		}
	}

	@Override
	protected void init() {
		texture.rebuild(GRID_SIZE);
	}

	@Override
	public void tick() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		texture.setZoom(FullscreenMap.getInstance().zoom.get());
		if (texture.getGridSize() != GRID_SIZE) {
			texture.rebuild(GRID_SIZE);
		}
		// The view centre plays the player's role for the sampler: the grid recentres on it.
		texture.tick((ClientLevel) client.level, centerX, centerZ);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		// Dark backdrop behind the (partly unloaded) map area, like every large-map client.
		graphics.fill(0, 0, width, height, 0xF0100E0C);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || texture.getTexture() == null) {
			return;
		}
		ensureRegistered(client);

		int grid = texture.getGridSize();
		long anchorX = texture.getAnchorX();
		long anchorZ = texture.getAnchorZ();
		double zoom = texture.getZoom();
		double scale = Math.max(1.0D, Math.ceil(Math.max(width, height) / (double) grid));
		int drawSize = (int) Math.ceil(grid * scale);
		// Screen position of the grid's top-left texel: the grid centre sits at the anchor,
		// and fractional anchor-vs-view-centre offset is absorbed here so panning is smooth.
		int blitX = (int) Math.floor(width / 2.0D - (grid / 2.0D + (centerX - anchorX) / zoom) * scale);
		int blitY = (int) Math.floor(height / 2.0D - (grid / 2.0D + (centerZ - anchorZ) / zoom) * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture.textureId(), blitX, blitY,
			0.0F, 0.0F, drawSize, drawSize, grid, grid, grid, grid, -1);

		drawWaypoints(graphics, player, anchorX, anchorZ, zoom, scale, blitX, blitY);
		drawPlayerMarker(graphics, player, anchorX, anchorZ, zoom, scale, blitX, blitY);
		drawInfoPanel(graphics, player);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Accepting the click starts a map drag; drags are only delivered for buttons the
			// screen accepted (same contract the HUD editor relies on).
			dragging = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Dragging the map content right means looking further west; scale is px/texel.
			double scale = Math.max(1.0D, Math.ceil(Math.max(width, height) / (double) texture.getGridSize()));
			double pixelsPerBlock = scale / texture.getZoom();
			centerX -= deltaX * pixelsPerBlock;
			centerZ -= deltaY * pixelsPerBlock;
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			var zoomOption = FullscreenMap.getInstance().zoom;
			zoomOption.set(Mth.clamp(zoomOption.get() + (scrollY > 0 ? -1 : 1),
				zoomOption.getMin(), zoomOption.getMax()));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_M) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void ensureRegistered(Minecraft client) {
		String id = texture.textureId().toString();
		if (!id.equals(registeredTextureId)) {
			client.getTextureManager().register(texture.textureId(), texture.getTexture());
			registeredTextureId = id;
		}
		if (texture.isUploadPending()) {
			texture.getTexture().upload();
			texture.clearUploadPending();
		}
	}

	private void drawWaypoints(GuiGraphicsExtractor graphics, LocalPlayer player, long anchorX, long anchorZ,
							   double zoom, double scale, int blitX, int blitY) {
		drawnWaypoints = 0;
		if (!FullscreenMap.getInstance().showWaypoints.get()) {
			return;
		}
		boolean labels = FullscreenMap.getInstance().waypointLabels.get();
		String dimension = WaypointsHud.currentDimension(player);
		for (Waypoint wp : WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled()) {
				continue;
			}
			int x = worldToScreenX(wp.x(), anchorX, zoom, scale, blitX);
			int y = worldToScreenZ(wp.z(), anchorZ, zoom, scale, blitY);
			if (x < -8 || y < -8 || x > width + 8 || y > height + 8) {
				continue;
			}
			// dot with a 1px drop shadow, like the HUD minimap's dots
			graphics.fill(x, y, x + 4, y + 4, 0x99000000);
			graphics.fill(x - 1, y - 1, x + 3, y + 3, wp.color());
			if (labels) {
				int dist = (int) Math.hypot(wp.x() - player.getX(), wp.z() - player.getZ());
				String label = wp.name() + " (" + dist + "m)";
				graphics.text(font, label, x + 6, y - 3, wp.color(), true);
			}
			drawnWaypoints++;
		}
	}

	private void drawPlayerMarker(GuiGraphicsExtractor graphics, LocalPlayer player, long anchorX, long anchorZ,
								  double zoom, double scale, int blitX, int blitY) {
		int x = worldToScreenX(player.getX(), anchorX, zoom, scale, blitX);
		int y = worldToScreenZ(player.getZ(), anchorZ, zoom, scale, blitY);
		// MC yaw: 0 = south (+Z = screen down); the arrow shape points north (up) at rest.
		float angle = (float) (Math.PI - Math.toRadians(player.getYRot()));
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + 1.5F, y + 1.5F);
		pose.rotate(angle);
		graphics.fill(-1, -6, 0, -5, 0xFF1A1A1A);
		graphics.fill(-2, -5, 1, -4, 0xFF1A1A1A);
		graphics.fill(-3, -4, 2, -3, 0xFF1A1A1A);
		graphics.fill(-1, -3, 0, 3, 0xFF1A1A1A);
		graphics.fill(0, -7, 1, -6, 0xFFFFFFFF);
		graphics.fill(-1, -6, 2, -5, 0xFFFFFFFF);
		graphics.fill(-2, -5, 3, -4, 0xFFFFFFFF);
		graphics.fill(0, -4, 1, 4, 0xFFFFFFFF);
		pose.popMatrix();
	}

	private void drawInfoPanel(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int panelW = 170;
		int panelH = 52;
		CarvedTheme.carvedPanel(graphics, 8, 8, panelW, panelH);
		CarvedTheme.engravedCaps(graphics, font, title.getString(), 18, 16, CarvedTheme.BRONZE_LIGHT);
		String coords = (int) Math.floor(centerX) + " / " + (int) Math.floor(player.getY()) + " / "
			+ (int) Math.floor(centerZ);
		graphics.text(font, coords, 18, 28, CarvedTheme.MARBLE, false);
		graphics.text(font, Component.translatable("imperiumclient.fullscreenmap.hint",
			FullscreenMap.getInstance().zoom.get()), 18, 40, CarvedTheme.MUTED, false);
	}

	/** Screen x of a world x, in the current frame's grid/scale parameters. */
	@VisibleForTesting
	int worldToScreenX(double worldX, long anchorX, double zoom, double scale, int blitX) {
		return (int) Math.round(blitX + ((worldX - anchorX) / zoom + texture.getGridSize() / 2.0D) * scale);
	}

	/** Screen y of a world z, in the current frame's grid/scale parameters. */
	@VisibleForTesting
	int worldToScreenZ(double worldZ, long anchorZ, double zoom, double scale, int blitY) {
		return (int) Math.round(blitY + ((worldZ - anchorZ) / zoom + texture.getGridSize() / 2.0D) * scale);
	}

	@VisibleForTesting
	public double getViewCenterX() {
		return centerX;
	}

	@VisibleForTesting
	public double getViewCenterZ() {
		return centerZ;
	}

	@VisibleForTesting
	public MinimapTexture getMapTexture() {
		return texture;
	}

	@VisibleForTesting
	public int getDrawnWaypoints() {
		return drawnWaypoints;
	}
}
