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

package io.github.axolotlclient.modules.hypixel.autoboop;

import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class FilterListConfigurationScreen extends Screen {
	final List<String> filters;
	public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final FiltersList filtersList;
	private final Screen parent;

	public FilterListConfigurationScreen(List<String> filters, Screen parent) {
		super(Component.translatable("autoboop.filters.configure"));
		this.filters = filters;
		this.parent = parent;
		this.filtersList = new FiltersList(this);
	}

	@Override
	protected void init() {
		layout.addTitleHeader(getTitle(), font);
		layout.addToContents(filtersList);
		Button resetButton = Button.builder(Component.translatable("autoboop.filters.clear"), button -> {
			filters.clear();
			filtersList.reload();
			AxolotlClientCommon.getInstance().saveConfig();
		}).build();
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout.addChild(resetButton);
		linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		this.filtersList.updateSize(this.width, this.layout);
		filtersList.reload();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
		filtersList.apply();
		AxolotlClientCommon.getInstance().saveConfig();
	}
}
