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

package io.github.axolotlclient.util.notifications;

import java.util.List;

import com.google.common.collect.ImmutableList;
import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AxolotlClientToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "toast/axolotlclient");
	private static final int DISPLAY_TIME_MILLIS = 5000;
	private static final int MAX_LINE_SIZE = 200;
	private static final int LINE_SPACING = 12;
	private static final int MARGIN = 10;
	private final Component title;
	private final List<FormattedCharSequence> messageLines;
	private final int width;
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

	public AxolotlClientToast(Component title, @Nullable Component message) {
		this(
			title,
			nullToEmpty(message),
			Math.max(DEFAULT_WIDTH, 2 * MARGIN + 15 + Math.max(Minecraft.getInstance().font.width(title), message == null ? 0 : Minecraft.getInstance().font.width(message)))
		);
	}

	public static AxolotlClientToast multiline(Minecraft minecraft, Component title, Component message) {
		Font font = minecraft.font;
		List<FormattedCharSequence> list = font.split(message, MAX_LINE_SIZE);
		int i = Math.min(MAX_LINE_SIZE, Math.max(font.width(title), list.stream().mapToInt(font::width).max().orElse(MAX_LINE_SIZE)));
		return new AxolotlClientToast(title, list, i + 2 * MARGIN + 15);
	}

	private AxolotlClientToast(Component title, List<FormattedCharSequence> messageLines, int width) {
		this.title = title;
		this.messageLines = messageLines;
		this.width = width;
	}

	private static ImmutableList<@NotNull FormattedCharSequence> nullToEmpty(@Nullable Component message) {
		return message == null ? ImmutableList.of() : ImmutableList.of(message.getVisualOrderText());
	}

	@Override
	public int width() {
		return this.width;
	}

	@Override
	public int height() {
		return 2 * MARGIN + Math.max(this.messageLines.size(), 1) * LINE_SPACING;
	}

	@Override
	public Toast.@NotNull Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	@Override
	public void update(@NotNull ToastManager toastManager, long visibilityTime) {
		var time = DISPLAY_TIME_MILLIS * toastManager.getNotificationDisplayTimeMultiplier();
		this.wantedVisibility = visibilityTime < time ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, @NotNull Font font, long visibilityTime) {
		guiGraphicsExtractor.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		guiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, (Identifier) AxolotlClientCommon.BADGE_PATH, 4, 4, 0, 0, 15, 15, 15, 15);
		int textOffset = 22;
		if (this.messageLines.isEmpty()) {
			guiGraphicsExtractor.text(font, this.title, textOffset, LINE_SPACING, -256, false);
		} else {
			guiGraphicsExtractor.text(font, this.title, textOffset, 7, -256, false);

			for (int i = 0; i < this.messageLines.size(); i++) {
				guiGraphicsExtractor.text(font, this.messageLines.get(i), textOffset, 18 + i * LINE_SPACING, -1, false);
			}
		}
	}
}
