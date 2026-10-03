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

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class FiltersList extends ContainerObjectSelectionList<FiltersList.Entry> {
	private final SpacerEntry spacer = new SpacerEntry();
	private final NewEntry newEntry = new NewEntry();
	final FilterListConfigurationScreen screen;

	public FiltersList(FilterListConfigurationScreen screen) {
		super(Minecraft.getInstance(), screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 24);
		this.screen = screen;

		reload();
	}

	public void reload() {
		clearEntries();
		for (String entry : screen.filters) {
			this.addEntry(new FilterEntry(entry));
		}

		addEntry(spacer);
		addEntry(newEntry);
	}

	@Override
	public int getRowWidth() {
		return 340;
	}

	public void apply() {
		screen.filters.clear();
		screen.filters.addAll(children().stream().filter(e -> e instanceof FilterEntry)
			.map(e -> (FilterEntry) e)
			.map(e -> e.editBox.getValue())
			.filter(s -> !s.isBlank()).toList());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		children().stream().filter(e -> e instanceof FilterEntry)
			.map(e -> (FilterEntry) e).map(e -> e.editBox).forEach(e -> e.setFocused(false));
		return super.mouseClicked(event, doubleClick);
	}

	@Environment(EnvType.CLIENT)
	public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

	}

	public static class SpacerEntry extends Entry {

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {

		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Nullable
		@Override
		public ComponentPath nextFocusPath(FocusNavigationEvent event) {
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	public class FilterEntry extends Entry {
		private static final Component REMOVE_BUTTON_TITLE = Component.translatable("autoboop.filters.remove");
		private final EditBox editBox;
		private final Button removeButton;

		FilterEntry(String filter) {
			this.editBox = new EditBox(screen.getFont(), 0, 0, 200, 20, Component.translatable("autoboop.filters.edit"));
			editBox.setValue(filter);
			editBox.setMaxLength(16);
			this.removeButton = Button.builder(REMOVE_BUTTON_TITLE, b -> {
					removeEntry(this);
					apply();
					refreshScrollAmount();
				}).bounds(0, 0, 50, 20)
				.build();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int i = scrollBarX() - removeButton.getWidth() - 10;
			int j = getContentY() - 2;
			this.removeButton.setPosition(i, j);
			this.removeButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);

			this.editBox.setPosition(getContentX(), j);
			this.editBox.setWidth(i - getContentX() - 4);
			this.editBox.extractWidgetRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of(this.editBox, removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(this.editBox, removeButton);
		}
	}

	public class NewEntry extends Entry {

		private final Button addButton;

		public NewEntry() {
			this.addButton = Button.builder(Component.translatable("autoboop.filters.add"), button -> {
					removeEntry(spacer);
					removeEntry(newEntry);
					addEntry(new FilterEntry(""));
					addEntry(spacer);
					addEntry(newEntry);
					apply();
					setScrollAmount(maxScrollAmount());
				}).bounds(0, 0, 150, 20)
				.build();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(addButton);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int i = scrollBarX() - getContentWidth() / 2 - 10 - addButton.getWidth() / 2;
			int j = getContentY() - 2;
			this.addButton.setPosition(i, j);
			this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(addButton);
		}
	}
}
