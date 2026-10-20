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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla;

import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoText;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/**
 * Movable title + subtitle (Alpine "titles" module, parity spec M2): the vanilla
 * title screen-text (the big centered announcement and its smaller second line)
 * rendered at a freely positionable HUD anchor instead of the fixed center.
 *
 * Vanilla still owns the STATE — InGameHudMixin forwards setTitle/setSubtitle/
 * setTimes/clearTitles calls here and cancels the vanilla extractTitle draw when
 * this entry is enabled — so fade timing, packet behaviour, and /title all work
 * unchanged. Alpha is derived from wall-clock since the last set (ticks*50ms),
 * matching the vanilla fadeIn/stay/fadeOut shape closely enough that a title
 * fades in, holds, and fades out exactly as without this module.
 *
 * The actual Component is drawn (as AxoText, never getString()), so per-section
 * colours from the title itself survive — the flat color only tints unstyled
 * sections, exactly like vanilla's own ARGB.white(alpha) title draw. Text wider
 * than the entry wraps to further centered lines (Font.split), and the entry
 * height grows to fit the wrapped lines instead of clipping them.
 */
public class TitleHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "titlehud");
	private static final String PLACEHOLDER_TITLE = "Title";
	private static final String PLACEHOLDER_SUBTITLE = "Subtitle";

	/** Scale of the title text (vanilla draws it ~2x). Subtitle renders at half this. */
	public final IntegerOption titleScale = new IntegerOption("titlescale", 2, val -> updateHeight(), 1, 4);
	public final BooleanOption customTextColor = new BooleanOption("customtextcolor", false);
	public final BooleanOption renderBackgroundOption = new BooleanOption("background", false);

	private final Minecraft client = (Minecraft) super.client;

	// Live title state, owned by vanilla via InGameHudMixin forwards.
	private volatile Component title = Component.empty();
	private volatile Component subtitle = Component.empty();
	private volatile int fadeInTicks = 10;
	private volatile int stayTicks = 70;
	private volatile int fadeOutTicks = 20;
	private volatile long setAtMs = 0;
	private volatile boolean cleared = true;

	/** Widest line actually drawn by the last render, entry units (scale included). Test seam. */
	private volatile int lastRenderedMaxLineWidth = -1;

	public TitleHud() {
		// Wide enough that a typical title line fits at scale 2 (wrapping takes over beyond
		// that) + a little taller than two text lines: the editor box wraps both lines.
		super(200, 31, false);
	}

	// ── state (called from InGameHudMixin on the netty/render thread vanilla uses) ─────

	public void setTitle(Component title) {
		this.title = title == null ? Component.empty() : title;
		this.setAtMs = System.currentTimeMillis();
		this.cleared = false;
		updateHeight();
	}

	public void setSubtitle(Component subtitle) {
		this.subtitle = subtitle == null ? Component.empty() : subtitle;
		updateHeight();
	}

	public void setTimes(int fadeIn, int stay, int fadeOut) {
		this.fadeInTicks = Math.max(0, fadeIn);
		this.stayTicks = Math.max(0, stay);
		this.fadeOutTicks = Math.max(0, fadeOut);
	}

	public void clearTitles() {
		this.cleared = true;
		updateHeight();
	}

	// ── rendering ───────────────────────────────────────────────────────────────────────

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		if (cleared) {
			return;
		}
		float alpha = currentAlpha();
		if (alpha <= 0.0f) {
			return;
		}

		int baseX = (int) ((float) getContentPos().x() + Math.round((float) getContentWidth() / 2));
		int y = (int) (float) getContentPos().y();

		Component titleText = this.title;
		Component subtitleText = this.subtitle;
		boolean hasTitle = !titleText.getString().isEmpty();
		boolean hasSubtitle = !subtitleText.getString().isEmpty();
		int color = colorWithAlpha(alpha);

		// Scaled draws go through the context matrix stack: scale, draw centered at the
		// anchor, restore. Each line wraps at contentWidth/scale so the scaled result stays
		// inside the entry; the subtitle renders at half the title's scale below it.
		int maxLineWidth = -1;
		if (hasTitle) {
			int scale = titleScale.get();
			List<FormattedCharSequence> lines = wrappedLines(titleText, scale);
			maxLineWidth = drawWrapped(graphics, titleText, baseX, y, scale, lines, color);
			y += client.font.lineHeight * scale * lines.size() + 2;
		}
		if (hasSubtitle) {
			int scale = Math.max(1, titleScale.get() / 2);
			List<FormattedCharSequence> lines = wrappedLines(subtitleText, scale);
			maxLineWidth = Math.max(maxLineWidth,
				drawWrapped(graphics, subtitleText, baseX, y, scale, lines, color));
		}
		lastRenderedMaxLineWidth = maxLineWidth;
	}

	/**
	 * Draws the component word-wrapped to the entry width at the given scale (inside its own
	 * scaled, centered matrix push) and returns the widest line in entry units (scale included).
	 */
	private int drawWrapped(AxoRenderContext graphics, Component text, int baseX, int y, int scale,
		List<FormattedCharSequence> lines, int color) {
		// Measurement uses the same Font.split the renderer's word-wrap path uses, so the
		// widest line reported here is the widest line actually drawn.
		int widest = 0;
		for (FormattedCharSequence line : lines) {
			widest = Math.max(widest, client.font.width(line));
		}
		graphics.br$pushMatrix();
		graphics.br$translateMatrix(baseX, y);
		graphics.br$scaleMatrix(scale, scale);
		graphics.br$drawCenteredWordWrap((AxoText) text, 0, 0, wrapWidth(scale), shadow.get(), color);
		graphics.br$popMatrix();
		return widest * scale;
	}

	/** The component split to the entry width at the given scale (same split the draw uses). */
	private List<FormattedCharSequence> wrappedLines(Component text, int scale) {
		int wrapWidth = wrapWidth(scale);
		if (wrapWidth <= 0 || client.font == null) {
			return List.of();
		}
		return client.font.split(text, wrapWidth);
	}

	/** Width available for one unscaled line: the entry content shrunk by the text scale. */
	private int wrapWidth(int scale) {
		int width = getContentWidth() > 0 ? getContentWidth() : getWidth();
		return Math.max(1, width / Math.max(1, scale));
	}

	/** Grows/shrinks the entry height to fit the wrapped title + subtitle lines. */
	private void updateHeight() {
		if (client == null || client.font == null) {
			return;
		}
		Component titleText = this.title;
		Component subtitleText = this.subtitle;
		boolean hasTitle = !cleared && !titleText.getString().isEmpty();
		boolean hasSubtitle = !cleared && !subtitleText.getString().isEmpty();
		int height = 0;
		if (hasTitle) {
			int scale = titleScale.get();
			height += client.font.lineHeight * scale * wrappedLines(titleText, scale).size();
		}
		if (hasTitle && hasSubtitle) {
			height += 2;
		}
		if (hasSubtitle) {
			int scale = Math.max(1, titleScale.get() / 2);
			height += client.font.lineHeight * scale * wrappedLines(subtitleText, scale).size();
		}
		if (height == 0) {
			height = getInitialContentHeight();
		}
		if (height != getHeight()) {
			setHeight(height);
			onBoundsUpdate();
		}
	}

	/** Widest line drawn by the last render, entry units (scale included); -1 when nothing was drawn. */
	public int lastRenderedMaxLineWidth() {
		return lastRenderedMaxLineWidth;
	}

	private float currentAlpha() {
		long elapsed = System.currentTimeMillis() - setAtMs;
		long fadeIn = fadeInTicks * 50L;
		long stay = stayTicks * 50L;
		long fadeOut = fadeOutTicks * 50L;
		if (elapsed < fadeIn && fadeIn > 0) {
			return elapsed / (float) fadeIn;
		}
		if (elapsed < fadeIn + stay) {
			return 1.0f;
		}
		if (elapsed < fadeIn + stay + fadeOut && fadeOut > 0) {
			return 1.0f - (elapsed - fadeIn - stay) / (float) fadeOut;
		}
		return 0.0f;
	}

	private int colorWithAlpha(float alpha) {
		if (customTextColor.get()) {
			Color c = textColor.get();
			return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)).toInt();
		}
		return ARGB.color((int) (alpha * 255), 255, 255, 255);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		int baseX = (int) ((float) getContentPos().x() + Math.round((float) getContentWidth() / 2));
		int y = (int) (float) getContentPos().y();
		graphics.br$drawString(PLACEHOLDER_TITLE, baseX - client.font.width(PLACEHOLDER_TITLE) / 2, y, -1, false);
		graphics.br$drawString(PLACEHOLDER_SUBTITLE, baseX - client.font.width(PLACEHOLDER_SUBTITLE) / 2,
			y + client.font.lineHeight + 2, new Color(160, 160, 160, 160).toInt(), false);
	}

	@Override
	public void postConfigLoad() {
		super.postConfigLoad();
		// The saved scale (or a leftover tall height from a previous long title) may differ
		// from the constructor default; re-derive the height for the editor placeholder box.
		updateHeight();
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(titleScale);
		options.add(customTextColor);
		options.add(renderBackgroundOption);
		return options;
	}
}
