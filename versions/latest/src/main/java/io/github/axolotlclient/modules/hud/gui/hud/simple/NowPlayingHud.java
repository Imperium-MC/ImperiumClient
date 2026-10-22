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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.imperium.media.MediaStatus;
import io.github.axolotlclient.imperium.media.MediaStatusRegistry;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Now-playing HUD (Alpine "spotify_state" parity, W5-B). Draws "track — artist" over a bronze
 * progress bar, reading whatever the active media provider published to
 * {@link MediaStatusRegistry} — today the optional Spotify desktop local poller. With nothing
 * playing (or the provider off/erroring) the entry auto-sizes to nothing and draws no pixels,
 * so the shell is invisible until a provider speaks (BoostsHud pattern).
 */
public class NowPlayingHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "nowplayinghud");
	private static final String PLACEHOLDER = "Now Playing";

	private static final int BAR_HEIGHT = 3;
	private static final int BAR_GAP = 2;
	// carved ramp (read-only mirror of CarvedTheme's, which lives in the menu package)
	private static final int BAR_TRACK = 0xFF151210;
	private static final int BAR_FILL = 0xFFA8823F;
	private static final int BAR_FILL_PAUSED = 0xFF5C4B2F;
	private static final int BAR_EDGE = 0xFF0B0908;

	/** Draw the bronze progress bar under the title line. */
	public final BooleanOption showProgressBar = new BooleanOption("show_progress_bar", true);
	// Alpine parity (spotify_state): display_timestamp adds the "m:ss / m:ss" readout next
	// to the bar; scrolling_track_name marquee-scrolls a title that outgrew the bar width
	// at scrolling_speed pixels per second.
	public final BooleanOption displayTimestamp = new BooleanOption("display_timestamp", false);
	public final BooleanOption scrollingTrackName = new BooleanOption("scrolling_track_name", false);
	public final IntegerOption scrollingSpeed = new IntegerOption("scrolling_speed", 20, 1, 100);

	private final Minecraft client = (Minecraft) super.client;
	private long scrollStartMs;
	private String lastTitle;

	/** Blank gap between marquee wraps, in pixels. */
	private static final int SCROLL_GAP_PX = 20;

	public NowPlayingHud() {
		super(80, 11, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		MediaStatus status = MediaStatusRegistry.current();
		if (status == null) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
			return;
		}

		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		long now = System.currentTimeMillis();
		String title = status.titleLine();
		int textWidth = client.font.width(title);
		int width = textWidth;

		int height = client.font.lineHeight;
		if (showProgressBar.get() && status.lengthMs() > 0) {
			int barWidth = Math.max(40, textWidth);
			drawTitle(graphics, title, x, y, barWidth, now);
			int barY = y + client.font.lineHeight + BAR_GAP;
			if (displayTimestamp.get()) {
				String timestamps = formatTime(status.progressMs(now)) + " / " + formatTime(status.lengthMs());
				int stampWidth = client.font.width(timestamps) + 4;
				int stampX = x + barWidth + 2;
				graphics.br$drawString(timestamps, stampX, barY - 1, timerColor(), shadow.get());
				width = Math.max(width, stampX + stampWidth);
			}
			drawProgressBar(graphics, x, barY, barWidth, status, now);
			width = Math.max(width, barWidth);
			height = client.font.lineHeight + BAR_GAP + BAR_HEIGHT;
			if (displayTimestamp.get()) {
				// the timestamp line reaches lower than the bar itself
				height = Math.max(height, 2 * client.font.lineHeight + 1);
			}
		} else {
			graphics.br$drawString(title, x, y, textColor.get().toInt(), shadow.get());
		}
		if (width != getContentWidth() || height != getContentHeight()) {
			setContentWidth(width);
			setContentHeight(height);
			onBoundsUpdate();
		}
	}

	private int timerColor() {
		return 0xFFA8823F;
	}

	private static String formatTime(long ms) {
		long totalSeconds = Math.floorDiv(ms, 1000L);
		return totalSeconds / 60 + ":" + String.format("%02d", totalSeconds % 60);
	}

	/**
	 * The title line: scrolling_track_name marquees a title that is wider than the bar —
	 * clipped to the bar width, wrapping around after a blank gap, advancing at
	 * scrolling_speed pixels per second. A fitting title draws plain.
	 */
	private void drawTitle(AxoRenderContext graphics, String title, int x, int y, int barWidth, long nowMs) {
		int textWidth = client.font.width(title);
		if (!scrollingTrackName.get() || textWidth <= barWidth) {
			graphics.br$drawString(title, x, y, textColor.get().toInt(), shadow.get());
			return;
		}
		if (!title.equals(lastTitle)) {
			lastTitle = title;
			scrollStartMs = nowMs;
		}
		int stripWidth = textWidth + SCROLL_GAP_PX;
		int offset = (int) ((nowMs - scrollStartMs) * scrollingSpeed.get() / 1000L) % stripWidth;
		graphics.br$pushScissor(x, y - 1, barWidth, client.font.lineHeight + 2);
		graphics.br$drawString(title, x - offset, y, textColor.get().toInt(), shadow.get());
		graphics.br$drawString(title, x - offset + stripWidth, y, textColor.get().toInt(), shadow.get());
		graphics.br$popScissor();
	}

	/**
	 * The carved progress bar: a 1px-recessed dark track with a bronze fill; pixel-grid like
	 * every other carved element (FarmingOverlay bar conventions, bronze instead of state colour).
	 */
	private void drawProgressBar(AxoRenderContext graphics, int x, int y, int width, MediaStatus status, long now) {
		float fraction = status.fraction(now);
		graphics.br$fillRect(x, y, width, BAR_HEIGHT, BAR_EDGE);
		graphics.br$fillRect(x + 1, y + 1, width - 2, BAR_HEIGHT - 2, BAR_TRACK);
		int fill = Math.round((width - 2) * fraction);
		if (fill > 0) {
			graphics.br$fillRect(x + 1, y + 1, fill, BAR_HEIGHT - 2, status.playing() ? BAR_FILL : BAR_FILL_PAUSED);
		}
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		if (MediaStatusRegistry.current() == null) {
			return; // hidden when idle, even in a fixed-size layout
		}
		super.render(context, delta);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		graphics.br$drawString(PLACEHOLDER, x, y, -1, false);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	/**
	 * Anchors bottom-left when the entry is dynamically positioned — a now-playing bar reads
	 * as music-player chrome down there rather than another top-left status line.
	 */
	@Override
	protected AnchorPoint getDefaultAnchor() {
		return AnchorPoint.BOTTOM_LEFT;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(showProgressBar);
		options.add(displayTimestamp);
		options.add(scrollingTrackName);
		options.add(scrollingSpeed);
		return options;
	}
}
