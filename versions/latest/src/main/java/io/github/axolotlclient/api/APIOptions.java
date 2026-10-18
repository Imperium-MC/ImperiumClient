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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.ui.screen.ConfigScreen;
import io.github.axolotlclient.api.chat.ChannelInvitesScreen;
import io.github.axolotlclient.api.chat.ChatListScreen;
import io.github.axolotlclient.api.requests.AccountDataRequest;
import io.github.axolotlclient.api.requests.AccountSettingsRequest;
import io.github.axolotlclient.util.ThreadExecuter;
import io.github.axolotlclient.util.dialogs.DialogProvider;
import io.github.axolotlclient.util.keybinds.KeyBinds;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class APIOptions extends Options {

	@Getter
	private static final Options Instance = new APIOptions();

	@Override
	public void init() {
		super.init();
		Minecraft client = Minecraft.getInstance();

		openPrivacyNoteScreen = () -> {
			var fut = new CompletableFuture<Boolean>();
			var parent = client.gui.screen();
			client.execute(() -> client.gui.setScreen(new PrivacyNoticeScreen(parent, fut)));
			return fut;
		};
		KeyBinds.getInstance().registerWithSimpleAction(
			new KeyMapping("api.chats.sidebar.open", InputConstants.KEY_O, KeyBinds.CATEGORY_AXOLOTLCLIENT), () -> {
				if (API.getInstance().isAuthenticated()) {
					var parent = client.gui.screen();
					client.execute(() -> client.gui.setScreen(new ChatsSidebar(parent)));
				}
			});
		category.add(
			new GenericOption("viewFriends", "clickToOpen", () -> {
				var parent = client.gui.screen();
				client.execute(() -> client.gui.setScreen(new FriendsScreen(parent)));
			}));
		category.add(
			new GenericOption("viewChats", "clickToOpen", () -> {
				var parent = client.gui.screen();
				client.execute(() -> client.gui.setScreen(new ChatListScreen(parent)));
			}));
		category.add(new GenericOption("api.channels.invites.view", "clickToOpen", () -> {
			var parent = client.gui.screen();
			client.execute(() -> client.gui.setScreen(new ChannelInvitesScreen(parent)));
		}));
		account.add(new GenericOption("api.account.usernames", "clickToOpen",
			() -> {
				var parent = client.gui.screen();
				client.execute(() -> client.gui.setScreen(new UsernameManagementScreen(parent)));
			}
		));
		account.add(new GenericOption("api.account.export", "api.account.export_data", () -> ThreadExecuter.scheduleTask(() -> {
			if (!API.getInstance().isAuthenticated()) {
				API.getInstance().getNotificationProvider().addStatus("api.account.export.failure.title", "api.error.unauthenticated");
				return;
			}
			DialogProvider.IMPL.save("Choose export destination", FabricLoader.getInstance().getGameDir(), ".json")
				.thenAccept(opt -> opt.ifPresent(AccountDataRequest::get));
		})));
		account.add(new GenericOption("api.account.delete", "api.account.delete_account", () -> {
			Screen previous = client.gui.screen();
			client.gui.setScreen(new ConfirmScreen(b -> {
				if (b) {
					if (!API.getInstance().isAuthenticated()) {
						API.getInstance().getNotificationProvider().addStatus("api.account.deletion.failure", "api.error.unauthenticated");
						return;
					} else {
						AccountSettingsRequest.deleteAccount().thenAccept(r -> {
							if (r) {
								API.getInstance().getNotificationProvider()
									.addStatus("api.account.deletion.success", "api.account.deletion.success.desc");
							} else {
								API.getInstance().getNotificationProvider()
									.addStatus("api.account.deletion.failure", "api.account.deletion.failure.desc");
							}
							enabled.set(false);
						});
					}
				}
				client.gui.setScreen(previous);
			}, Component.translatable("api.account.confirm_deletion"),
				Component.translatable("api.account.confirm_deletion.desc")
			));
		}));
		Consumer<Boolean> consumer = settingUpdated;
		settingUpdated = b -> {
			if (client.gui.screen() instanceof ConfigScreen) {
				consumer.accept(b);
			}
		};
		if (Constants.ENABLED) {
			AxolotlClient.config().addCategory(category);
		}
	}
}
