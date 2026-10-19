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

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable;
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry;
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.modules.hud.util.DefaultOptions;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.modules.waypoints.Waypoint;
import io.github.axolotlclient.modules.waypoints.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Square/circular chunk-colour minimap HUD (Alpine "minimap" module, parity spec M3).
 *
 * <p>Rendering: {@link MinimapTexture} keeps a NativeImage-backed dynamic texture of
 * top-block {@link net.minecraft.world.level.material.MapColor}s (vanilla map palette,
 * slope- and water-shaded), drawn as a single scaled blit that rotates with the camera in
 * follow-camera mode. A second small dynamic texture acts as the anti-aliased circular
 * mask ring when the circle shape is selected. Waypoints from the shared WaypointManager
 * (read-only) are projected onto the map as dots, with a label for the nearest one.</p>
 *
 * <p>Out of scope for this pass (follow-up): a fullscreen map view keybind.</p>
 */
public class MinimapHud extends BoxHudEntry implements DynamicallyPositionable {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "minimaphud");

	private static final int DEFAULT_SIZE = 128;
	/** Extra content sampled beyond the window so a rotating square map has no hollow corners. */
	private static final float ROTATION_OVERSIZE = 1.5F;

	public final EnumOption<Shape> shape = new EnumOption<>("minimap.shape", Shape.class, Shape.SQUARE);
	public final EnumOption<Rotation> rotation = new EnumOption<>("minimap.rotation", Rotation.class, Rotation.NORTH_LOCK);
	public final IntegerOption zoom = new IntegerOption("minimap.zoom", 2, 1, 8);
	public final IntegerOption mapSize = new IntegerOption("minimap.size", DEFAULT_SIZE, 64, 256);
	public final IntegerOption opacity = new IntegerOption("minimap.opacity", 100, 10, 100);
	public final BooleanOption showWaypoints = new BooleanOption("minimap.show_waypoints", true);
	public final BooleanOption waypointLabels = new BooleanOption("minimap.waypoint_labels", true);
	public final BooleanOption showNorth = new BooleanOption("minimap.show_north", true);

	private final EnumOption<AnchorPoint> anchor = DefaultOptions.getAnchorPoint(AnchorPoint.TOP_RIGHT, this);
	private final Minecraft client = (Minecraft) super.client;
	private final MinimapTexture texture = new MinimapTexture();

	private int maskVersion;
	private DynamicTexture maskTexture;
	private int bakedMaskHash = -1;
	private boolean registered;

	public MinimapHud() {
		super(DEFAULT_SIZE, DEFAULT_SIZE, true);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public void init() {
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (isEnabled()) {
				texture.markChunkDirty(chunk.getPos());
			}
		});
	}

	@Override
	public boolean tickable() {
		return true;
	}

	@Override
	public void tick() {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		syncBounds();
		texture.setZoom(zoom.get());
		int grid = requiredGrid();
		if (grid != texture.getGridSize()) {
			texture.rebuild(grid);
			registered = false;
		}
		texture.tick((ClientLevel) player.level(), player.getX(), player.getZ());
	}

	@Override
	public void render(AxoRenderContext ctx, float delta) {
		if (shape.get() == Shape.CIRCLE) {
			// Circle mode: circular backing instead of the square background plate;
			// the rim comes from the baked mask ring, so the square outline is skipped.
			ctx.br$pushMatrix();
			scale(ctx);
			if (background.get() && backgroundColor.get().getAlpha() > 0) {
				ctx.br$fillRectRound(getBounds(), backgroundColor.get(), Math.min(getWidth(), getHeight()) / 2f);
			}
			renderComponent(ctx, delta);
			ctx.br$popMatrix();
		} else {
			super.render(ctx, delta);
		}
	}

	@Override
	public void renderComponent(AxoRenderContext ctx, float delta) {
		syncBounds();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || texture.getTexture() == null) {
			renderEmpty(ctx);
			return;
		}
		ensureRegistered();
		if (texture.isUploadPending()) {
			texture.getTexture().upload();
			texture.clearUploadPending();
		}
		final GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) ctx;
		final int size = getContentWidth();
		final int grid = texture.getGridSize();
		final float pxPerTexel = size / (float) grid;
		final double px = player.getX();
		final double pz = player.getZ();
		final float yawDeg = Mth.rotLerp(delta, player.yRotO, player.getYRot());
		final boolean follow = rotation.get() == Rotation.FOLLOW_CAMERA;
		// MC yaw: 0 = south (+Z). Rotating content by PI - yaw points the view direction up.
		final float mapRot = follow ? (float) (Math.PI - Math.toRadians(yawDeg)) : 0.0F;
		final float fracX = (float) (texture.playerOffsetX(px) * pxPerTexel);
		final float fracZ = (float) (texture.playerOffsetZ(pz) * pxPerTexel);

		DrawPosition pos = getContentPos();
		final int cx = pos.x() + size / 2;
		final int cy = pos.y() + size / 2;
		final int tint = ARGB.color((int) (opacity.get() * 2.55F), 0xFFFFFF);

		boolean scissor = shape.get() == Shape.SQUARE && follow;
		if (scissor) {
			ctx.br$pushScissor(getTrueContentX(), getTrueContentY(), getTrueContentWidth(), getTrueContentHeight());
		}
		ctx.br$pushMatrix();
		ctx.br$translateMatrix(cx, cy);
		ctx.br$rotateMatrix(mapRot);
		ctx.br$translateMatrix(-fracX, -fracZ);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture.textureId(), -size / 2, -size / 2,
			0.0F, 0.0F, size, size, grid, grid, grid, grid, tint);
		if (showWaypoints.get()) {
			drawWaypointDots(ctx, pxPerTexel, fracX / pxPerTexel, fracZ / pxPerTexel);
		}
		ctx.br$popMatrix();
		if (scissor) {
			ctx.br$popScissor();
		}

		if (showWaypoints.get() && waypointLabels.get()) {
			drawNearestWaypointLabel(ctx, cx, cy, size, pxPerTexel, fracX / pxPerTexel, fracZ / pxPerTexel, mapRot);
		}
		drawPlayerArrow(ctx, cx, cy, follow, yawDeg);
		if (showNorth.get()) {
			drawNorthIndicator(ctx, cx, cy, size, mapRot);
		}
		if (shape.get() == Shape.CIRCLE) {
			drawCircleMask(graphics, cx, cy);
		}
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext ctx, float delta) {
		renderComponent(ctx, delta);
	}

	private void renderEmpty(AxoRenderContext ctx) {
		// No world yet (menu-time HUD editor): a filled placeholder square/circle.
		int size = getContentWidth();
		if (size <= 0) {
			return;
		}
		DrawPosition pos = getContentPos();
		if (shape.get() == Shape.CIRCLE) {
			ctx.br$fillRectRound(pos.x(), pos.y(), size, size, backgroundColor.get().toInt(), size / 2f);
		} else {
			ctx.br$fillRect(pos.x(), pos.y(), size, size, backgroundColor.get().toInt());
		}
	}

	private void drawWaypointDots(AxoRenderContext ctx, float pxPerTexel, double fracTexelX, double fracTexelZ) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		String dimension = WaypointsHud.currentDimension(player);
		long anchorX = texture.getAnchorX();
		long anchorZ = texture.getAnchorZ();
		int zoom = texture.getZoom();
		int half = texture.getGridSize() / 2 - 2;
		int limit = shape.get() == Shape.CIRCLE
			? Math.max(1, (int) ((getContentWidth() / 2f) / pxPerTexel) - 2)
			: half;
		for (Waypoint wp : WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled()) {
				continue;
			}
			double tx = (wp.x() - anchorX) / (double) zoom - fracTexelX;
			double tz = (wp.z() - anchorZ) / (double) zoom - fracTexelZ;
			if (Math.abs(tx) > limit || Math.abs(tz) > limit) {
				continue;
			}
			if (shape.get() == Shape.CIRCLE && tx * tx + tz * tz > (double) limit * limit) {
				continue;
			}
			int x = (int) Math.round(tx * pxPerTexel);
			int y = (int) Math.round(tz * pxPerTexel);
			ctx.br$fillRect(x, y, 3, 3, 0xFF000000);          // 1px drop shadow
			ctx.br$fillRect(x - 1, y - 1, 3, 3, wp.color());
		}
	}

	private void drawNearestWaypointLabel(AxoRenderContext ctx, int cx, int cy, int size, float pxPerTexel,
										   double fracTexelX, double fracTexelZ, float mapRot) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		String dimension = WaypointsHud.currentDimension(player);
		Waypoint nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (Waypoint wp : WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled()) {
				continue;
			}
			double dx = wp.x() - player.getX();
			double dz = wp.z() - player.getZ();
			double distSq = dx * dx + dz * dz;
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = wp;
			}
		}
		if (nearest == null) {
			return;
		}
		int zoom = texture.getZoom();
		double tx = (nearest.x() - texture.getAnchorX()) / (double) zoom - fracTexelX;
		double tz = (nearest.z() - texture.getAnchorZ()) / (double) zoom - fracTexelZ;
		int half = texture.getGridSize() / 2 - 2;
		if (Math.abs(tx) > half || Math.abs(tz) > half) {
			return;
		}
		float sx = (float) (tx * pxPerTexel);
		float sy = (float) (tz * pxPerTexel);
		// the label is drawn outside the rotated pose and scissor: apply the map rotation
		// manually, and keep it inside the visible window
		float cos = Mth.cos(mapRot);
		float sin = Mth.sin(mapRot);
		int dx = (int) (sx * cos - sy * sin);
		int dy = (int) (sx * sin + sy * cos);
		int limit = size / 2 - client.font.lineHeight;
		if (Math.abs(dx) > limit || Math.abs(dy) > limit) {
			return;
		}
		String label = nearest.name();
		ctx.br$drawString(label, cx + dx - client.font.width(label) / 2, cy + dy - client.font.lineHeight - 2,
			nearest.color(), true);
	}

	private void drawPlayerArrow(AxoRenderContext ctx, int cx, int cy, boolean follow, float yawDeg) {
		// The arrow points where the player faces; on a rotating map that is always "up".
		float angle = follow ? 0.0F : (float) Math.toRadians(yawDeg) + (float) Math.PI;
		ctx.br$pushMatrix();
		ctx.br$rotateMatrixAround(angle, cx + 0.5F, cy + 0.5F);
		drawArrowShape(ctx, cx + 1, cy + 1, 0xFF1A1A1A);
		drawArrowShape(ctx, cx, cy, 0xFFFFFFFF);
		ctx.br$popMatrix();
	}

	private static void drawArrowShape(AxoRenderContext ctx, int cx, int cy, int color) {
		ctx.br$fillRect(cx, cy - 5, 1, 1, color);      // tip
		ctx.br$fillRect(cx - 1, cy - 4, 3, 1, color);
		ctx.br$fillRect(cx - 2, cy - 3, 5, 1, color);
		ctx.br$fillRect(cx - 1, cy - 2, 3, 1, color);
		ctx.br$fillRect(cx, cy - 1, 1, 4, color);      // shaft
	}

	private void drawNorthIndicator(AxoRenderContext ctx, int cx, int cy, int size, float mapRot) {
		// north (-Z) is "up" in content space; project it through the map rotation
		float dirX = Mth.sin(mapRot);
		float dirY = -Mth.cos(mapRot);
		int radius = size / 2 - 3;
		int x = cx + (int) (dirX * radius);
		int y = cy + (int) (dirY * radius);
		ctx.br$drawCenteredString("N", x, y - client.font.lineHeight / 2, 0xFFFFFFFF, true);
	}

	private void drawCircleMask(GuiGraphicsExtractor graphics, int cx, int cy) {
		boolean outlineOn = outline.get();
		int hash = maskParamsHash(outlineOn);
		if (hash != bakedMaskHash) {
			bakeMask(outlineOn);
			bakedMaskHash = hash;
		}
		if (maskTexture == null) {
			return;
		}
		int dim = texture.getGridSize();
		graphics.blit(RenderPipelines.GUI_TEXTURED, maskId(), cx - dim / 2, cy - dim / 2,
			0.0F, 0.0F, dim, dim, dim, dim, dim, dim, -1);
	}

	private Identifier maskId() {
		return Identifier.fromNamespaceAndPath("imperiumclient", "minimap/mask/v" + maskVersion);
	}

	private int maskParamsHash(boolean outlineOn) {
		int result = texture.getGridSize();
		result = 31 * result + getContentWidth();
		result = 31 * result + (outlineOn ? outlineWidth.get() : 0);
		result = 31 * result + (outlineOn ? outlineColor.get().toInt() : 0);
		return result;
	}

	/**
	 * Bakes the circle mask: transparent inside the circle, a ring in the outline colour along
	 * the rim (anti-aliased outer edge), transparent outside — so rotated square content
	 * poking past the rim is fully covered.
	 */
	private void bakeMask(boolean outlineOn) {
		int dim = texture.getGridSize();
		int radius = getContentWidth() / 2;
		int ring = outlineOn ? Math.min(outlineWidth.get(), radius - 1) : 0;
		int ringColor = outlineOn ? outlineColor.get().toInt() : 0xFFFFFFFF;
		if (maskTexture != null) {
			// DynamicTexture.close() releases its NativeImage as well
			maskTexture.close();
		}
		NativeImage image = new NativeImage(dim, dim, false);
		float center = dim / 2f;
		for (int y = 0; y < dim; y++) {
			for (int x = 0; x < dim; x++) {
				float dx = x + 0.5F - center;
				float dy = y + 0.5F - center;
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				int color = 0;
				if (dist >= radius - ring && dist < radius) {
					color = ringColor;
				} else if (dist >= radius && dist < radius + 1.0F) {
					color = ARGB.multiplyAlpha(ringColor, radius + 1.0F - dist);
				}
				image.setPixel(x, y, color);
			}
		}
		maskVersion++;
		maskTexture = new DynamicTexture(maskId()::toString, image);
		client.getTextureManager().register(maskId(), maskTexture);
		maskTexture.upload();
	}

	private void ensureRegistered() {
		if (registered) {
			return;
		}
		client.getTextureManager().register(texture.textureId(), texture.getTexture());
		texture.getTexture().upload();
		registered = true;
	}

	private int requiredGrid() {
		int size = mapSize.get();
		if (shape.get() == Shape.SQUARE && rotation.get() == Rotation.FOLLOW_CAMERA) {
			// the diagonal of the rotated window must stay inside the sampled grid
			return Mth.ceil(size * ROTATION_OVERSIZE);
		}
		return size;
	}

	private void syncBounds() {
		int size = mapSize.get();
		if (getContentWidth() != size || getContentHeight() != size) {
			setContentWidth(size);
			setContentHeight(size);
			setWidth(size);
			setHeight(size);
			onBoundsUpdate();
		}
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(shape);
		options.add(rotation);
		options.add(zoom);
		options.add(mapSize);
		options.add(opacity);
		options.add(showWaypoints);
		options.add(waypointLabels);
		options.add(showNorth);
		options.add(anchor);
		return options;
	}

	@Override
	public AnchorPoint getAnchor() {
		return anchor.get();
	}

	@Override
	public double getDefaultX() {
		return 1.0D;
	}

	public MinimapTexture getTexture() {
		return texture;
	}

	/** True once the map texture has been registered with the texture manager (render path ran). */
	public boolean isRegistered() {
		return registered;
	}

	public enum Shape {
		SQUARE,
		CIRCLE;

		@Override
		public String toString() {
			return "minimap.shape." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Rotation {
		NORTH_LOCK,
		FOLLOW_CAMERA;

		@Override
		public String toString() {
			return "minimap.rotation." + name().toLowerCase(Locale.ROOT);
		}
	}
}
