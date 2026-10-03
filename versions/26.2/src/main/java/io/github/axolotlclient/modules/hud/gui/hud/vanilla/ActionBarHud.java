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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class ActionBarHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("kronhud", "actionbarhud");
	private static final String PLACEHOLDER = "Action Bar";

	public final IntegerOption timeShown = new IntegerOption("timeshown", 60, 40, 300);
	public final BooleanOption customTextColor = new BooleanOption("customtextcolor", false);
	private final Minecraft client = (Minecraft) super.client;

	public ActionBarHud() {
		super(115, 13, false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
	}

	public void render(GuiGraphicsExtractor graphics, Component actionBar, int color) {

		graphics.text(client.font, actionBar,
			(int) ((float) getContentPos().x() + Math.round((float) getContentWidth() / 2) -
				(float) client.font.width(actionBar) / 2), (int) ((float) getContentPos().y() + 3),
			customTextColor.get() ? (textColor.get().getAlpha() == 255 ? new Color(
				textColor.get().getRed(), textColor.get().getGreen(), textColor.get().getBlue(),
				ARGB.alpha(color)
			).toInt() : textColor.get().toInt()) : color, shadow.get()
		);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		graphics.br$drawString(PLACEHOLDER, (int) ((float) getContentPos().x() + Math.round((float) getContentWidth() / 2) -
				(float) client.br$getFont().br$getWidth(PLACEHOLDER) / 2),
			(int) ((float) getContentPos().y() + 3), -1, false
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(timeShown);
		options.add(customTextColor);
		return options;
	}
}
