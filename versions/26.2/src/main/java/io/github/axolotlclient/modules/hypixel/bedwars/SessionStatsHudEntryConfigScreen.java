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

package io.github.axolotlclient.modules.hypixel.bedwars;

import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class SessionStatsHudEntryConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final SessionStatisticsOverlay hud = BedwarsMod.getInstance().getSessionStatsOverlay();
	private final EntryList list = new EntryList(minecraft, layout.getWidth(), layout.getContentHeight(), layout.getHeaderHeight(), 24);

	public SessionStatsHudEntryConfigScreen(@Nullable Screen parent) {
		super(tr("title"));
		this.parent = parent;
	}

	private static Component tr(String key) {
		return Component.translatable("bedwars.session_stats.configure." + key);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
		AxolotlClientCommon.getInstance().saveConfig();
	}

	@Override
	protected void init() {
		layout.addTitleHeader(getTitle(), getFont());
		layout.addToContents(list);
		layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, btn -> onClose()).build());
		repositionElements();
		layout.visitWidgets(this::addRenderableWidget);
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		list.updateSize(width, layout);
	}

	private class EntryList extends ContainerObjectSelectionList<ListEntry> {

		private final int rowWidth;

		public EntryList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			rowWidth = hud.getEntries().stream().mapToInt(e -> font.width((Component) e.name())).max().orElse(100) + 180;
			hud.getEntries().forEach(e -> addEntry(new ListEntry(e, this)));
		}

		void moveEntry(ListEntry entry, int direction) {
			var index = children().indexOf(entry);
			var next = index + direction;
			swap(index, next);
			var v = hud.getEntries().get(next);
			hud.getEntries().set(index, v);
			hud.getEntries().set(next, entry.entry);
		}

		@Override
		public int getRowWidth() {
			return rowWidth;
		}
	}

	private class ListEntry extends ContainerObjectSelectionList.Entry<ListEntry> {
		private final StringWidget name;
		private final CycleButton<Boolean> showHide;
		private final SessionStatisticsOverlay.SessionStatsEntry entry;
		private final EntryList list;
		private final Button up, down;

		private ListEntry(SessionStatisticsOverlay.SessionStatsEntry entry, EntryList list) {
			this.entry = entry;
			this.list = list;
			name = new StringWidget((Component) entry.name(), getFont())
				.setMaxWidth(150, StringWidget.TextOverflow.SCROLLING);
			showHide = CycleButton.booleanBuilder(tr("entry.hide"), tr("entry.show"), entry.enabled().get())
				.displayOnlyValue()
				.create(0, 0, 50, 20, Component.empty(), (b, v) -> entry.enabled().set(v));
			up = Button.builder(tr("entry.move_up"), btn ->
				minecraft.execute(() -> list.moveEntry(this, -1))).width(50).build();
			down = Button.builder(tr("entry.move_down"), btn ->
				minecraft.execute(() -> list.moveEntry(this, 1))).width(50).build();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(name, showHide, up, down);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean isHovering, float partialTick) {
			var x = getContentX();
			var y = getContentY();
			name.setPosition(x, y + getContentHeight() / 2 - name.getHeight() / 2);
			var right = getContentRight();
			down.setPosition(right - down.getWidth(), y);
			up.setPosition(down.getX() - up.getWidth(), y);
			showHide.setPosition(up.getX() - showHide.getWidth(), y);

			var size = list.children().size();
			var index = list.children().indexOf(this);

			down.active = index != size - 1;
			up.active = index != 0;

			name.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			down.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			up.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			showHide.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(name, showHide, up, down);
		}
	}
}
