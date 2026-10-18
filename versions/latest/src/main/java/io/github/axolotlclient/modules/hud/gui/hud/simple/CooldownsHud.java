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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.util.ClientColors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Item cooldown bars (Alpine "cooldowns" module, parity spec M2): every item on the
 * hotbar with an active cooldown (ender pearls, chorus fruit, shields, modded items)
 * renders as a name line plus a horizontal progress bar draining to zero. Only the
 * hotbar is scanned — that's what the player can actually act on — using the public
 * ItemCooldowns API (isOnCooldown / getCooldownPercent), no accessor widening.
 *
 * The entry auto-sizes its box to the active list (PotionsHud pattern): with no
 * cooldowns running the module draws nothing, so it costs a hotbar scan per frame
 * and no render when idle.
 */
public class CooldownsHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "cooldownshud");
	private static final String PLACEHOLDER = "Cooldowns";

	public final BooleanOption showItemName = new BooleanOption("showitemname", true);
	public final BooleanOption showSeconds = new BooleanOption("showseconds", true);
	public final ColorOption barColor = new ColorOption("barcolor", new Color(206, 164, 100).immutable());
	/** Bar thickness in pixels; the line height is this + 2. */
	public final IntegerOption barThickness = new IntegerOption("barthickness", 2, 1, 6);

	private static final int LINE_SPACING = 1;
	private final Minecraft client = (Minecraft) super.client;

	public CooldownsHud() {
		super(80, 13, true);
		background = new BooleanOption("background", false);
	}

	private record ActiveCooldown(String name, float percent, float remainingSeconds) {}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		List<ActiveCooldown> active = scanCooldowns(delta);
		if (active.isEmpty()) {
			return;
		}

		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		int width = getContentWidth();
		int thickness = barThickness.get();

		for (ActiveCooldown cd : active) {
			int lineY = y;
			if (showItemName.get()) {
				String label = showSeconds.get() && cd.remainingSeconds() >= 0.05f
					? cd.name() + " (" + String.format("%.1f", cd.remainingSeconds()) + "s)"
					: cd.name();
				graphics.br$drawString(label, x, lineY, textColor.get().toInt(), shadow.get());
				lineY += client.font.lineHeight + 1;
			}
			// Background track (subtle) then the filled portion draining with the cooldown.
			int trackColor = new Color(0, 0, 0, 90).toInt();
			graphics.br$fillRect(x, lineY, width, thickness, trackColor);
			int filled = Math.round(width * cd.percent());
			if (filled > 0) {
				graphics.br$fillRect(x, lineY, Math.min(filled, width), thickness, barColor.get().toInt());
			}
			y = lineY + thickness + LINE_SPACING;
		}
	}

	/** Hotbar slots 0-8; items on cooldown become render rows. Cheap: 9 checks/frame. */
	private List<ActiveCooldown> scanCooldowns(float delta) {
		var player = client.player;
		if (player == null) {
			return List.of();
		}
		var cooldowns = player.getCooldowns();
		List<ActiveCooldown> out = new ArrayList<>();
		int lineH = client.font.lineHeight + 1 + barThickness.get() + LINE_SPACING;
		int height = 0;
		int width = 0;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || !cooldowns.isOnCooldown(stack)) {
				continue;
			}
			float percent = cooldowns.getCooldownPercent(stack, delta);
			// getCooldownPercent returns 1->0 as the cooldown runs OUT; remaining seconds
			// derive from the percent only as a display estimate (duration is not public).
			String name = stack.getHoverName().getString();
			int rowWidth = showItemName.get()
				? client.font.width(name) + (showSeconds.get() ? 34 : 0)
				: getContentWidth();
			width = Math.max(width, rowWidth);
			height += lineH;
			out.add(new ActiveCooldown(name, percent, -1));
		}
		if (out.isEmpty()) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
		} else if (width != getContentWidth() || height != getContentHeight()) {
			setContentWidth(width);
			setContentHeight(height);
			onBoundsUpdate();
		}
		return out;
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		if (client.player == null) {
			return;
		}
		super.render(context, delta);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		graphics.br$drawString(PLACEHOLDER, x, y, -1, false);
		int lineY = y + client.font.lineHeight + 1;
		graphics.br$fillRect(x, lineY, getContentWidth(), barThickness.get(), ClientColors.SELECTOR_BLUE.toInt());
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(showItemName);
		options.add(showSeconds);
		options.add(barColor);
		options.add(barThickness);
		return options;
	}

}
