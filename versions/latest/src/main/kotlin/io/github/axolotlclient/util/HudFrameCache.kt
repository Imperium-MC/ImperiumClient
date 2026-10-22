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

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.mixin.GuiGraphicsExtractorAccessor
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.util.Rectangle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.GuiTextRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import kotlin.math.floor
import kotlin.math.max

/**
 * `hud_frame_limit` (performance category, M3): HUD entries whose content does not
 * change on its own re-RECORD into the [GuiRenderState] at most `limit` times per
 * second; between records the recorded elements are REPLAYED by re-submitting the very same
 * render-state objects.
 *
 * This works because every GUI element the extractor submits is an immutable snapshot
 * (`GuiTextRenderState` and friends copy the pose, and their lazily prepared glyph data
 * is reused, not re-laid-out) that vanilla allocates fresh per submit and never pools — so
 * holding the references between frames and re-adding them is equivalent to a fresh render,
 * minus all the layout, item-model and text-format work. Entries that report
 * [HudEntry.dynamicContent] always render live and are never captured, so data
 * readouts (FPS, CPS, coordinates, ping...) never visibly freeze.
 *
 * Ordering is preserved per entry: the capture is armed only around each cacheable entry's
 * render, buckets are keyed by entry id, and both record and replay walk the same z-sorted
 * entry list, re-submitting through the same public add methods so vanilla's node
 * navigation and debug-rectangle logic run exactly as on a record frame.
 *
 * The cache invalidates itself when the limit is off, the window is resized, any entry's
 * enabled/hidden state or bounds change, or enough frames have passed at the current frame
 * rate to cover one record interval (frame-count pacing off the game's own frame-time source,
 * so a throttled or vsynced game simply re-records every frame instead of serving stale
 * recordings to hit a rate it cannot reach).
 */
object HudFrameCache {

    private val buckets = HashMap<io.github.axolotlclient.bridge.util.AxoIdentifier, List<Runnable>>()
    private val currentBucket = ArrayList<Runnable>()

    private var capturing = false
    private var captureTarget: GuiRenderState? = null

    // Pacing state: records are spaced by frame count, derived from a rolling average of
    // Minecraft.getFrameTimeNs() (the debug-FPS source) so the cache adapts to any frame
    // rate without depending on wall-clock behaviour under throttling.
    private var emaFrameMillis = 16.0
    private var framesSinceRecord = 0
    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cachedSignature: Long = 0
    private var hasRecord = false
    private var lastFrameReplayed = false

    // Evidence for the gametest/benchmark: frames served from the recording vs re-recorded.
    private var replayedFrames: Long = 0
    private var recordedFrames: Long = 0

    // Why the last frame re-recorded instead of replaying (gametest debugging evidence).
    private var lastInvalidation = "never recorded"

    /**
     * Renders the given z-sorted entries, capturing or replaying the cacheable ones according
     * to `hud_frame_limit`. Called from the latest `HudManager.render`.
     */
    @JvmStatic
    fun render(context: AxoRenderContext, delta: Float, ordered: List<HudEntry>) {
        val limit = if (AxolotlClient.config() == null) 0 else AxolotlClient.config().hudFrameLimit.get()
        val extractor = context as? GuiGraphicsExtractor
        if (limit <= 0 || extractor == null) {
            if (hasRecord) {
                clear()
            }
            renderEntries(context, delta, ordered, null)
            lastFrameReplayed = false
            return
        }

        val state = (extractor as GuiGraphicsExtractorAccessor).`axolotlclient$guiRenderState`()
        val frameTimeNs = Minecraft.getInstance().frameTimeNs
        if (frameTimeNs > 0) {
            emaFrameMillis = emaFrameMillis * 0.9 + (frameTimeNs / 1_000_000.0) * 0.1
        }
        // How many frames fit into one record interval at the current frame rate; at or
        // below the limit this is 1 and every frame re-records (a cache slower than the
        // game must never serve stale frames to keep up a rate it cannot reach).
        val frameBudget = max(1, floor((1000.0 / limit) / max(0.01, emaFrameMillis)).toInt())
        val expired = framesSinceRecord >= frameBudget
        val resized = extractor.guiWidth() != cachedWidth || extractor.guiHeight() != cachedHeight
        val signature = signature(ordered)
        if (!hasRecord || expired || resized || signature != cachedSignature) {
            lastInvalidation = when {
                !hasRecord -> "no record"
                expired -> "expired"
                resized -> "resized"
                else -> "signature"
            }
            record(context, extractor, state, delta, ordered, signature)
            lastFrameReplayed = false
        } else {
            framesSinceRecord++
            replay(context, state, delta, ordered)
            lastFrameReplayed = true
        }
    }

    /**
     * Whether the most recent frame was served from the recording (gametest evidence).
     */
    @JvmStatic
    fun lastFrameReplayed(): Boolean = lastFrameReplayed

    @JvmStatic
    fun replayedFrames(): Long = replayedFrames

    @JvmStatic
    fun recordedFrames(): Long = recordedFrames

    @JvmStatic
    fun resetCounters() {
        replayedFrames = 0
        recordedFrames = 0
    }

    /**
     * Why the most recent frame re-record instead of replaying (gametest debugging evidence).
     */
    @JvmStatic
    fun lastInvalidation(): String = lastInvalidation

    private fun record(context: AxoRenderContext, extractor: GuiGraphicsExtractor, state: GuiRenderState, delta: Float, ordered: List<HudEntry>, signature: Long) {
        cachedWidth = extractor.guiWidth()
        cachedHeight = extractor.guiHeight()
        cachedSignature = signature
        framesSinceRecord = 0
        buckets.clear()
        hasRecord = true
        recordedFrames++
        try {
            renderEntries(context, delta, ordered, state)
        } finally {
            capturing = false
            captureTarget = null
            currentBucket.clear()
        }
    }

    private fun replay(context: AxoRenderContext, state: GuiRenderState, delta: Float, ordered: List<HudEntry>) {
        replayedFrames++
        for (hud in ordered) {
            if (!hud.isEnabled || hud.isHidden) {
                continue
            }
            if (hud.dynamicContent()) {
                hud.render(context, delta)
                continue
            }
            val bucket = buckets[hud.getId()]
            if (bucket != null) {
                for (resubmit in bucket) {
                    resubmit.run()
                }
            }
        }
    }

    private fun renderEntries(context: AxoRenderContext, delta: Float, ordered: List<HudEntry>, recording: GuiRenderState?) {
        for (hud in ordered) {
            if (!hud.isEnabled || hud.isHidden) {
                continue
            }
            if (recording == null || hud.dynamicContent()) {
                hud.render(context, delta)
                continue
            }
            // Cacheable entry during a record frame: arm the capture around exactly this
            // entry's render so its submitted elements land in its bucket.
            currentBucket.clear()
            capturing = true
            captureTarget = recording
            try {
                hud.render(context, delta)
            } finally {
                capturing = false
                captureTarget = null
            }
            if (currentBucket.isNotEmpty()) {
                buckets[hud.getId()] = ArrayList(currentBucket)
            }
        }
    }

    /**
     * Cheap fingerprint of everything the recording depends on: which entries render, where
     * they sit, and how big they are. Any change re-records instead of replaying a stale
     * layout.
     */
    private fun signature(ordered: List<HudEntry>): Long {
        var hash = 1L
        for (hud in ordered) {
            var entry = hud.getId().hashCode().toLong()
            entry = entry * 31L + (if (hud.isEnabled) 1 else 0)
            entry = entry * 31L + (if (hud.isHidden) 1 else 0)
            if (hud.isEnabled && !hud.isHidden && !hud.dynamicContent()) {
                val bounds: Rectangle = hud.trueBounds
                entry = entry * 31L + bounds.x
                entry = entry * 31L + bounds.y
                entry = entry * 31L + bounds.width
                entry = entry * 31L + bounds.height
            }
            hash = hash * 31L + entry
        }
        return hash
    }

    private fun clear() {
        buckets.clear()
        hasRecord = false
        cachedWidth = -1
        cachedHeight = -1
    }

    // Capture hooks, invoked by GuiRenderStateCaptureMixin for every element submission on
    // the render thread while a cacheable entry renders. Each stores a re-submission runnable
    // closing over the (persistent) render state and the immutable element.

    @JvmStatic
    fun captureText(element: GuiTextRenderState) {
        if (!capturing) {
            return
        }
        val target = captureTarget!!
        currentBucket.add { target.addText(element) }
    }

    @JvmStatic
    fun captureItem(element: GuiItemRenderState) {
        if (!capturing) {
            return
        }
        val target = captureTarget!!
        currentBucket.add { target.addItem(element) }
    }

    @JvmStatic
    fun captureGuiElement(element: GuiElementRenderState) {
        if (!capturing) {
            return
        }
        val target = captureTarget!!
        currentBucket.add { target.addGuiElement(element) }
    }

    @JvmStatic
    fun capturePicturesInPicture(element: PictureInPictureRenderState) {
        if (!capturing) {
            return
        }
        val target = captureTarget!!
        currentBucket.add { target.addPicturesInPictureState(element) }
    }
}
