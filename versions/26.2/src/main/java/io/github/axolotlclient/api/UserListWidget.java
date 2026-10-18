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

package io.github.axolotlclient.api;

import java.util.List;
import java.util.stream.Collectors;

import io.github.axolotlclient.api.types.PkSystem;
import io.github.axolotlclient.api.types.User;
import io.github.axolotlclient.modules.auth.Auth;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public class UserListWidget extends ObjectSelectionList<UserListWidget.UserListEntry> {

	private final FriendsScreen screen;

	public UserListWidget(FriendsScreen screen, Minecraft client, int width, int height, int top, int bottom, int entryHeight) {
		super(client, width, bottom - top, top, entryHeight);
		this.screen = screen;
	}

	public void setUsers(List<User> users) {
		users.forEach(user -> addEntry(new UserListEntry(user)));
	}

	@Override
	public int getRowWidth() {
		return super.getRowWidth() + 85;
	}

	public int addEntry(UserListEntry entry) {
		return super.addEntry(entry.init(screen));
	}

	@Override
	protected int scrollBarX() {
		return super.scrollBarX() + 30;
	}

	@Override
	public boolean isFocused() {
		return this.screen.getFocused() == this;
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo mouseButtonInfo) {
		return true;
	}

	@Override
	public void clearEntries() {
		super.clearEntries();
	}

	public static class UserListEntry extends Entry<UserListEntry> {

		@Getter
		private final User user;
		private final Minecraft client;
		private MutableComponent note;
		private FriendsScreen screen;

		@Getter
		private boolean outgoingRequest;

		public UserListEntry(User user, MutableComponent note) {
			this(user);
			this.note = note.withStyle(ChatFormatting.ITALIC);
		}

		public UserListEntry(User user) {
			this.client = Minecraft.getInstance();
			this.user = user;
		}

		public UserListEntry init(FriendsScreen screen) {
			this.screen = screen;
			return this;
		}

		public UserListEntry outgoing() {
			outgoingRequest = true;
			return this;
		}

		@Override
		public Component getNarration() {
			return Component.literal(user.getName());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			var x = getContentX();
			var y = getContentY();
			var entryWidth = getContentWidth();
			var entryHeight = getContentHeight();
			if (user.isSystem()) {
				MutableComponent fronters = Component.literal(
					user.getSystem().getFronters().stream().map(PkSystem.Member::getDisplayName)
						.collect(Collectors.joining("/")));
				Component tag = Component.literal("(" + user.getSystem().getName() + "/" + user.getName() + ")")
					.setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY));
				graphics.text(client.font, fronters.append(tag), x + 3, y + 1, -1);
			} else {
				graphics.text(client.font, user.getName(), x + 3 + 33, y + 1, -1);
			}

			if (user.getStatus().isOnline() && user.getStatus().getActivity() != null) {
				graphics.text(client.font, user.getStatus().getTitle(), x + 3 + 33, y + 12, 0xFF808080);
				graphics.text(client.font, user.getStatus().getDescription(), x + 3 + 40, y + 23, 0xFF808080);
			} else if (user.getStatus().getLastOnline() != null) {
				graphics.text(client.font, user.getStatus().getLastOnline(), x + 3 + 33, y + 12, 0xFF808080);
			}

			if (note != null) {
				graphics.text(client.font, note, x + entryWidth - client.font.width(note) - 4,
					y + entryHeight - 10, 0xFF808080
				);
			}

			Identifier texture = Auth.getInstance().getSkinTexture(user.getUuid());
			PlayerFaceExtractor.extractRenderState(graphics, texture, x - 1, y - 1, 33, true, false, -1);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			this.screen.select(this);
			if (doubleClick && client.level == null) {
				screen.openChat();
			}

			return false;
		}
	}
}
