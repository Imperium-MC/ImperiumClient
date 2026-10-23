/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.imperium.media.MediaStatus
import io.github.axolotlclient.imperium.media.MediaStatusRegistry
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Now-playing HUD (Alpine "spotify_state" parity, W5-B). Draws "track — artist" over a bronze
 * progress bar, reading whatever the active media provider published to
 * [MediaStatusRegistry] — today the optional Spotify desktop local poller. With nothing
 * playing (or the provider off/erroring) the entry auto-sizes to nothing and draws no pixels,
 * so the shell is invisible until a provider speaks (BoostsHud pattern).
 */
class NowPlayingHud : TextHudEntry(80, 11, true) {

    /** Draw the bronze progress bar under the title line. */
    @JvmField
    val showProgressBar = BooleanOption("show_progress_bar", true)

    // Alpine parity (spotify_state): display_timestamp adds the "m:ss / m:ss" readout next
    // to the bar; scrolling_track_name marquee-scrolls a title that outgrew the bar width
    // at scrolling_speed pixels per second.
    @JvmField
    val displayTimestamp = BooleanOption("display_timestamp", false)

    @JvmField
    val scrollingTrackName = BooleanOption("scrolling_track_name", false)

    @JvmField
    val scrollingSpeed = IntegerOption("scrolling_speed", 20, 1, 100)

    private val mc: Minecraft = super.client as Minecraft
    private var scrollStartMs: Long = 0
    private var lastTitle: String? = null

    init {
        background = BooleanOption("background", false)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val status = MediaStatusRegistry.current()
        if (status == null) {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
            return
        }

        val x = contentPos.x().toInt()
        var y = contentPos.y().toInt()
        val now = System.currentTimeMillis()
        val title = status.titleLine()
        val textWidth = mc.font.width(title)
        var width = textWidth

        var height = mc.font.lineHeight
        if (showProgressBar.get() && status.lengthMs() > 0) {
            val barWidth = max(40, textWidth)
            drawTitle(graphics, title, x, y, barWidth, now)
            val barY = y + mc.font.lineHeight + BAR_GAP
            if (displayTimestamp.get()) {
                val timestamps = formatTime(status.progressMs(now)) + " / " + formatTime(status.lengthMs())
                val stampWidth = mc.font.width(timestamps) + 4
                val stampX = x + barWidth + 2
                graphics.`br$drawString`(timestamps, stampX, barY - 1, timerColor(), shadow.get())
                width = max(width, stampX + stampWidth)
            }
            drawProgressBar(graphics, x, barY, barWidth, status, now)
            width = max(width, barWidth)
            height = mc.font.lineHeight + BAR_GAP + BAR_HEIGHT
            if (displayTimestamp.get()) {
                // the timestamp line reaches lower than the bar itself
                height = max(height, 2 * mc.font.lineHeight + 1)
            }
        } else {
            graphics.`br$drawString`(title, x, y, textColor.get().toInt(), shadow.get())
        }
        if (width != getContentWidth() || height != getContentHeight()) {
            setContentWidth(width)
            setContentHeight(height)
            onBoundsUpdate()
        }
    }

    private fun timerColor(): Int = 0xFFA8823F.toInt()

    /**
     * The title line: scrolling_track_name marquees a title that is wider than the bar —
     * clipped to the bar width, wrapping around after a blank gap, advancing at
     * scrolling_speed pixels per second. A fitting title draws plain.
     */
    private fun drawTitle(graphics: AxoRenderContext, title: String, x: Int, y: Int, barWidth: Int, nowMs: Long) {
        val textWidth = mc.font.width(title)
        if (!scrollingTrackName.get() || textWidth <= barWidth) {
            graphics.`br$drawString`(title, x, y, textColor.get().toInt(), shadow.get())
            return
        }
        if (title != lastTitle) {
            lastTitle = title
            scrollStartMs = nowMs
        }
        val stripWidth = textWidth + SCROLL_GAP_PX
        val offset = (((nowMs - scrollStartMs) * scrollingSpeed.get() / 1000L) % stripWidth).toInt()
        graphics.`br$pushScissor`(x, y - 1, barWidth, mc.font.lineHeight + 2)
        graphics.`br$drawString`(title, x - offset, y, textColor.get().toInt(), shadow.get())
        graphics.`br$drawString`(title, x - offset + stripWidth, y, textColor.get().toInt(), shadow.get())
        graphics.`br$popScissor`()
    }

    /**
     * The carved progress bar: a 1px-recessed dark track with a bronze fill; pixel-grid like
     * every other carved element (FarmingOverlay bar conventions, bronze instead of state colour).
     */
    private fun drawProgressBar(graphics: AxoRenderContext, x: Int, y: Int, width: Int, status: MediaStatus, now: Long) {
        val fraction = status.fraction(now)
        graphics.`br$fillRect`(x, y, width, BAR_HEIGHT, BAR_EDGE)
        graphics.`br$fillRect`(x + 1, y + 1, width - 2, BAR_HEIGHT - 2, BAR_TRACK)
        val fill = ((width - 2) * fraction).roundToInt()
        if (fill > 0) {
            graphics.`br$fillRect`(
                x + 1, y + 1, fill, BAR_HEIGHT - 2, if (status.playing()) BAR_FILL else BAR_FILL_PAUSED
            )
        }
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (MediaStatusRegistry.current() == null) {
            return // hidden when idle, even in a fixed-size layout
        }
        super.render(context, delta)
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val x = contentPos.x().toInt()
        val y = contentPos.y().toInt()
        graphics.`br$drawString`(PLACEHOLDER, x, y, -1, false)
    }

    override fun getId(): Identifier = ID

    /**
     * Anchors bottom-left when the entry is dynamically positioned — a now-playing bar reads
     * as music-player chrome down there rather than another top-left status line.
     */
    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.BOTTOM_LEFT

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(showProgressBar)
        options.add(displayTimestamp)
        options.add(scrollingTrackName)
        options.add(scrollingSpeed)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "nowplayinghud")
        private const val PLACEHOLDER = "Now Playing"
        private const val BAR_HEIGHT = 3
        private const val BAR_GAP = 2

        // carved ramp (read-only mirror of CarvedTheme's, which lives in the menu package)
        private const val BAR_TRACK = 0xFF151210.toInt()
        private const val BAR_FILL = 0xFFA8823F.toInt()
        private const val BAR_FILL_PAUSED = 0xFF5C4B2F.toInt()
        private const val BAR_EDGE = 0xFF0B0908.toInt()

        /** Blank gap between marquee wraps, in pixels. */
        private const val SCROLL_GAP_PX = 20

        private fun formatTime(ms: Long): String {
            val totalSeconds = Math.floorDiv(ms, 1000L)
            return (totalSeconds / 60).toString() + ":" + String.format("%02d", totalSeconds % 60)
        }
    }
}
