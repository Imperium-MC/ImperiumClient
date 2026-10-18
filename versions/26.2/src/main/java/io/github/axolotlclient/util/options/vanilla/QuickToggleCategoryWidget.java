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

package io.github.axolotlclient.util.options.vanilla;

import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.OptionCategoryImpl;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.BooleanWidget;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.CategoryWidget;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@SuppressWarnings("unused")
public class QuickToggleCategoryWidget extends CategoryWidget {
	private BooleanWidget enabledButton;

	public QuickToggleCategoryWidget(int x, int y, int width, int height, OptionCategoryImpl category) {
		super(x, y, width, height, category);
		category.getOptions().stream()
			.filter(o -> o instanceof BooleanOption)
			.map(o -> (BooleanOption) o)
			.filter(o -> "enabled".equals(o.getName())).findFirst()
			.ifPresent(booleanOption -> {
				enabledButton = new BooleanWidget(x + (width - 33), y + 3, 30, height - 5, booleanOption);
				enabledButton.active = !(booleanOption instanceof ForceableBooleanOption o && o.isForceOff());
			});
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {

		if (enabledButton != null && enabledButton.isMouseOver(mouseX, mouseY)) {
			this.isHovered = false;
			return true;
		}
		return super.isMouseOver(mouseX, mouseY);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractContents(graphics, mouseX, mouseY, delta);

		if (enabledButton != null) {
			enabledButton.setY(getY() + 2);
			enabledButton.update();
			enabledButton.extractRenderState(graphics, mouseX, mouseY, delta);
		}
	}

	@Override
	protected void extractScrollingStringOverContents(ActiveTextCollector activeTextCollector, Component component, int i) {
		int j = this.getX() + i;
		int k = this.getX() + this.getWidth() - i;
		if (enabledButton != null) {
			k -= enabledButton.getWidth() + 4;
		}
		activeTextCollector.acceptScrollingWithDefaultCenter(component, j, k, this.getY(), this.getY() + this.getHeight());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {

		if (enabledButton != null &&
			enabledButton.isHoveredOrFocused()) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			enabledButton.onPress(event);
			return true;
		}
		return this.isHovered && super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.active || !this.visible) {
			return false;
		} else if (!event.isSelection()) {
			return false;
		} else {
			this.playDownSound(Minecraft.getInstance().getSoundManager());
			onPress(event);
			return true;
		}
	}
}
