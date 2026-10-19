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

package io.github.axolotlclient.modules.chat;

import io.github.axolotlclient.AxolotlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The small camera button on the chat screen ({@code chat_screenshot_button}). The icon is
 * drawn vector-style with plain {@code fill} rectangles — a camera body with a viewfinder
 * hump and a lens ring — so no glyph/texture is involved and it stays crisp at any scale.
 *
 * <p>Not focus-navigable: the chat screen's input owns keyboard focus, and tabbing into a
 * one-shot button there would be noise. Clicking it grabs the chat screenshot through
 * {@link ChatScreenshot#capture(Minecraft)}.</p>
 */
public class ChatScreenshotButton extends AbstractWidget {

	/** Icon/backdrop colors (ARGB). */
	private static final int BACKDROP = 0x66000000;
	private static final int BACKDROP_HOVER = 0x99000000;
	private static final int ICON = 0xFFE0E0E0;
	private static final int ICON_HOVER = 0xFFFFFFFF;
	private static final int ICON_DARK = 0xFF303030;

	public ChatScreenshotButton(int x, int y, int size) {
		super(x, y, size, size, Component.translatable("chat_screenshot_button"));
		setTooltip(Tooltip.create(Component.translatable("chat_screenshot_button")));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		boolean hover = isHovered;
		int size = getWidth();
		int left = getX();
		int top = getY();

		// Backdrop plate.
		graphics.fill(left, top, left + size, top + size, hover ? BACKDROP_HOVER : BACKDROP);

		// Vector camera, sized relative to the button so a 12px and a 20px button both work:
		// the viewfinder hump, the body, and a two-tone lens.
		int unit = Math.max(1, size / 6);
		int iconColor = hover ? ICON_HOVER : ICON;
		int bodyLeft = left + unit;
		int bodyTop = top + 2 * unit;
		int bodyRight = left + size - unit;
		int bodyBottom = top + size - unit;

		// Body.
		graphics.fill(bodyLeft, bodyTop, bodyRight, bodyBottom, iconColor);
		// Viewfinder hump on top of the body.
		graphics.fill(left + 2 * unit, top + unit, left + size - 2 * unit, bodyTop, iconColor);
		// Lens: a dark ring with the body color left in its center.
		int lensLeft = left + size / 2 - unit;
		int lensRight = left + size / 2 + 2 * unit;
		int lensTop = top + size / 2 - unit;
		int lensBottom = top + size / 2 + 2 * unit;
		graphics.fill(lensLeft, lensTop, lensRight, lensBottom, ICON_DARK);
		graphics.fill(lensLeft + unit / 2 + unit % 2, lensTop + unit / 2 + unit % 2, lensRight - unit / 2 - unit % 2, lensBottom - unit / 2 - unit % 2, iconColor);
	}

	@Override
	public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		if (AxolotlClient.config().chatScreenshotButton.get()) {
			ChatScreenshot.capture(Minecraft.getInstance());
		}
		// the down-click sound is played by AbstractWidget.mouseClicked already
	}

	@Override
	public boolean isFocused() {
		return false; // the chat input must never lose focus to this button
	}

	@Nullable
	@Override
	public net.minecraft.client.gui.ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null; // not reachable by keyboard navigation
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
