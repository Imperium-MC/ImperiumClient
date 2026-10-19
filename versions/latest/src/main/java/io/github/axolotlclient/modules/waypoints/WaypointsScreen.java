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

package io.github.axolotlclient.modules.waypoints;

import io.github.axolotlclient.api.SimpleTextInputScreen;
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Waypoint management screen (Alpine "waypoints" module, parity spec M2): lists every
 * waypoint with its coordinates and dimension, lets the player delete, enable/disable
 * (double-click or button) and add a new waypoint at the current position.
 */
public class WaypointsScreen extends Screen {

	private final Screen parent;
	private WaypointListWidget list;
	private Button deleteButton;
	private Button toggleButton;

	public WaypointsScreen(Screen parent) {
		super(Component.translatable("waypoints.screen.title"));
		this.parent = parent;
	}

	@Override
	public void init() {
		list = new WaypointListWidget(this, minecraft, width, height, 40, height - 64, 26);
		addRenderableWidget(list);
		list.populate();

		addRenderableWidget(Button.builder(Component.translatable("waypoints.add"), button ->
				minecraft.gui.setScreen(new SimpleTextInputScreen(this,
					Component.translatable("waypoints.add.title"),
					Component.translatable("waypoints.add.name"),
					WaypointsHud::addWaypointAtCurrentPosition)))
			.bounds(width / 2 - 155, height - 52, 150, 20).build());

		addRenderableWidget(toggleButton = Button.builder(Component.translatable("waypoints.toggle"), button -> {
				WaypointEntry entry = list.getSelected();
				if (entry != null) {
					Waypoint wp = entry.getWaypoint();
					WaypointManager.getInstance().replace(wp, wp.withEnabled(!wp.enabled()));
					refresh();
				}
			}).bounds(width / 2 + 5, height - 52, 150, 20).build());

		addRenderableWidget(deleteButton = Button.builder(Component.translatable("selectServer.delete"), button -> {
				WaypointEntry entry = list.getSelected();
				if (entry != null) {
					WaypointManager.getInstance().remove(entry.getWaypoint());
					refresh();
				}
			}).bounds(width / 2 - 155, height - 28, 150, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> minecraft.gui.setScreen(parent))
			.bounds(width / 2 + 5, height - 28, 150, 20).build());

		updateButtonStates();
	}

	private void refresh() {
		minecraft.gui.setScreen(new WaypointsScreen(parent));
	}

	void select(WaypointEntry entry) {
		list.setSelected(entry);
		updateButtonStates();
	}

	private void updateButtonStates() {
		boolean selected = list.getSelected() != null;
		deleteButton.active = selected;
		toggleButton.active = selected;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 20, -1);
	}

	private static class WaypointListWidget extends ObjectSelectionList<WaypointEntry> {

		private final WaypointsScreen screen;

		WaypointListWidget(WaypointsScreen screen, Minecraft client, int width, int height, int top, int bottom,
						   int entryHeight) {
			super(client, width, bottom - top, top, entryHeight);
			this.screen = screen;
		}

		private void populate() {
			WaypointManager.getInstance().getWaypoints()
				.forEach(waypoint -> addEntry(new WaypointEntry(screen, waypoint)));
		}

		@Override
		public int getRowWidth() {
			return super.getRowWidth() + 100;
		}

		@Override
		public boolean isFocused() {
			return screen.getFocused() == this;
		}
	}

	private static class WaypointEntry extends ObjectSelectionList.Entry<WaypointEntry> {

		private final WaypointsScreen screen;
		@Getter
		private final Waypoint waypoint;
		private final Minecraft client = Minecraft.getInstance();

		WaypointEntry(WaypointsScreen screen, Waypoint waypoint) {
			this.screen = screen;
			this.waypoint = waypoint;
		}

		@Override
		public Component getNarration() {
			return Component.literal(waypoint.name());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
								   float tickDelta) {
			int x = getContentX();
			int y = getContentY();

			// color swatch
			graphics.fill(x, y + 2, x + 8, y + 10, 0xFF000000 | waypoint.color());
			graphics.fill(x + 1, y + 3, x + 7, y + 9, waypoint.color());

			String state = waypoint.enabled() ? "waypoints.enabled" : "waypoints.disabled";
			graphics.text(client.font, waypoint.name(), x + 14, y, waypoint.color());
			graphics.text(client.font, Component.translatable(state), x + 14 + client.font.width(waypoint.name()) + 6, y,
				waypoint.enabled() ? 0xFF55FF55 : 0xFFFF5555);

			String coords = (int) Math.floor(waypoint.x()) + " / " + (int) Math.floor(waypoint.y()) + " / "
				+ (int) Math.floor(waypoint.z());
			String dimension = waypoint.dimension().replace("minecraft:", "");
			graphics.text(client.font, coords + "  (" + dimension + ")", x + 14, y + 12, 0xFFAAAAAA);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			screen.select(this);
			if (doubleClick) {
				WaypointManager.getInstance().replace(waypoint, waypoint.withEnabled(!waypoint.enabled()));
				screen.refresh();
			}
			return false;
		}
	}
}
