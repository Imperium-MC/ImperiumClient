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

package io.github.axolotlclient.util.options.vanilla;

import java.util.Base64;

import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.screen.GraphicsEditorScreen;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.GraphicsWidget;
import io.github.axolotlclient.util.notifications.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AxoGraphicsWidget extends GraphicsWidget {
	private final GraphicsOption option;

	public AxoGraphicsWidget(int x, int y, int width, int height, GraphicsOption option) {
		super(x, y, width, height, option);
		this.option = option;
	}

	@Override
	public void onPress(@NotNull InputWithModifiers mods) {
		Minecraft.getInstance().gui.setScreen(new AxoGraphicsEditorScreen(Minecraft.getInstance().gui.screen(), this.option));
	}

	public static class AxoGraphicsEditorScreen extends GraphicsEditorScreen {

		public AxoGraphicsEditorScreen(Screen parent, GraphicsOption option) {
			super(parent, option);
		}

		@Override
		public void init() {
			super.init();

			int buttonX = gridX + maxGridWidth + 10;
			int buttonY = gridY + 60;
			var back = (Button) children().getLast();
			removeWidget(back);
			addRenderableWidget(Button.builder(Component.translatable("graphics.copy_text"),
					btn -> minecraft.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(option.get().getPixelData())))
				.pos(buttonX, buttonY + 25).width(100).build());
			addRenderableWidget(Button.builder(Component.translatable("graphics.paste_text"),
					btn -> {
						try {
							option.get().setPixelData(Base64.getDecoder().decode(minecraft.keyboardHandler.getClipboard()));
						} catch (IllegalArgumentException e) {
							Notifications.getInstance().addStatus("graphics.paste_text.failed", "graphics.paste_text.failed.desc");
						}
					})
				.pos(buttonX, buttonY + 50).width(100).build());
			addRenderableWidget(back);
		}
	}
}
