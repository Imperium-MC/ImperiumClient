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

import io.github.axolotlclient.AxolotlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * chunk_borders (Alpine parity): world-space boundary lines for the player's current chunk
 * (16x16 column) and, optionally, the containing region (512x512 grid).
 *
 * <p>26.3 has a gizmo layer exactly for this ({@link Gizmos#line} — the same path the
 * vanilla F3+G chunk border debug renderer uses). The lines are emitted at the tail of
 * {@code LevelRenderer.collectPerFrameRenderThreadGizmos()}, while that frame's render-thread
 * gizmo collector is active, and are drawn on top of the world.
 */
public class ChunkBorders {

	/**
	 * Blocks per region (32 chunks), used for the optional region border grid.
	 */
	public static final int REGION_SIZE = 512;

	private static final float CHUNK_LINE_WIDTH = 2.0F;
	private static final float REGION_LINE_WIDTH = 3.0F;

	/**
	 * Chunk base coordinate of a world block coordinate (works for negatives; the two's
	 * complement mask is equivalent to floorDiv/floorMul by 16).
	 */
	public static int chunkBase(int blockCoord) {
		return blockCoord & ~0xF;
	}

	/**
	 * Region base coordinate of a world block coordinate (multiples of 512).
	 */
	public static int regionBase(int blockCoord) {
		return blockCoord & ~(REGION_SIZE - 1);
	}

	public static void emit() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!AxolotlClient.config().chunkBorders.get() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		ClientLevel level = minecraft.level;
		int blockX = Mth.floor(minecraft.player.getX());
		int blockZ = Mth.floor(minecraft.player.getZ());
		int minY = level.getMinY();
		int maxY = level.getMaxY() + 1;

		emitCell(chunkBase(blockX), chunkBase(blockZ), 16,
			AxolotlClient.config().chunkBorderColor.get().toInt(), CHUNK_LINE_WIDTH, minY, maxY);

		if (AxolotlClient.config().regionBorders.get()) {
			emitCell(regionBase(blockX), regionBase(blockZ), REGION_SIZE,
				AxolotlClient.config().regionBorderColor.get().toInt(), REGION_LINE_WIDTH, minY, maxY);
		}
	}

	/**
	 * Draws the vertical pillars and the top/bottom perimeter of a cell whose north-west
	 * corner is ({@code baseX}, {@code baseZ}) and whose edge length is {@code size}.
	 */
	private static void emitCell(int baseX, int baseZ, int size, int color, float width, int minY, int maxY) {
		int x1 = baseX;
		int z1 = baseZ;
		int x2 = baseX + size;
		int z2 = baseZ + size;

		line(x1, minY, z1, x1, maxY, z1, color, width);
		line(x2, minY, z1, x2, maxY, z1, color, width);
		line(x2, minY, z2, x2, maxY, z2, color, width);
		line(x1, minY, z2, x1, maxY, z2, color, width);

		for (int y : new int[]{minY, maxY}) {
			line(x1, y, z1, x2, y, z1, color, width);
			line(x2, y, z1, x2, y, z2, color, width);
			line(x2, y, z2, x1, y, z2, color, width);
			line(x1, y, z2, x1, y, z1, color, width);
		}
	}

	private static void line(int x1, int y1, int z1, int x2, int y2, int z2, int color, float width) {
		GizmoProperties properties = Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), color, width);
		if (properties != null) {
			properties.setAlwaysOnTop();
		}
	}
}
