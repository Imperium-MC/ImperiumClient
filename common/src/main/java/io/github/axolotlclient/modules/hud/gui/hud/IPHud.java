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

package io.github.axolotlclient.modules.hud.gui.hud;

import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.PlatformDispatch;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.render.AxoSprite;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.util.DrawPosition;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
public class IPHud extends TextHudEntry {

	public static final AxoIdentifier ID = AxoIdentifier.of("kronhud", "iphud");
	private final BooleanOption showIcon = new BooleanOption("iphud.show_icon", false);
	private AxoSprite.Dynamic sprite;
	private final IntegerOption height = new IntegerOption("hud.height", 13, 9, 64);

	public IPHud() {
		super(115, 13, true);

		Events.DISCONNECT.register(() -> {
			if (sprite != null) {
				sprite.close();
				sprite = null;
			}
		});

		Events.CONNECTION_PLAY_READY.register(info -> {
			if (showIcon.get()) {
				sprite = PlatformDispatch.ipHud$getServerIcon();
			}
		});
	}

	/**
	 * The address only changes when connecting to a different server, so this entry is safe
	 * to serve from the {@code hud_frame_limit} recording despite being a text HUD.
	 */
	@Override
	public boolean dynamicContent() {
		return false;
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}

	public String getValue() {
		if (client.br$isLocalServer() || client.br$getServerAddress() == null) {
			return "Singleplayer";
		}

		return client.br$getServerAddress();
	}

	private void updateSize(AxoRenderContext graphics) {
		int w = getContentWidth();
		int h = getContentHeight();
		int hNew = height.get();
		boolean updated = false;
		if (h != hNew) {
			setContentHeight(hNew);
			updated = true;
		}
		int req = graphics.br$getFont().br$getWidth(getValue()) + 4;
		if (showIcon.get() && sprite != null) {
			req += getContentHeight() + 1;
		}
		if (w != req) {
			setContentWidth(req);
			updated = true;
		}
		if (updated) {
			onBoundsUpdate();
		}
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		var options = super.getConfigurationOptions();
		options.add(showIcon);
		options.add(height);
		return options;
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		updateSize(graphics);
		DrawPosition pos = getContentPos();
		int textX = pos.x() + getContentWidth() / 2;

		if (showIcon.get() && sprite != null) {
			int imageSize = getContentHeight() - 2;
			textX += imageSize / 2;
			graphics.br$drawTexture(sprite, pos.x() + 1, pos.y() + 1, imageSize, imageSize);
		}

		graphics.br$drawCenteredString(getValue(), textX, pos.y() + getContentHeight() / 2 - client.br$getFont().br$getFontHeight() / 2, textColor.get().toInt(), true);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		renderComponent(graphics, delta);
	}
}
