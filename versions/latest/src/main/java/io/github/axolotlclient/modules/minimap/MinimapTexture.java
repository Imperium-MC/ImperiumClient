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

import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.axolotlclient.AxolotlClientCommon;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.MapColor;

/**
 * The minimap's chunk-colour cache (Alpine "minimap" module, parity spec M3).
 *
 * <p>Conceptually the same as every chunk-colour minimap (Xaero/JourneyMap/Maple): a square
 * sample grid where each texel stores the {@link MapColor}-derived colour of the top-most
 * non-air block of one world column (shaded by slope and water depth), backed by a
 * {@link NativeImage}+{@link DynamicTexture} and re-uploaded only when a tick actually wrote
 * pixels — the exact pattern vanilla's map item uses (MapTextureManager).</p>
 *
 * <p>Updates are incremental: the grid is anchored to a zoom-quantised world position, so
 * walking shifts the existing content (array copies, no re-sampling) and only the exposed
 * edge strips are sampled. Chunk loads mark their grid region dirty, and a periodic
 * full-grid rescan (every {@link #RESCAN_INTERVAL_TICKS}) picks up block changes within
 * that delay. Sampling is budgeted per tick so a full grid fill never hitches a frame.</p>
 */
public final class MinimapTexture {

	/** Columns sampled per tick at most; a 192x192 grid fills in ~5 ticks. */
	private static final int SAMPLE_BUDGET = 8192;
	/** Full-grid rescan cadence: catches block changes within loaded chunks. */
	private static final int RESCAN_INTERVAL_TICKS = 40;
	private static final int MAX_WATER_DEPTH_SHADE = 8;

	private int gridSize;
	private int zoom = 2;
	private long anchorX, anchorZ;
	private int[] colors = new int[0];
	private int[] heights = new int[0];
	private boolean[] dirty = new boolean[0];
	private boolean allDirty = true;
	private int rescanCounter;
	private int version;
	private NativeImage image;
	private DynamicTexture texture;
	private boolean uploadPending;
	private final Reference2IntMap<BlockState> colorCache = new Reference2IntOpenHashMap<>();
	private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
	private ClientLevel level;

	public Identifier textureId() {
		return Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "minimap/map/v" + version);
	}

	public DynamicTexture getTexture() {
		return texture;
	}

	public int getGridSize() {
		return gridSize;
	}

	public int getZoom() {
		return zoom;
	}

	public void setZoom(int zoom) {
		if (zoom == this.zoom) {
			return;
		}
		// The anchor is quantised to the zoom grid, so a zoom change invalidates every texel.
		this.zoom = zoom;
		markAllDirty();
	}

	public long getAnchorX() {
		return anchorX;
	}

	public long getAnchorZ() {
		return anchorZ;
	}

	public void setLevel(ClientLevel level) {
		this.level = level;
		reset();
	}

	/** (Re)allocates the grid; called when the display size or rotation mode changes. */
	public void rebuild(int gridSize) {
		if (gridSize == this.gridSize && image != null) {
			return;
		}
		this.gridSize = gridSize;
		version++;
		colors = new int[gridSize * gridSize];
		heights = new int[gridSize * gridSize];
		dirty = new boolean[gridSize * gridSize];
		if (texture != null) {
			texture.close(); // DynamicTexture.close() also releases its NativeImage
		} else if (image != null) {
			image.close();
		}
		image = new NativeImage(gridSize, gridSize, false);
		texture = new DynamicTexture((Supplier<String>) this::label, image);
		reset();
	}

	private String label() {
		return "imperiumclient minimap map texture";
	}

	private void reset() {
		java.util.Arrays.fill(colors, 0);
		java.util.Arrays.fill(heights, Integer.MIN_VALUE);
		markAllDirty();
	}

	public void markAllDirty() {
		allDirty = true;
		uploadPending = true;
	}

	public boolean isUploadPending() {
		return uploadPending;
	}

	public void clearUploadPending() {
		uploadPending = false;
	}

	/** Marks the grid region covered by a loaded chunk for resampling. */
	public void markChunkDirty(ChunkPos pos) {
		if (allDirty || level == null) {
			return;
		}
		markWorldRectDirty(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ());
	}

	private void markWorldRectDirty(int minX, int minZ, int maxX, int maxZ) {
		int half = gridSize / 2;
		int x0 = Math.max(0, worldToTexelX(minX) + half);
		int x1 = Math.min(gridSize - 1, worldToTexelX(maxX) + half);
		int z0 = Math.max(0, worldToTexelZ(minZ) + half);
		int z1 = Math.min(gridSize - 1, worldToTexelZ(maxZ) + half);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				dirty[z * gridSize + x] = true;
			}
		}
	}

	// world -> grid-space texel (0 = grid centre), unclamped
	int worldToTexelX(int worldX) {
		return (int) Math.floor((worldX - anchorX) / (double) zoom);
	}

	int worldToTexelZ(int worldZ) {
		return (int) Math.floor((worldZ - anchorZ) / (double) zoom);
	}

	/** Runs once per client tick: recenters the grid on the player, then samples within budget. */
	public void tick(ClientLevel level, double playerX, double playerZ) {
		if (level == null) {
			return;
		}
		if (this.level != level) {
			setLevel(level);
		}
		if (image == null) {
			return;
		}
		recenter(playerX, playerZ);

		if (--rescanCounter <= 0) {
			rescanCounter = RESCAN_INTERVAL_TICKS;
			markAllDirty();
		}

		int budget = SAMPLE_BUDGET;
		boolean wrote = false;
		if (allDirty) {
			// Keep the old colours visible while the resample runs, so periodic rescans
			// don't blank the map; only teleports/zoom changes clear via reset()/big shifts.
			java.util.Arrays.fill(dirty, true);
			allDirty = false;
		}
		for (int t = 0; t < dirty.length && budget > 0; t++) {
			if (!dirty[t]) {
				continue;
			}
			dirty[t] = false;
			budget--;
			int x = t % gridSize;
			int z = t / gridSize;
			int color = sampleColumn(x, z);
			colors[t] = color;
			image.setPixel(x, z, color);
			wrote = true;
		}
		if (wrote) {
			uploadPending = true;
		}
	}

	/** Shifts the grid so its centre stays on the zoom-quantised player position. */
	private void recenter(double playerX, double playerZ) {
		long targetX = Math.round(playerX / zoom) * (long) zoom;
		long targetZ = Math.round(playerZ / zoom) * (long) zoom;
		int shiftX = (int) ((targetX - anchorX) / zoom);
		int shiftZ = (int) ((targetZ - anchorZ) / zoom);
		if (shiftX == 0 && shiftZ == 0) {
			return;
		}
		anchorX = targetX;
		anchorZ = targetZ;
		if (Math.abs(shiftX) >= gridSize || Math.abs(shiftZ) >= gridSize) {
			// Teleport-scale jump: the old content is from a completely different place, blank it.
			java.util.Arrays.fill(colors, 0);
			java.util.Arrays.fill(heights, Integer.MIN_VALUE);
			markAllDirty();
			return;
		}
		if (allDirty) {
			return;
		}
		shiftBy(shiftX, shiftZ);
	}

	private void shiftBy(int shiftX, int shiftZ) {
		int[] oldColors = colors.clone();
		int[] oldHeights = heights.clone();
		boolean[] oldDirty = dirty.clone();
		java.util.Arrays.fill(colors, 0);
		java.util.Arrays.fill(heights, Integer.MIN_VALUE);
		java.util.Arrays.fill(dirty, true);
		for (int z = 0; z < gridSize; z++) {
			int srcZ = z - shiftZ;
			if (srcZ < 0 || srcZ >= gridSize) {
				continue;
			}
			for (int x = 0; x < gridSize; x++) {
				int srcX = x - shiftX;
				if (srcX < 0 || srcX >= gridSize) {
					continue;
				}
				int dst = z * gridSize + x;
				int src = srcZ * gridSize + srcX;
				colors[dst] = oldColors[src];
				heights[dst] = oldHeights[src];
				dirty[dst] = oldDirty[src];
			}
		}
		uploadPending = true;
	}

	/** Sub-texel player offset inside the centre texel, in grid-space texel units. */
	public double playerOffsetX(double playerX) {
		return (playerX - anchorX) / (double) zoom;
	}

	public double playerOffsetZ(double playerZ) {
		return (playerZ - anchorZ) / (double) zoom;
	}

	/** Samples the top-most non-air block of one column; returns ARGB (0 alpha = unloaded). */
	private int sampleColumn(int gridX, int gridZ) {
		int half = gridSize / 2;
		int blockX = (int) anchorX + (gridX - half) * zoom;
		int blockZ = (int) anchorZ + (gridZ - half) * zoom;
		ClientChunkCache cache = level.getChunkSource();
		LevelChunk chunk = cache.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false);
		if (chunk == null) {
			heights[gridZ * gridSize + gridX] = Integer.MIN_VALUE;
			return 0;
		}
		LevelChunkSection[] sections = chunk.getSections();
		int minY = level.getMinY();
		int baseX = blockX & 15;
		int baseZ = blockZ & 15;
		for (int s = sections.length - 1; s >= 0; s--) {
			LevelChunkSection section = sections[s];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			for (int y = 15; y >= 0; y--) {
				int worldY = minY + s * 16 + y;
				BlockState state = section.getBlockState(baseX, y, baseZ);
				if (state.isAir()) {
					continue;
				}
				heights[gridZ * gridSize + gridX] = worldY;
				return columnColor(state, worldY, blockX, blockZ, gridX, gridZ);
			}
		}
		heights[gridZ * gridSize + gridX] = Integer.MIN_VALUE;
		return 0;
	}

	private int columnColor(BlockState state, int topY, int blockX, int blockZ, int gridX, int gridZ) {
		int base = baseColor(state, blockX, topY, blockZ);
		if (base == 0) {
			return 0;
		}
		if (state.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
			int depth = 1;
			ClientChunkCache cache = level.getChunkSource();
			LevelChunk chunk = cache.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false);
			if (chunk != null) {
				int minY = level.getMinY();
				for (int y = topY - 1; y >= minY && depth < MAX_WATER_DEPTH_SHADE; y--) {
					BlockState below = sectionState(chunk, blockX, y, blockZ, minY);
					if (below == null || below.getBlock() != net.minecraft.world.level.block.Blocks.WATER) {
						break;
					}
					depth++;
				}
			}
			return shade(base, 1.0F - 0.07F * depth);
		}
		return shade(base, slopeFactor(gridX, gridZ, topY));
	}

	/** Base opaque colour for a block state (0 = transparent on the map), cached per interned state. */
	private int baseColor(BlockState state, int blockX, int worldY, int blockZ) {
		if (colorCache.containsKey(state)) {
			return colorCache.getInt(state);
		}
		MapColor mapColor = state.getMapColor(level, scratchPos.set(blockX, worldY, blockZ));
		int base = mapColor == null || mapColor == MapColor.NONE ? 0 : 0xFF000000 | mapColor.col;
		colorCache.put(state, base);
		return base;
	}

	private BlockState sectionState(LevelChunk chunk, int blockX, int worldY, int blockZ, int minY) {
		int index = (worldY - minY) >> 4;
		LevelChunkSection[] sections = chunk.getSections();
		if (index < 0 || index >= sections.length) {
			return null;
		}
		LevelChunkSection section = sections[index];
		if (section == null) {
			return null;
		}
		return section.getBlockState(blockX & 15, worldY & 15, blockZ & 15);
	}

	/** Classic map embossing: lit when lower than the north/west neighbours, shaded when higher. */
	private float slopeFactor(int gridX, int gridZ, int topY) {
		int north = neighborHeight(gridX, gridZ - 1);
		int west = neighborHeight(gridX - 1, gridZ);
		int slope = 0;
		if (north != Integer.MIN_VALUE) {
			slope += Integer.compare(topY, north);
		}
		if (west != Integer.MIN_VALUE) {
			slope += Integer.compare(topY, west);
		}
		if (slope > 0) {
			return 1.0F - 0.11F * slope;
		}
		if (slope < 0) {
			return 1.0F - 0.06F * slope;
		}
		return 1.0F;
	}

	private int neighborHeight(int gridX, int gridZ) {
		if (gridX < 0 || gridX >= gridSize || gridZ < 0 || gridZ >= gridSize) {
			return Integer.MIN_VALUE;
		}
		return heights[gridZ * gridSize + gridX];
	}

	private static int shade(int argb, float factor) {
		if (factor >= 0.999F && factor <= 1.001F) {
			return argb;
		}
		int a = (argb >>> 24) & 0xFF;
		int r = clampChannel((int) (((argb >>> 16) & 0xFF) * factor));
		int g = clampChannel((int) (((argb >>> 8) & 0xFF) * factor));
		int b = clampChannel((int) ((argb & 0xFF) * factor));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int clampChannel(int value) {
		return value < 0 ? 0 : Math.min(value, 255);
	}

	/** Number of texels that currently hold a sampled colour (for tests and diagnostics). */
	public int sampledTexels() {
		int count = 0;
		for (int color : colors) {
			if ((color >>> 24) != 0) {
				count++;
			}
		}
		return count;
	}

	/** True while any texel still awaits its first sample after a fill/reset. */
	public boolean hasPendingWork() {
		if (allDirty) {
			return true;
		}
		for (boolean b : dirty) {
			if (b) {
				return true;
			}
		}
		return false;
	}
}
