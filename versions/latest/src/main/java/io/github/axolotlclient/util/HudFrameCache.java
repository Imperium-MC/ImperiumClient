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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.mixin.GuiGraphicsExtractorAccessor;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * {@code hud_frame_limit} (performance category, M3): HUD entries whose content does not
 * change on its own re-RECORD into the {@link GuiRenderState} at most {@code limit} times per
 * second; between records the recorded elements are REPLAYED by re-submitting the very same
 * render-state objects.
 *
 * <p>This works because every GUI element the extractor submits is an immutable snapshot
 * ({@code GuiTextRenderState} and friends copy the pose, and their lazily prepared glyph data
 * is reused, not re-laid-out) that vanilla allocates fresh per submit and never pools — so
 * holding the references between frames and re-adding them is equivalent to a fresh render,
 * minus all the layout, item-model and text-format work. Entries that report
 * {@link HudEntry#dynamicContent()} always render live and are never captured, so data
 * readouts (FPS, CPS, coordinates, ping...) never visibly freeze.
 *
 * <p>Ordering is preserved per entry: the capture is armed only around each cacheable entry's
 * render, buckets are keyed by entry id, and both record and replay walk the same z-sorted
 * entry list, re-submitting through the same public add methods so vanilla's node
 * navigation and debug-rectangle logic run exactly as on a record frame.
 *
 * <p>The cache invalidates itself when the limit is off, the window is resized, any entry's
 * enabled/hidden state or bounds change, or enough frames have passed at the current frame
 * rate to cover one record interval (frame-count pacing off the game's own frame-time source,
 * so a throttled or vsynced game simply re-records every frame instead of serving stale
 * recordings to hit a rate it cannot reach).
 */
public final class HudFrameCache {

	private HudFrameCache() {
	}

	private static final Map<AxoIdentifier, List<Runnable>> buckets = new HashMap<>();
	private static final List<Runnable> currentBucket = new ArrayList<>();

	private static boolean capturing;
	private static GuiRenderState captureTarget;
	// Pacing state: records are spaced by frame count, derived from a rolling average of
	// Minecraft.getFrameTimeNs() (the debug-FPS source) so the cache adapts to any frame
	// rate without depending on wall-clock behaviour under throttling.
	private static double emaFrameMillis = 16.0;
	private static int framesSinceRecord;
	private static int cachedWidth = -1;
	private static int cachedHeight = -1;
	private static long cachedSignature;
	private static boolean hasRecord;
	private static boolean lastFrameReplayed;

	// Evidence for the gametest/benchmark: frames served from the recording vs re-recorded.
	private static long replayedFrames;
	private static long recordedFrames;
	// Why the last frame re-recorded instead of replaying (gametest debugging evidence).
	private static String lastInvalidation = "never recorded";

	/**
	 * Renders the given z-sorted entries, capturing or replaying the cacheable ones according
	 * to {@code hud_frame_limit}. Called from the latest {@code HudManager.render}.
	 */
	public static void render(AxoRenderContext context, float delta, List<HudEntry> ordered) {
		int limit = AxolotlClient.config() == null ? 0 : AxolotlClient.config().hudFrameLimit.get();
		if (limit <= 0 || !(context instanceof GuiGraphicsExtractor extractor)) {
			if (hasRecord) {
				clear();
			}
			renderEntries(context, delta, ordered, null);
			lastFrameReplayed = false;
			return;
		}

		GuiRenderState state = ((GuiGraphicsExtractorAccessor) extractor).axolotlclient$guiRenderState();
		long frameTimeNs = Minecraft.getInstance().getFrameTimeNs();
		if (frameTimeNs > 0) {
			emaFrameMillis = emaFrameMillis * 0.9D + (frameTimeNs / 1_000_000.0D) * 0.1D;
		}
		// How many frames fit into one record interval at the current frame rate; at or
		// below the limit this is 1 and every frame re-records (a cache slower than the
		// game must never serve stale frames to keep up a rate it cannot reach).
		int frameBudget = Math.max(1, (int) Math.floor((1000.0D / limit) / Math.max(0.01D, emaFrameMillis)));
		boolean expired = framesSinceRecord >= frameBudget;
		boolean resized = extractor.guiWidth() != cachedWidth || extractor.guiHeight() != cachedHeight;
		long signature = signature(ordered);
		if (!hasRecord || expired || resized || signature != cachedSignature) {
			lastInvalidation = (!hasRecord ? "no record" : expired ? "expired" : resized ? "resized" : "signature");
			record(context, extractor, state, delta, ordered, signature);
			lastFrameReplayed = false;
		} else {
			framesSinceRecord++;
			replay(context, state, delta, ordered);
			lastFrameReplayed = true;
		}
	}

	/**
	 * Whether the most recent frame was served from the recording (gametest evidence).
	 */
	public static boolean lastFrameReplayed() {
		return lastFrameReplayed;
	}

	public static long replayedFrames() {
		return replayedFrames;
	}

	public static long recordedFrames() {
		return recordedFrames;
	}

	public static void resetCounters() {
		replayedFrames = 0;
		recordedFrames = 0;
	}

	/**
	 * Why the most recent frame re-record instead of replaying (gametest debugging evidence).
	 */
	public static String lastInvalidation() {
		return lastInvalidation;
	}

	private static void record(AxoRenderContext context, GuiGraphicsExtractor extractor, GuiRenderState state,
							   float delta, List<HudEntry> ordered, long signature) {
		cachedWidth = extractor.guiWidth();
		cachedHeight = extractor.guiHeight();
		cachedSignature = signature;
		framesSinceRecord = 0;
		buckets.clear();
		hasRecord = true;
		recordedFrames++;
		try {
			renderEntries(context, delta, ordered, state);
		} finally {
			capturing = false;
			captureTarget = null;
			currentBucket.clear();
		}
	}

	private static void replay(AxoRenderContext context, GuiRenderState state, float delta, List<HudEntry> ordered) {
		replayedFrames++;
		for (HudEntry hud : ordered) {
			if (!hud.isEnabled() || hud.isHidden()) {
				continue;
			}
			if (hud.dynamicContent()) {
				hud.render(context, delta);
				continue;
			}
			List<Runnable> bucket = buckets.get(hud.getId());
			if (bucket != null) {
				for (Runnable resubmit : bucket) {
					resubmit.run();
				}
			}
		}
	}

	private static void renderEntries(AxoRenderContext context, float delta, List<HudEntry> ordered, GuiRenderState recording) {
		for (HudEntry hud : ordered) {
			if (!hud.isEnabled() || hud.isHidden()) {
				continue;
			}
			if (recording == null || hud.dynamicContent()) {
				hud.render(context, delta);
				continue;
			}
			// Cacheable entry during a record frame: arm the capture around exactly this
			// entry's render so its submitted elements land in its bucket.
			currentBucket.clear();
			capturing = true;
			captureTarget = recording;
			try {
				hud.render(context, delta);
			} finally {
				capturing = false;
				captureTarget = null;
			}
			if (!currentBucket.isEmpty()) {
				buckets.put(hud.getId(), new ArrayList<>(currentBucket));
			}
		}
	}

	/**
	 * Cheap fingerprint of everything the recording depends on: which entries render, where
	 * they sit, and how big they are. Any change re-records instead of replaying a stale
	 * layout.
	 */
	private static long signature(List<HudEntry> ordered) {
		long hash = 1L;
		for (HudEntry hud : ordered) {
			long entry = hud.getId().hashCode();
			entry = entry * 31L + (hud.isEnabled() ? 1 : 0);
			entry = entry * 31L + (hud.isHidden() ? 1 : 0);
			if (hud.isEnabled() && !hud.isHidden() && !hud.dynamicContent()) {
				Rectangle bounds = hud.getTrueBounds();
				entry = entry * 31L + bounds.x;
				entry = entry * 31L + bounds.y;
				entry = entry * 31L + bounds.width;
				entry = entry * 31L + bounds.height;
			}
			hash = hash * 31L + entry;
		}
		return hash;
	}

	private static void clear() {
		buckets.clear();
		hasRecord = false;
		cachedWidth = -1;
		cachedHeight = -1;
	}

	// Capture hooks, invoked by GuiRenderStateCaptureMixin for every element submission on
	// the render thread while a cacheable entry renders. Each stores a re-submission runnable
	// closing over the (persistent) render state and the immutable element.

	public static void captureText(GuiTextRenderState element) {
		if (!capturing) {
			return;
		}
		GuiRenderState target = captureTarget;
		currentBucket.add(() -> target.addText(element));
	}

	public static void captureItem(GuiItemRenderState element) {
		if (!capturing) {
			return;
		}
		GuiRenderState target = captureTarget;
		currentBucket.add(() -> target.addItem(element));
	}

	public static void captureGuiElement(GuiElementRenderState element) {
		if (!capturing) {
			return;
		}
		GuiRenderState target = captureTarget;
		currentBucket.add(() -> target.addGuiElement(element));
	}

	public static void capturePicturesInPicture(PictureInPictureRenderState element) {
		if (!capturing) {
			return;
		}
		GuiRenderState target = captureTarget;
		currentBucket.add(() -> target.addPicturesInPictureState(element));
	}
}
