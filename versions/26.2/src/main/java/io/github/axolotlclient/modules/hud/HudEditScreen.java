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

package io.github.axolotlclient.modules.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.AxolotlClientConfig;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.modules.hud.gui.component.Positionable;
import io.github.axolotlclient.modules.hud.snapping.SnappingHelper;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.util.ExtraCursorTypes;
import io.github.axolotlclient.util.MathUtil;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class HudEditScreen extends Screen {
	private final Screen parent;
	private HudEntry current;
	private DrawPosition offset = null;
	private boolean mouseDown;
	private SnappingHelper snap;
	private ModificationMode pendingMode = ModificationMode.NONE;
	private ModificationMode mode = ModificationMode.NONE;

	public HudEditScreen() {
		this(null);
	}

	public HudEditScreen(Screen parent) {
		super(Component.empty());
		updateSnapState();
		mouseDown = false;
		this.parent = parent;
	}

	private void updateSnapState() {
		if (HudManager.getInstance().isSnappingEnabled() && current != null) {
			var bounds = SnappingHelper.getNonDependentEntries(current, HudManager.getInstance().getMoveableEntries())
				.stream()
				.map(Positionable::getTrueBounds)
				.collect(Collectors.toCollection(ArrayList::new));
			bounds.remove(current.getTrueBounds());
			snap = new SnappingHelper(bounds, current.getTrueBounds());
		} else if (snap != null) {
			snap = null;
		}
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		Optional<HudEntry> entry;
		if (current != null && mode != ModificationMode.NONE) {
			current.setHovered(true);
			graphics.requestCursor(mode.type);
			entry = Optional.of(current);
		} else {
			entry = HudManager.getInstance().getEntryXY(mouseX, mouseY);
			entry.ifPresent(abstractHudEntry -> abstractHudEntry.setHovered(true));
		}
		if (mouseDown && snap != null && HudManagerCommon.getInstance().hudLinkCreationEnabled.get()) {
			snap.renderHighlights(graphics, current);
		}
		HudManager.getInstance().renderPlaceholder(graphics, delta);
		if (getFocused() instanceof HudEntryWidget w) {
			w.extractRenderState(graphics, mouseX, mouseY, delta);
		}
		if (entry.isPresent()) {
			var bounds = entry.get().getTrueBounds();
			if (mode == ModificationMode.NONE && bounds.isMouseOver(mouseX, mouseY)) {
				var supportsScaling = entry.get().supportsScaling();
				var tolerance = Math.min(HudManagerCommon.HUD_RESCALE_GRAB_TOLERANCE, Math.min(bounds.width(), bounds.height()) / 2);
				var toleranceSquared = tolerance * tolerance;
				var pending = ModificationMode.MOVE;
				if (supportsScaling) {
					if (MathUtil.distSq(mouseX, mouseY, bounds.x(), bounds.y()) < toleranceSquared) {
						// top-left
						pending = ModificationMode.TOP_LEFT;
					} else if (MathUtil.distSq(mouseX, mouseY, bounds.xEnd(), bounds.yEnd()) < toleranceSquared) {
						// bottom-right
						pending = ModificationMode.BOTTOM_RIGHT;
					} else if (MathUtil.distSq(mouseX, mouseY, bounds.x(), bounds.yEnd()) < toleranceSquared) {
						// bottom-left
						pending = ModificationMode.BOTTOM_LEFT;
					} else if (MathUtil.distSq(mouseX, mouseY, bounds.xEnd(), bounds.y()) < toleranceSquared) {
						// top-right
						pending = ModificationMode.TOP_RIGHT;
					}
				}
				graphics.requestCursor(pending.type);
				this.pendingMode = pending;
			}
		} else if (current == null) {
			pendingMode = ModificationMode.NONE;
			mode = ModificationMode.NONE;
		}
		if (mouseDown && snap != null) {
			snap.renderSnaps(graphics);
		}
	}

	@Override
	public void removed() {
		mode = ModificationMode.NONE;
		super.removed();
	}

	@Override
	public void init() {
		mode = ModificationMode.NONE;

		HudManager.getInstance().getMoveableEntries().forEach(e -> addRenderableWidget(new HudEntryWidget(e)));

		this.addRenderableWidget(Button.builder(Component.translatable("hud.snapping").append(": ")
				.append(Component.translatable(HudManager.getInstance().isSnappingEnabled() ? "options.on" : "options.off")),
			buttonWidget -> {
				HudManager.getInstance().toggleSnapping();
				buttonWidget.setMessage(Component.translatable("hud.snapping").append(": ")
					.append(Component.translatable(HudManager.getInstance().isSnappingEnabled() ? "options.on" : "options.off")));
			}).bounds(width / 2 - 50, height / 2 + 12, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("hud.clientOptions"),
			_ -> {
				Screen screen = ConfigStyles.createScreen(this, AxolotlClient.getInstance().getConfigManager().getRoot());
				minecraft.gui.setScreen(screen);
			}).bounds(width / 2 - 75, height / 2 - 10, 150, 20).build());

		if (parent != null)
			addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, _ -> minecraft.gui.setScreen(parent))
				.bounds(width / 2 - 75, height - 50 + 22, 150, 20).build());
		else
			addRenderableWidget(Button.builder(Component.translatable("close"),
					_ -> minecraft.gui.setScreen(null))
				.bounds(width / 2 - 75, height - 50 + 22, 150, 20).build());
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		boolean value = super.mouseClicked(event, doubleClick);
		var mouseX = event.x();
		var mouseY = event.y();
		Optional<HudEntry> entry = HudManager.getInstance().getEntryXY((int) Math.round(mouseX),
			(int) Math.round(mouseY));
		if (event.button() == 0) {
			mouseDown = true;
			if (entry.isPresent()) {
				current = entry.get();
				offset = new DrawPosition((int) Math.round(mouseX - current.getTruePos().x()),
					(int) Math.round(mouseY - current.getTruePos().y()));
				if (pendingMode == ModificationMode.MOVE) {
					updateSnapState();
				}
				mode = pendingMode;
				return true;
			} else {
				mode = ModificationMode.NONE;
				current = null;
			}
		} else if (event.button() == 1) {
			entry.ifPresent(abstractHudEntry -> {
				Screen screen = ConfigStyles.createScreen(this, abstractHudEntry.getCategory());
				minecraft.gui.setScreen(screen);
			});
		}
		return value;
	}

	@Override
	public boolean mouseReleased(@NotNull MouseButtonEvent event) {
		if (current != null) {
			AxolotlClientConfig.getInstance().getConfigManager(current.getCategory()).save();
		}
		current = null;
		snap = null;
		mouseDown = false;
		mode = ModificationMode.NONE;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		var mouseX = event.x();
		var mouseY = event.y();
		if (current != null) {
			current.clearBoundsDependencies();
			if (mode == ModificationMode.MOVE) {
				current.setPos((int) (mouseX - offset.x() + current.offsetTrueWidth()), (int) (mouseY - offset.y() + current.offsetTrueHeight()));
				if (snap != null) {
					Collection<HudEntry> entries = null;
					Optional<Integer> snapX = snap.getCurrentXSnap(), snapY = snap.getCurrentYSnap();
					if (snapX.isPresent() || snapY.isPresent()) {
						entries = HudManagerCommon.getInstance().getMoveableEntries();
						entries.remove(current);
						entries.removeIf(e -> e.dependsOnX(current).isPresent() || e.dependsOnY(current).isPresent());
					}
					snap.setCurrent(current.getTrueBounds());
					if (snapX.isPresent()) {
						current.setX(snapX.get() + current.offsetTrueWidth());
						if (HudManagerCommon.getInstance().hudLinkCreationEnabled.get()) {
							snap.getXTouching(entries, current).forEach(c -> {
								c.getLeft().removeBoundsDependencyX(current);
								current.addBoundsDependency(c.getLeft(), c.getRight());
							});
						}
					}
					if (snapY.isPresent()) {
						current.setY(snapY.get() + current.offsetTrueHeight());
						if (HudManagerCommon.getInstance().hudLinkCreationEnabled.get()) {
							snap.getYTouching(entries, current).forEach(c -> {
								c.getLeft().removeBoundsDependencyY(current);
								current.addBoundsDependency(c.getLeft(), c.getRight());
							});
						}
					}
					HudManagerCommon.getInstance().saveHudDependencyLinks();
				}
			} else {
				var bounds = current.getTrueBounds();
				double newWidth, newHeight;
				if (mode == ModificationMode.TOP_LEFT) {
					// top-left corner
					newWidth = mouseX - bounds.xEnd();
					newHeight = mouseY - bounds.yEnd();
				} else if (mode == ModificationMode.BOTTOM_LEFT) {
					// bottom-left corner
					newWidth = mouseX - bounds.xEnd();
					newHeight = mouseY - bounds.y();
				} else if (mode == ModificationMode.TOP_RIGHT) {
					// top-right corner
					newWidth = mouseX - bounds.x();
					newHeight = mouseY - bounds.yEnd();
				} else if (mode == ModificationMode.BOTTOM_RIGHT) {
					// bottom-right corner
					newWidth = mouseX - bounds.x();
					newHeight = mouseY - bounds.y();
				} else {
					newWidth = bounds.width();
					newHeight = bounds.height();
				}
				float newScale = current.getScale() * Math.max((float) Math.abs(newWidth) / bounds.width(), (float) Math.abs(newHeight) / bounds.height());
				current.setScale(Math.max(0.1f, newScale));
				if (mode == ModificationMode.TOP_LEFT) {
					// top-left corner
					current.setPos(bounds.xEnd() - current.getTrueWidth() + current.offsetTrueWidth(), bounds.yEnd() - current.getTrueHeight() + current.offsetTrueHeight());
				} else if (mode == ModificationMode.BOTTOM_LEFT) {
					// bottom-left corner
					current.setX(bounds.xEnd() - current.getTrueWidth() + current.offsetTrueWidth());
				} else if (mode == ModificationMode.TOP_RIGHT) {
					// top-right corner
					current.setY(bounds.yEnd() - current.getTrueHeight() + current.offsetTrueHeight());
				}
			}
			if (current.tickable()) {
				current.tick();
			}
			return true;
		}
		return false;
	}

	@RequiredArgsConstructor
	private enum ModificationMode {
		NONE(CursorType.DEFAULT),
		MOVE(CursorTypes.RESIZE_ALL),
		TOP_LEFT(ExtraCursorTypes.RESIZE_NWSE),
		TOP_RIGHT(ExtraCursorTypes.RESIZE_NESW),
		BOTTOM_LEFT(ExtraCursorTypes.RESIZE_NESW),
		BOTTOM_RIGHT(ExtraCursorTypes.RESIZE_NWSE);
		private final CursorType type;
	}
}
