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
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

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
 */
public class TitleHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "titlehud");
	private static final String PLACEHOLDER_TITLE = "Title";
	private static final String PLACEHOLDER_SUBTITLE = "Subtitle";

	/** Scale of the title text (vanilla draws it ~2x). Subtitle renders at half this. */
	public final IntegerOption titleScale = new IntegerOption("titlescale", 2, 1, 4);
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

	public TitleHud() {
		// Wide + a little taller than two text lines: the editor box wraps both lines.
		super(120, 31, false);
	}

	// ── state (called from InGameHudMixin on the netty/render thread vanilla uses) ─────

	public void setTitle(Component title) {
		this.title = title == null ? Component.empty() : title;
		this.setAtMs = System.currentTimeMillis();
		this.cleared = false;
	}

	public void setSubtitle(Component subtitle) {
		this.subtitle = subtitle == null ? Component.empty() : subtitle;
	}

	public void setTimes(int fadeIn, int stay, int fadeOut) {
		this.fadeInTicks = Math.max(0, fadeIn);
		this.stayTicks = Math.max(0, stay);
		this.fadeOutTicks = Math.max(0, fadeOut);
	}

	public void clearTitles() {
		this.cleared = true;
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

		// Scaled draws go through the context matrix stack: scale, draw centered at
		// the anchor, restore. Subtitle renders at half the title's scale below it.
		if (hasTitle) {
			int scale = titleScale.get();
			graphics.br$pushMatrix();
			graphics.br$translateMatrix(baseX, y);
			graphics.br$scaleMatrix(scale, scale);
			graphics.br$drawString(titleText.getString(),
				-client.font.width(titleText) / 2, 0, color, shadow.get());
			graphics.br$popMatrix();
			y += client.font.lineHeight * scale + 2;
		}
		if (hasSubtitle) {
			int scale = Math.max(1, titleScale.get() / 2);
			graphics.br$pushMatrix();
			graphics.br$translateMatrix(baseX, y);
			graphics.br$scaleMatrix(scale, scale);
			graphics.br$drawString(subtitleText.getString(),
				-client.font.width(subtitleText) / 2, 0, color, shadow.get());
			graphics.br$popMatrix();
		}
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
