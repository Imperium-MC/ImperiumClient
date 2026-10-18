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

package io.github.axolotlclient.modules.hud;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.util.ClientColors;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class HudEntryWidget implements Renderable, GuiEventListener, NarratableEntry {

	private final HudEntry entry;
	private boolean focused, moving;

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
		if (!focused) {
			moving = false;
		}
	}

	@Override
	public boolean isFocused() {
		return entry.isEnabled() && focused;
	}

	@Override
	public @NotNull NarrationPriority narrationPriority() {
		return isFocused() ? NarrationPriority.FOCUSED : (entry.isHovered() ? NarrationPriority.HOVERED : NarrationPriority.NONE);
	}

	@Override
	public void updateNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("hud.entry.name", entry.getName()));
		if (moving) {
			narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("hud.entry.usage.move"));
		} else {
			narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("hud.entry.usage"));
		}
	}

	@Override
	public @NotNull ScreenRectangle getRectangle() {
		if (!entry.isEnabled()) {
			return ScreenRectangle.empty();
		}
		return new ScreenRectangle(entry.getTrueX(), entry.getTrueY(), entry.getTrueWidth(), entry.getTrueHeight());
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return entry.isEnabled() && entry.isHovered();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
		if (isFocused()) {
			var bounds = entry.getTrueBounds();
			guiGraphicsExtractor.br$outlineRect(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2, moving ? ClientColors.SELECTOR_RED.toInt() : -1);
		}
	}

	@Nullable
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		if (!entry.isEnabled()) {
			return null;
		} else {
			return !this.isFocused() ? ComponentPath.leaf(this) : null;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		var mc = Minecraft.getInstance();
		boolean consume = false;
		var keyCode = event.key();
		if (moving) {
			consume = true;
			int step = mc.hasControlDown() ? 10 : 1;
			if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_SPACE) {
				moving = false;
			} else if (keyCode == InputConstants.KEY_UP) {
				entry.setY(entry.getRawTrueY() - step + entry.offsetTrueHeight());
			} else if (keyCode == InputConstants.KEY_DOWN) {
				entry.setY(entry.getRawTrueY() + step + entry.offsetTrueHeight());
			} else if (keyCode == InputConstants.KEY_LEFT) {
				entry.setX(entry.getRawTrueX() - step + entry.offsetTrueWidth());
			} else if (keyCode == InputConstants.KEY_RIGHT) {
				entry.setX(entry.getRawTrueX() + step + entry.offsetTrueWidth());
			} else {
				consume = false;
			}
		}
		if (consume) {
			return true;
		}
		consume = true;
		if (keyCode == InputConstants.KEY_SPACE) {
			moving = true;
		} else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			mc.gui.setScreen(ConfigStyles.createScreen(mc.gui.screen(), entry.getCategory()));
		} else if (keyCode == InputConstants.KEY_DELETE) {
			entry.setEnabled(false);
		} else {
			consume = false;
		}
		return consume;
	}
}
