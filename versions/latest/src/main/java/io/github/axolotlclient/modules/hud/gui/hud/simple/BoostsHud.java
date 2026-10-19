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
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.imperium.BoostRegistry;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Boosts HUD (Alpine "enchantedmc / boosts" parity) — server-specific. Lists the active
 * entries of {@link BoostRegistry} (name + remaining time), one line each; with no boosts
 * running the entry auto-sizes to nothing and draws no pixels (CooldownsHud pattern), so
 * the shell is invisible until the future ImperiumMC boost channel populates it.
 */
public class BoostsHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "boostshud");
	private static final String PLACEHOLDER = "Boosts";

	/** Show the remaining time after each boost name. */
	public final BooleanOption showRemaining = new BooleanOption("show_remaining", true);

	private final Minecraft client = (Minecraft) super.client;

	public BoostsHud() {
		super(80, 11, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		List<BoostRegistry.ActiveBoost> boosts = BoostRegistry.active();
		if (boosts.isEmpty()) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
			return;
		}

		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		int line_height = client.font.lineHeight + 1;
		int width = 0;
		for (BoostRegistry.ActiveBoost boost : boosts) {
			String label = showRemaining.get()
				? boost.name() + " " + formatRemaining(boost.endsAtEpochMs() - System.currentTimeMillis())
				: boost.name();
			graphics.br$drawString(label, x, y, textColor.get().toInt(), shadow.get());
			width = Math.max(width, client.font.width(label));
			y += line_height;
		}
		if (width != getContentWidth() || boosts.size() * line_height != getContentHeight()) {
			setContentWidth(width);
			setContentHeight(boosts.size() * line_height);
			onBoundsUpdate();
		}
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		if (BoostRegistry.active().isEmpty()) {
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

	private static String formatRemaining(long ms) {
		long seconds = Math.max(0, (ms + 999) / 1000);
		if (seconds < 60) {
			return seconds + "s";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + "m" + (seconds % 60) + "s";
		}
		return minutes / 60 + "h" + (minutes % 60) + "m";
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(showRemaining);
		return options;
	}
}
