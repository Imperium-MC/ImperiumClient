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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import net.minecraft.resources.Identifier;

public class HotbarHud extends TextHudEntry {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "hotbarhud");

	public HotbarHud() {
		super(182, 22, false);
		supportsScaling = false;
	}

	@Override
	public void renderComponent(AxoRenderContext context, float delta) {
		// this is just a matrix translate in InGameHudMixin
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext context, float delta) {
		DrawPosition pos = getContentPos();

		context.br$drawCenteredString(getName(), pos.x() + width / 2,
			pos.y() + height / 2 - 4, -1);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public boolean overridesF3() {
		return true;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> list = new ArrayList<>();
		list.add(enabled);
		list.add(hide);
		list.add(anchor);
		return list;
	}

	@Override
	protected AnchorPoint getDefaultAnchor() {
		return AnchorPoint.BOTTOM_MIDDLE;
	}

	@Override
	public double getDefaultX() {
		return 0.5;
	}

	@Override
	public double getDefaultY() {
		return 0.96;
	}
}
