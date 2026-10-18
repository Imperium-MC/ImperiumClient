/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.auth.skin;

import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LoadingScreen extends Screen {
	private final Component description;

	public LoadingScreen(Component title, Component description) {
		super(title);
		this.description = description;
	}

	@Override
	protected void init() {
		int headerHeight = 33;
		StringWidget titleWidget = new StringWidget(width / 2 - font.width(getTitle()) / 2, headerHeight / 2 - font.lineHeight / 2, font.width(getTitle()), font.lineHeight, getTitle(), getFont());
		addRenderableWidget(titleWidget);

		var loadingPlaceholder = new LoadingDotsWidget(getFont(), description);
		loadingPlaceholder.setPosition(width/2 - loadingPlaceholder.getWidth()/2, height/2 - loadingPlaceholder.getHeight()/2);
		addRenderableWidget(loadingPlaceholder);
	}
}
