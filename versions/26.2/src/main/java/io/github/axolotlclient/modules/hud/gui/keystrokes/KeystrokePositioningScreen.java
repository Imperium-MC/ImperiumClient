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

package io.github.axolotlclient.modules.hud.gui.keystrokes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud;
import io.github.axolotlclient.modules.hud.snapping.SnappingHelper;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.util.DrawUtil;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import io.github.axolotlclient.util.ClientColors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class KeystrokePositioningScreen extends Screen {
	private final Screen parent;
	private final KeystrokeHud hud;
	private KeystrokeHud.Keystroke focused;
	private final KeystrokeHud.Keystroke editing;

	public KeystrokePositioningScreen(Screen parent, KeystrokeHud hud, KeystrokeHud.Keystroke focused) {
		super(Component.translatable("keystrokes.stroke.move"));
		this.parent = parent;
		this.hud = hud;
		if (hud.keystrokes == null) {
			hud.setKeystrokes();
		}
		this.editing = focused;
		mouseDown = false;
	}

	public KeystrokePositioningScreen(Screen parent, KeystrokeHud hud) {
		this(parent, hud, null);
	}

	private DrawPosition offset = null;
	private boolean mouseDown;
	private SnappingHelper snap;

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
		guiGraphicsExtractor.pose().pushMatrix();
		super.extractBackground(guiGraphicsExtractor, mouseX, mouseY, partialTick);
		HudManager.getInstance().renderPlaceholder(guiGraphicsExtractor, partialTick);
		guiGraphicsExtractor.pose().popMatrix();
		extractTransparentBackground(guiGraphicsExtractor);
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, _ -> onClose()).pos(width / 2 - 75, height - 50 + 22).width(150).build());
		this.addRenderableWidget(Button.builder(Component.translatable("hud.snapping").append(": ")
				.append(Component.translatable(HudManager.getInstance().isSnappingEnabled() ? "options.on" : "options.off")),
			buttonWidget -> {
				HudManager.getInstance().toggleSnapping();
				buttonWidget.setMessage(Component.translatable("hud.snapping").append(": ")
					.append(Component.translatable(HudManager.getInstance().isSnappingEnabled() ? "options.on" : "options.off")));
			}).bounds(width / 2 - 50, height - 50, 100, 20).build());
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
		if (editing != null) {
			drawStroke(guiGraphicsExtractor, mouseX, mouseY, editing);
		} else {
			hud.keystrokes.forEach(s -> drawStroke(guiGraphicsExtractor, mouseX, mouseY, s));
		}
		if (mouseDown && snap != null) {
			snap.renderSnaps(guiGraphicsExtractor);
		}
	}

	private void drawStroke(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, KeystrokeHud.Keystroke s) {
		var rect = getScaledRenderPos(s);
		if (rect.isMouseOver(mouseX, mouseY)) {
			DrawUtil.fillRect(guiGraphicsExtractor, rect, ClientColors.SELECTOR_BLUE.withAlpha(100));
			guiGraphicsExtractor.requestCursor(CursorTypes.RESIZE_ALL);
		} else {
			DrawUtil.fillRect(guiGraphicsExtractor, rect, ClientColors.WHITE.withAlpha(50));
		}
		guiGraphicsExtractor.pose().pushMatrix();
		guiGraphicsExtractor.pose().scale(hud.getScale(), hud.getScale());
		s.render(guiGraphicsExtractor);
		guiGraphicsExtractor.pose().popMatrix();
		DrawUtil.outlineRect(guiGraphicsExtractor, rect, Colors.BLACK);
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		var value = super.mouseClicked(event, doubleClick);
		if (event.button() == 0) {
			var mouseX = event.x();
			var mouseY = event.y();
			Optional<KeystrokeHud.Keystroke> entry = Optional.empty();
			Optional<Rectangle> pos = Optional.empty();
			if (editing == null) {
				for (KeystrokeHud.Keystroke k : hud.keystrokes) {
					pos = Optional.of(getScaledRenderPos(k));
					if (pos.get().isMouseOver(mouseX, mouseY)) {
						entry = Optional.of(k);
						break;
					}
				}
			} else {
				pos = Optional.of(getScaledRenderPos(editing));
				if (pos.get().isMouseOver(mouseX, mouseY)) {
					entry = Optional.of(editing);
				}
			}
			if (entry.isPresent()) {
				focused = entry.get();
				mouseDown = true;
				var rect = pos.get();
				offset = new DrawPosition((int) Math.round(mouseX - rect.x()),
					(int) Math.round(mouseY - rect.y()));
				updateSnapState();
				return true;
			} else {
				focused = null;
			}
		} else if (event.button() == 1 && editing == null) {
			var mouseX = event.x();
			var mouseY = event.y();
			Optional<KeystrokeHud.Keystroke> entry = Optional.empty();
			for (KeystrokeHud.Keystroke k : hud.keystrokes) {
				var pos = getScaledRenderPos(k);
				if (pos.isMouseOver(mouseX, mouseY)) {
					entry = Optional.of(k);
					break;
				}
			}
			entry.ifPresent(stroke -> minecraft.gui.setScreen(new ConfigureKeyBindScreen(this, hud, stroke, false)));
		}
		return value;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
		hud.saveKeystrokes();
	}

	@Override
	public boolean mouseReleased(@NotNull MouseButtonEvent event) {
		if (focused != null) {
			hud.saveKeystrokes();
		}
		snap = null;
		mouseDown = false;
		focused = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(@NotNull MouseButtonEvent event, double deltaX, double deltaY) {
		if (focused != null && mouseDown) {
			focused.setX((int) Math.round((event.x() - offset.x()) / hud.getScale()));
			focused.setY((int) Math.round((event.y() - offset.y()) / hud.getScale()));
			if (snap != null) {
				Optional<Integer> snapX, snapY;
				var rect = getScaledRenderPos(focused);
				snap.setCurrent(rect);
				if ((snapX = snap.getCurrentXSnap()).isPresent()) {
					focused.setX(Math.round(snapX.get() / hud.getScale()));
				}
				if ((snapY = snap.getCurrentYSnap()).isPresent()) {
					focused.setY(Math.round(snapY.get() / hud.getScale()));
				}
			}
			return true;
		}
		return false;
	}

	private Rectangle getScaledRenderPos(KeystrokeHud.Keystroke stroke) {
		return stroke.getRenderPosition().scale(hud.getScale());
	}

	private List<Rectangle> getAllBounds() {
		return Stream.concat(HudManager.getInstance().getAllBounds().stream(), hud.keystrokes.stream().map(this::getScaledRenderPos)).toList();
	}

	private void updateSnapState() {
		if (HudManager.getInstance().isSnappingEnabled() && focused != null) {
			snap = new SnappingHelper(getAllBounds(), getScaledRenderPos(focused));
		} else if (snap != null) {
			snap = null;
		}
	}
}
