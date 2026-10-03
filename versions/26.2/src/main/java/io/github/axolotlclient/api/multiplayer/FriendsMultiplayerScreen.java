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

package io.github.axolotlclient.api.multiplayer;

import io.github.axolotlclient.api.FriendsScreen;
import io.github.axolotlclient.api.handlers.StatusUpdateHandler;
import io.github.axolotlclient.api.requests.FriendRequest;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class FriendsMultiplayerScreen extends Screen {
	@Getter
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final Screen lastScreen;
	protected FriendsMultiplayerSelectionList serverSelectionList;
	private Button selectButton;
	private final Button friendsCountButton = Button.builder(Component.translatable("api.servers.friends", "..."), button -> {
	}).build();
	private ServerData editingServer;
	private static final Component NO_ONLINE_FRIENDS = Component.translatable("api.servers.friends.no_online_friends");
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 60, 60);

	public FriendsMultiplayerScreen(Screen lastScreen) {
		super(Component.translatable("api.servers.friends.title"));
		this.lastScreen = lastScreen;
	}

	@Override
	protected void init() {

		var header = this.layout.addToHeader(LinearLayout.vertical()).spacing(8);
		header.addChild(new StringWidget(getTitle(), getFont()), LayoutSettings::alignHorizontallyCenter);
		var headerRow = header.addChild(LinearLayout.horizontal()).spacing(4);
		headerRow.defaultCellSetting().alignHorizontallyCenter();
		headerRow.addChild(Button.builder(Component.translatable("api.servers"), button ->
			minecraft.gui.setScreen(new JoinMultiplayerScreen(lastScreen))).width(100).build());
		headerRow.addChild(friendsCountButton);
		friendsCountButton.setWidth(100);
		friendsCountButton.active = false;


		this.serverSelectionList = this.layout.addToContents(new FriendsMultiplayerSelectionList(this, this.minecraft, this.width, this.layout.getContentHeight(), layout.getHeaderHeight(), 36));
		FriendRequest.getInstance().getFriends().thenAccept(friends -> {
			friendsCountButton.setMessage(Component.translatable("api.servers.friends", friends.stream().filter(u -> u.getStatus().isOnline()).count()));
			this.serverSelectionList.updateList(friends);
		});
		StatusUpdateHandler.addUpdateListener("friends_multiplayer_screen", serverSelectionList::updateEntry);

		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal().spacing(4));
		LinearLayout linearLayout3 = linearLayout.addChild(LinearLayout.horizontal().spacing(4));

		this.selectButton = linearLayout2.addChild(Button.builder(Component.translatable("selectServer.select"), buttonx -> this.joinSelectedServer()).width(100).build());
		linearLayout2.addChild(Button.builder(Component.translatable("selectServer.direct"), buttonx -> {
			this.editingServer = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
			this.minecraft.gui.setScreen(new DirectJoinServerScreen(this, this::directJoinCallback, this.editingServer));
		}).width(100).build());
		linearLayout2.addChild(Button.builder(Component.translatable("api.friends"), buttonx ->
			this.minecraft.gui.setScreen(new FriendsScreen(this))).width(100).build());
		linearLayout3.addChild(Button.builder(Component.translatable("selectServer.edit"), buttonx -> {
		}).width(74).build()).active = false;
		linearLayout3.addChild(Button.builder(Component.translatable("selectServer.delete"), buttonx -> {
		}).width(74).build()).active = false;
		linearLayout3.addChild(
			Button.builder(Component.translatable("selectServer.refresh"), buttonx -> this.refreshServerList()).width(74).build()
		);
		linearLayout3.addChild(Button.builder(CommonComponents.GUI_BACK, buttonx -> this.onClose()).width(74).build());
		layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.onSelectedChange();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.serverSelectionList != null) {
			this.serverSelectionList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	@Override
	public void tick() {
		this.pinger.tick();
	}

	@Override
	public void removed() {
		StatusUpdateHandler.removeUpdateListener("friends_multiplayer_screen");
		this.pinger.removeAll();
	}

	@Override
	public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);

		if (serverSelectionList.children().isEmpty()) {
			guiGraphicsExtractor.centeredText(font, NO_ONLINE_FRIENDS, width / 2, height / 2 - font.lineHeight / 2, -1);
		}
	}

	private void refreshServerList() {
		this.minecraft.gui.setScreen(new FriendsMultiplayerScreen(this.lastScreen));
	}

	private void directJoinCallback(boolean confirmed) {
		if (confirmed) {
			ServerList servers = new ServerList(minecraft);
			servers.load();
			ServerData serverData = servers.get(this.editingServer.ip);
			if (serverData == null) {
				servers.add(this.editingServer, true);
				servers.save();
				this.join(this.editingServer);
			} else {
				this.join(serverData);
			}
		} else {
			this.minecraft.gui.setScreen(this);
		}
	}

	public void joinSelectedServer() {
		FriendsMultiplayerSelectionList.Entry entry = this.serverSelectionList.getSelected();
		if (entry != null) {
			this.join(entry.getServerData());
		}
	}

	private void join(ServerData server) {
		if (server == null) {
			return;
		}
		ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(server.ip), server, false, null);
	}

	public void setSelected(FriendsMultiplayerSelectionList.Entry selected) {
		this.serverSelectionList.setSelected(selected);
		this.onSelectedChange();
	}

	protected void onSelectedChange() {
		this.selectButton.active = false;
		FriendsMultiplayerSelectionList.Entry entry = this.serverSelectionList.getSelected();
		if (entry != null && !(entry instanceof FriendsMultiplayerSelectionList.LoadingHeader)) {
			this.selectButton.active = entry.canJoin();
		}
	}
}
