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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.axolotlclient.AxolotlClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * light_level (Photon parity): a keybind-toggled spawn-safety overlay. Block tops around the
 * player are tinted by their effective light level — green where hostile mobs cannot spawn
 * (light &gt; 7 after the sky-darkening scale), red where they can.
 *
 * <p><b>Sampling budget.</b> Column scanning is the expensive part (sturdy-top + two-air +
 * light-layer reads per position), so it never runs unbounded on the render path: the client
 * tick advances a work queue of at most {@link #COLUMNS_PER_TICK} columns per tick, rebuilt
 * whenever the player anchor moves more than {@link #ANCHOR_SLACK} blocks or every
 * {@link #REBUILD_INTERVAL_TICKS} ticks (world/light changes are picked up by that periodic
 * rebuild). Results are cached per column and rendering only walks the cache. Unloaded chunks
 * are skipped ({@code hasChunk}) — both the scan and the render stay capped to loaded chunks.</p>
 *
 * <p><b>Render path.</b> Each surface block gets a Photon-style colour cross (two
 * {@link Gizmos#line} segments) plus a thin translucent cuboid slab tint, submitted through the
 * frame's gizmo collector window that {@link #init()} opens at Fabric's
 * {@code LevelRenderEvents.BEFORE_GIZMOS} — the same submission pattern the WorldEdit CUI
 * selection uses, drained by {@code LevelRenderer.finalizeGizmoCollection} a moment later.</p>
 */
public final class LightLevelOverlay {

	/**
	 * A hostile mob can spawn at light {@code <= 7} — light {@code > 7} is spawn-safe. The
	 * threshold applies to the scaled/effective light, not the raw sky value.
	 */
	public static final int SAFE_LIGHT_THRESHOLD = 7;

	/** Columns sampled per client tick (the sampling budget). */
	private static final int COLUMNS_PER_TICK = 128;
	/** Full rebuild cadence in ticks; also how fast light changes get picked up. */
	private static final int REBUILD_INTERVAL_TICKS = 20;
	/** Anchor slack in blocks: rebuild when the player moved further than this from the anchor. */
	private static final int ANCHOR_SLACK = 2;
	/** Vertical scan window above/below the player's feet. */
	private static final int VERTICAL_UP = 4;
	private static final int VERTICAL_DOWN = 8;
	/** Height of the tint slab on top of a surface block, in blocks. */
	private static final double SLAB_HEIGHT = 0.03;
	/** Cross arm length and elevation of the marker lines above the block top, in blocks. */
	private static final double CROSS_ARM = 0.35;
	private static final double LINE_LIFT = 0.01;
	private static final float LINE_WIDTH = 2.0F;

	/**
	 * A sampled column: the y of the topmost spawnable surface in the window, and whether that
	 * spot is spawn-safe. {@code null} sample = no spawnable surface (or unloaded chunk).
	 */
	public record Sample(int y, boolean safe) {
	}

	private static final Map<Long, Sample> SAMPLES = new HashMap<>();
	private static final ArrayDeque<Long> PENDING = new ArrayDeque<>();
	private static int anchorX, anchorY, anchorZ;
	private static String dimension = "";
	private static long lastRebuildGameTime = Long.MIN_VALUE;
	private static boolean registered;
	/**
	 * Diagnostics: how many markers the last {@link #emit()} drew (render-path introspection
	 * for the game test — distinguishes "nothing sampled" from "sampled but not rendered").
	 */
	private static volatile int lastEmittedMarkers;

	private LightLevelOverlay() {
	}

	/** Registers the client-tick sampler and the render hook. Called once from the client entry point. */
	public static synchronized void init() {
		if (!registered) {
			registered = true;
			ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
			// The sanctioned submission point (same pattern as the WorldEdit CUI selection):
			// Fabric fires BEFORE_GIZMOS right before LevelRenderer.finalizeGizmoCollection
			// drains the render-thread collector, and nothing calls
			// collectPerFrameRenderThreadGizmos() in a normal frame unless a mod opens that
			// window itself — so the overlay opens it, submits, and lets the frame drain it.
			LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
				if (!AxolotlClient.config().lightOverlay.get()) {
					return;
				}
				try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
					emit();
				}
			});
		}
	}

	/**
	 * Effective light level "after scale" (Photon semantics): the sky layer darkened by the
	 * current sky darkening (time of day, rain), the block layer untouched. Pure function.
	 */
	public static int effectiveLight(int blockLight, int skyLight, int skyDarken) {
		return Math.max(blockLight, skyLight - skyDarken);
	}

	/**
	 * Whether a position with these raw light values is spawn-safe (green) or mobs can spawn
	 * there (red). Pure function.
	 */
	public static boolean isSpawnSafe(int blockLight, int skyLight, int skyDarken) {
		return effectiveLight(blockLight, skyLight, skyDarken) > SAFE_LIGHT_THRESHOLD;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (!AxolotlClient.config().lightOverlay.get() || level == null || minecraft.player == null) {
			if (!SAMPLES.isEmpty() || !PENDING.isEmpty()) {
				SAMPLES.clear();
				PENDING.clear();
				lastRebuildGameTime = Long.MIN_VALUE;
			}
			return;
		}

		int blockX = Mth.floor(minecraft.player.getX());
		int blockY = Mth.floor(minecraft.player.getY());
		int blockZ = Mth.floor(minecraft.player.getZ());
		String currentDimension = level.dimension().identifier().toString();
		long gameTime = level.getGameTime();
		boolean dimensionChanged = !currentDimension.equals(dimension);
		boolean moved = Math.abs(blockX - anchorX) > ANCHOR_SLACK
			|| Math.abs(blockY - anchorY) > ANCHOR_SLACK
			|| Math.abs(blockZ - anchorZ) > ANCHOR_SLACK;
		boolean stale = gameTime - lastRebuildGameTime >= REBUILD_INTERVAL_TICKS;
		if (dimensionChanged) {
			// Samples are world state — none of them mean anything in the new dimension.
			SAMPLES.clear();
		}
		if (dimensionChanged || moved || stale) {
			anchorX = blockX;
			anchorY = blockY;
			anchorZ = blockZ;
			dimension = currentDimension;
			lastRebuildGameTime = gameTime;
			rebuildQueue(AxolotlClient.config().lightOverlayRadius.get());
		}

		for (int i = 0; i < COLUMNS_PER_TICK && !PENDING.isEmpty(); i++) {
			long column = PENDING.poll();
			int x = unpackX(column);
			int z = unpackZ(column);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			Sample sample = sampleColumn(level, x, z);
			if (sample != null) {
				SAMPLES.put(column, sample);
			} else {
				SAMPLES.remove(column);
			}
		}
	}

	/**
	 * Refills the pending queue with every column of the radius around the anchor, closest
	 * first (so the area around the player resolves first after a rebuild), and drops cached
	 * samples that fell out of the radius.
	 */
	private static void rebuildQueue(int radius) {
		PENDING.clear();
		List<Long> columns = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				columns.add(pack(anchorX + dx, anchorZ + dz));
			}
		}
		columns.sort(Comparator.comparingLong(LightLevelOverlay::distanceToAnchorSq));
		PENDING.addAll(columns);

		int pruneRadius = radius + 4;
		Iterator<Long> iterator = SAMPLES.keySet().iterator();
		while (iterator.hasNext()) {
			long column = iterator.next();
			if (Math.abs(unpackX(column) - anchorX) > pruneRadius || Math.abs(unpackZ(column) - anchorZ) > pruneRadius) {
				iterator.remove();
			}
		}
	}

	private static long distanceToAnchorSq(long column) {
		long dx = unpackX(column) - anchorX;
		long dz = unpackZ(column) - anchorZ;
		return dx * dx + dz * dz;
	}

	/**
	 * Finds the topmost spawnable surface of a column within the vertical window: a block with a
	 * sturdy top and no fluid, with two collision-free positions above it. Reads the light layers
	 * at the position a mob would occupy. Returns {@code null} if the window holds no such spot.
	 */
	private static Sample sampleColumn(ClientLevel level, int x, int z) {
		int top = anchorY + VERTICAL_UP;
		int bottom = anchorY - VERTICAL_DOWN;
		for (int y = top; y >= bottom; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (!state.isFaceSturdy(level, pos, Direction.UP) || !level.getFluidState(pos).isEmpty()) {
				continue;
			}
			BlockPos head = pos.above();
			BlockState headState = level.getBlockState(head);
			BlockState headAboveState = level.getBlockState(head.above());
			if (!headState.getCollisionShape(level, head).isEmpty()
				|| !level.getFluidState(head).isEmpty()
				|| !headAboveState.getCollisionShape(level, head.above()).isEmpty()) {
				continue;
			}
			boolean safe = isSpawnSafe(
				level.getBrightness(LightLayer.BLOCK, head),
				level.getBrightness(LightLayer.SKY, head),
				level.getSkyDarken());
			return new Sample(y, safe);
		}
		return null;
	}

	/**
	 * Draws the cached samples on the surface blocks: a Photon-style colour cross on each block
	 * top (two short {@link Gizmos#line} segments) plus a translucent tint slab over the block
	 * for the area effect. Must only be called while a gizmo collector window is active —
	 * {@link #init()} opens one per frame at BEFORE_GIZMOS; outside a window the submissions
	 * are silently refused.
	 */
	public static void emit() {
		if (!AxolotlClient.config().lightOverlay.get() || SAMPLES.isEmpty()) {
			lastEmittedMarkers = 0;
			return;
		}
		int safeColor = AxolotlClient.config().lightOverlaySafeColor.get().toInt();
		int unsafeColor = AxolotlClient.config().lightOverlayUnsafeColor.get().toInt();
		int emitted = 0;
		for (Map.Entry<Long, Sample> entry : SAMPLES.entrySet()) {
			Sample sample = entry.getValue();
			int x = unpackX(entry.getKey());
			int z = unpackZ(entry.getKey());
			int color = sample.safe() ? safeColor : unsafeColor;

			// Colour cross on the block top, inset like the classic light-level overlays.
			double cx = x + 0.5;
			double cz = z + 0.5;
			double top = sample.y() + 1.0 + LINE_LIFT;
			line(new Vec3(cx - CROSS_ARM, top, cz), new Vec3(cx + CROSS_ARM, top, cz), color);
			line(new Vec3(cx, top, cz - CROSS_ARM), new Vec3(cx, top, cz + CROSS_ARM), color);

			// Translucent tint slab across the whole block top.
			AABB slab = new AABB(x, sample.y() + 1.0, z,
				x + 1.0, sample.y() + 1.0 + SLAB_HEIGHT, z + 1.0);
			GizmoProperties properties = Gizmos.cuboid(slab, GizmoStyle.fill(color));
			if (properties != null) {
				properties.setAlwaysOnTop();
			}
			emitted++;
		}
		lastEmittedMarkers = emitted;
	}

	private static void line(Vec3 from, Vec3 to, int color) {
		GizmoProperties properties = Gizmos.line(from, to, color, LINE_WIDTH);
		if (properties != null) {
			properties.setAlwaysOnTop();
		}
	}

	/**
	 * Packs a column into a key: 26 bits per coordinate is ample for the +-30M block range and
	 * keeps the sign.
	 */
	private static long pack(int x, int z) {
		return ((long) x & 0x3FFFFFFL) << 26 | ((long) z & 0x3FFFFFFL);
	}

	private static int unpackX(long column) {
		return (int) (column << 12 >> 38);
	}

	private static int unpackZ(long column) {
		return (int) (column << 38 >> 38);
	}

	/**
	 * Render-state introspection (used by the game test): how many columns currently carry a
	 * sample.
	 */
	public static int cachedSampleCount() {
		return SAMPLES.size();
	}

	/**
	 * Render-state introspection (used by the game test): the cached sample of one column, or
	 * {@code null} when that column has no spawnable surface in the window or is not loaded.
	 */
	public static Sample cachedSample(int x, int z) {
		return SAMPLES.get(pack(x, z));
	}

	/**
	 * Render-path introspection (used by the game test): how many markers the most recent
	 * {@link #emit()} call drew. Non-zero while the screen shows nothing means the failure is
	 * downstream of the gizmo submission, not in the sampler.
	 */
	public static int lastEmittedMarkers() {
		return lastEmittedMarkers;
	}
}
