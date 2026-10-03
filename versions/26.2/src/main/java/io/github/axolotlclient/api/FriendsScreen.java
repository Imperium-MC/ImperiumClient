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

import io.github.axolotlclient.api.chat.ChatScreen;
import io.github.axolotlclient.api.requests.ChannelRequest;
import io.github.axolotlclient.api.requests.FriendRequest;
import io.github.axolotlclient.api.util.AlphabeticalComparator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class FriendsScreen extends Screen {

	private final Screen parent;

	private UserListWidget widget;

	private Button chatButton, removeButton, onlineTab, allTab, pendingTab, blockedTab;
	private Button denyButton, acceptButton, unblockButton, cancelButton;
	private FriendsButton vanillaFriendsButton;

	private Tab current = Tab.ONLINE;

	protected FriendsScreen(Screen parent, Tab tab) {
		this(parent);
		current = tab;
	}

	public FriendsScreen(Screen parent) {
		super(Component.translatable("api.screen.friends"));
		this.parent = parent;
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
		if (minecraft.gui.screen() instanceof FriendsOverlayScreen) return;
		super.extractBlurredBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, -1);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		} else if (event.key() == 294) {
			this.refresh();
			return true;
		} else if (this.widget.getSelected() != null) {
			if (event.key() != 257 && event.key() != 335) {
				return this.widget.keyPressed(event);
			} else {
				this.openChat();
				return true;
			}
		} else {
			return false;
		}
	}

	@Override
	protected void init() {
		addRenderableWidget(widget = new UserListWidget(this, minecraft, width, height, 32, height - 64, 35));

		widget.clearEntries();

		if (current == Tab.ALL || current == Tab.ONLINE) {
			FriendRequest.getInstance().getFriends().whenCompleteAsync((list, t) -> widget.setUsers(
				list.stream().sorted((u1, u2) -> new AlphabeticalComparator().compare(u1.getName(), u2.getName()))
					.filter(user -> {
						if (current == Tab.ONLINE) {
							return user.getStatus().isOnline();
						}
						return true;
					}).toList()));
		} else if (current == Tab.PENDING) {
			FriendRequest.getInstance().getFriendRequests().whenCompleteAsync((con, th) -> {

				con.getLeft().stream()
					.sorted((u1, u2) -> new AlphabeticalComparator().compare(u1.getName(), u2.getName())).forEach(
						user -> widget.addEntry(new UserListWidget.UserListEntry(user, Component.translatable(
							"api.friends.pending.incoming"))));
				con.getRight().stream()
					.sorted((u1, u2) -> new AlphabeticalComparator().compare(u1.getName(), u2.getName())).forEach(
						user -> widget.addEntry(new UserListWidget.UserListEntry(user, Component.translatable(
							"api.friends.pending.outgoing")).outgoing()));
			});
		} else if (current == Tab.BLOCKED) {
			FriendRequest.getInstance().getBlocked().whenCompleteAsync((list, th) -> widget.setUsers(
				list.stream().sorted((u1, u2) -> new AlphabeticalComparator().compare(u1.getName(), u2.getName()))
					.toList()));
		}

		vanillaFriendsButton = this.addRenderableWidget(
			CommonButtons.friends(
				20,
				_ -> OnlineOptionsScreen.confirmFriendsListEnabled(this.minecraft, () -> this.minecraft.gui.setScreen(new FriendsOverlayScreen(this)), this),
				!this.minecraft.isDemo()
			)
		);
		vanillaFriendsButton.setPosition(width - 30, 10);

		this.addRenderableWidget(blockedTab = Button.builder(Component.translatable("api.friends.tab.blocked"),
				button -> minecraft.gui.setScreen(
					new FriendsScreen(parent, Tab.BLOCKED))
			).bounds(this.width / 2 + 24, this.height - 52, 57, 20)
			.build());

		this.addRenderableWidget(pendingTab = Button.builder(Component.translatable("api.friends.tab.pending"),
				button -> minecraft.gui.setScreen(
					new FriendsScreen(parent, Tab.PENDING))
			).bounds(this.width / 2 - 34, this.height - 52, 57, 20)
			.build());

		this.addRenderableWidget(allTab = Button.builder(Component.translatable("api.friends.tab.all"),
				button -> minecraft.gui.setScreen(
					new FriendsScreen(parent, Tab.ALL))
			).bounds(this.width / 2 - 94, this.height - 52, 57, 20)
			.build());

		this.addRenderableWidget(onlineTab = Button.builder(Component.translatable("api.friends.tab.online"),
				button -> minecraft.gui.setScreen(
					new FriendsScreen(parent, Tab.ONLINE))
			).bounds(this.width / 2 - 154, this.height - 52, 57, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.translatable("api.friends.add"),
			button -> minecraft.gui.setScreen(new AddFriendScreen(this))
		).bounds(this.width / 2 + 88, this.height - 52, 66, 20).build());

		this.removeButton =
			this.addRenderableWidget(Button.builder(Component.translatable("api.friends.remove"), button -> {
				UserListWidget.UserListEntry entry = this.widget.getSelected();
				if (entry != null) {
					removeButton.active = false;
					FriendRequest.getInstance().removeFriend(entry.getUser()).thenRun(() -> minecraft.submit(this::refresh));
				}
			}).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

		addRenderableWidget(denyButton = Button.builder(Component.translatable("api.friends.request.deny"),
			button -> denyRequest()
		).bounds(this.width / 2 - 50, this.height - 28, 48, 20).build());

		addRenderableWidget(acceptButton = Button.builder(Component.translatable("api.friends.request.accept"),
				button -> acceptRequest()
			).bounds(this.width / 2 + 2, this.height - 28, 48, 20)
			.build());

		unblockButton = addRenderableWidget(Button.builder(Component.translatable("api.users.unblock"),
			b -> {
				b.active = false;
				FriendRequest.getInstance().unblockUser(widget.getSelected().getUser()).thenRun(() -> minecraft.submit(this::refresh));
			}).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
		cancelButton = addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> {
			b.active = false;
			FriendRequest.getInstance().cancelFriendRequest(widget.getSelected().getUser()).thenRun(() -> minecraft.submit(this::refresh));
		}).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

		this.addRenderableWidget(chatButton =
			Button.builder(Component.translatable("api.friends.chat"), button -> openChat())
				.bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.gui.setScreen(this.parent))
			.bounds(this.width / 2 + 4 + 50, this.height - 28, 100, 20).build());
		updateButtonActivationStates();
	}

	private void refresh() {
		minecraft.gui.setScreen(new FriendsScreen(parent));
	}

	private void denyRequest() {
		UserListWidget.UserListEntry entry = widget.getSelected();
		if (entry != null) {
			denyButton.active = false;
			FriendRequest.getInstance().denyFriendRequest(entry.getUser()).thenRun(() -> minecraft.submit(this::refresh));
		}
	}

	private void acceptRequest() {
		UserListWidget.UserListEntry entry = widget.getSelected();
		if (entry != null) {
			acceptButton.active = false;
			FriendRequest.getInstance().acceptFriendRequest(entry.getUser()).thenRun(() -> minecraft.submit(this::refresh));
		}
	}

	private void updateButtonActivationStates() {
		UserListWidget.UserListEntry entry = widget.getSelected();
		chatButton.active = entry != null && (current == Tab.ALL || current == Tab.ONLINE);

		removeButton.visible = true;
		unblockButton.active = removeButton.active = entry != null;
		denyButton.visible = false;
		acceptButton.visible = unblockButton.visible = cancelButton.visible = false;
		if (current == Tab.ONLINE) {
			onlineTab.active = false;
			allTab.active = pendingTab.active = blockedTab.active = true;
		} else if (current == Tab.ALL) {
			allTab.active = false;
			onlineTab.active = pendingTab.active = blockedTab.active = true;
		} else if (current == Tab.PENDING) {
			pendingTab.active = false;
			onlineTab.active = allTab.active = blockedTab.active = true;
			removeButton.visible = false;

			if (entry != null && entry.isOutgoingRequest()) {
				cancelButton.visible = true;
			} else {
				denyButton.visible = true;
				acceptButton.visible = true;
			}
			denyButton.active = acceptButton.active = entry != null;
		} else if (current == Tab.BLOCKED) {
			blockedTab.active = false;
			onlineTab.active = allTab.active = pendingTab.active = true;
			removeButton.visible = false;
			unblockButton.visible = true;
		}
	}

	public void openChat() {
		if (!chatButton.active) {
			return;
		}
		UserListWidget.UserListEntry entry = widget.getSelected();
		if (entry != null) {
			chatButton.active = false;
			ChannelRequest.getOrCreateDM(entry.getUser())
				.thenAccept(c -> minecraft.execute(() -> minecraft.gui.setScreen(new ChatScreen(this, c))));
		}
	}

	public void select(UserListWidget.UserListEntry entry) {
		this.widget.setSelected(entry);
		this.updateButtonActivationStates();
	}

	@Override
	public void tick() {
		if (this.minecraft.getPlayerSocialManager().isFriendListEnabled() && vanillaFriendsButton != null) {
			vanillaFriendsButton.refreshIncomingRequestCount();
		}
	}

	public enum Tab {
		ONLINE, ALL, PENDING, BLOCKED
	}
}
