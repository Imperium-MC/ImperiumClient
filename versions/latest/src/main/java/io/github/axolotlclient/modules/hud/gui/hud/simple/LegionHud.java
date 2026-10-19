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
import io.github.axolotlclient.imperium.LegionStateRegistry;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Legion status HUD (Alpine "factions status HUD" parity, reimplemented for ImperiumMC
 * Legions): one line with your legion name, online/total members and power, fed from
 * {@link LegionStateRegistry}. Until the future {@code imperiumclient:legion} channel
 * populates the registry the entry auto-sizes to nothing and draws no pixels
 * (CooldownsHud/BoostsHud shell pattern), so the module is invisible in the default setup.
 */
public class LegionHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "legionhud");
	private static final String PLACEHOLDER = "Legion";

	/** Show "online/total" after the legion name. */
	public final BooleanOption showMembers = new BooleanOption("legionhud.show_members", true);
	/** Show the war-score power after the name. */
	public final BooleanOption showPower = new BooleanOption("legionhud.show_power", true);

	private final Minecraft client = (Minecraft) super.client;

	public LegionHud() {
		super(70, 13, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		LegionStateRegistry.LegionState legion = LegionStateRegistry.get();
		if (legion == null) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
			return;
		}

		String label = buildLabel(legion);
		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		int width = client.font.width(label);
		graphics.br$drawString(label, x, y, textColor.get().toInt(), shadow.get());
		if (width != getContentWidth() || client.font.lineHeight != getContentHeight()) {
			setContentWidth(width);
			setContentHeight(client.font.lineHeight);
			onBoundsUpdate();
		}
	}

	/** "[Spartans] 3/12 · Power 4.2k" — each part toggleable, separators dropped when empty. */
	private String buildLabel(LegionStateRegistry.LegionState legion) {
		StringBuilder label = new StringBuilder("[").append(legion.name()).append("]");
		if (showMembers.get()) {
			label.append(" ").append(legion.onlineMembers()).append("/").append(legion.totalMembers());
		}
		if (showPower.get()) {
			label.append(label.indexOf(" ") > 0 ? " \u00B7 " : " ")
				.append("Power ").append(formatPower(legion.power()));
		}
		return label.toString();
	}

	/** Compact power formatting: 950 below 1k, 4.2k above, 1.2m above a million. */
	private static String formatPower(long power) {
		if (power < 1000) {
			return Long.toString(power);
		}
		if (power < 1_000_000) {
			return String.format("%.1fk", power / 1000.0).replace(',', '.');
		}
		return String.format("%.1fm", power / 1_000_000.0).replace(',', '.');
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		if (LegionStateRegistry.get() == null) {
			return; // hidden while legion-less, even in a fixed-size layout
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

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(showMembers);
		options.add(showPower);
		return options;
	}
}
