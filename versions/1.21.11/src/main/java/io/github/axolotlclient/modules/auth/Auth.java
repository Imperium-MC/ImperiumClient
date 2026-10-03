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

package io.github.axolotlclient.modules.auth;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.api.API;
import io.github.axolotlclient.api.util.UUIDHelper;
import io.github.axolotlclient.mixin.DownloadedPackSourceAccessor;
import io.github.axolotlclient.mixin.MinecraftClientAccessor;
import io.github.axolotlclient.mixin.ServerPackManagerAccessor;
import io.github.axolotlclient.mixin.SplashManagerAccessor;
import io.github.axolotlclient.modules.Module;
import io.github.axolotlclient.modules.auth.skin.SkinManager;
import io.github.axolotlclient.util.ThreadExecuter;
import io.github.axolotlclient.util.notifications.Notifications;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

public class Auth extends Accounts implements Module {

	@Getter
	private final static Auth Instance = new Auth();
	public final BooleanOption showButton = new BooleanOption("auth.showButton", false);
	public final BooleanOption skinManagerAnimations = new BooleanOption("skins.manage.animations", true);
	private final Minecraft mc = Minecraft.getInstance();
	private final GenericOption viewAccounts = new GenericOption("viewAccounts", "clickToOpen", () -> mc.setScreen(new AccountsScreen(mc.screen)));
	private final Set<String> loadingTexture = new HashSet<>();
	private final Map<String, Identifier> textures = new WeakHashMap<>();
	@Getter
	private final SkinManager skinManager = new SkinManager();

	@Override
	public void init() {
		load();
		this.msApi = new MSApi(this, () -> mc.options.languageCode);
		if (isContained(mc.getUser().getSessionId())) {
			current = getAccounts().stream().filter(account -> account.getUuid().equals(UUIDHelper.toUndashed(mc.getUser().getProfileId()))).toList().getFirst();
			if (current.needsRefresh()) {
				current.refresh(msApi).thenRun(this::save);
			}
		} else {
			current = new Account(mc.getUser().getName(), UUIDHelper.toUndashed(mc.getUser().getProfileId()), mc.getUser().getAccessToken());
		}

		category.add(showButton, viewAccounts, skinManagerAnimations);
		AxolotlClient.config().general.add(category);
	}

	@Override
	protected void login(Account account) {
		if (mc.level != null) {
			return;
		}

		if (account.needsRefresh() && !account.isOffline()) {
			if (account.isExpired()) {
				Notifications.getInstance().addStatus(Component.translatable("auth.notif.title"), Component.translatable("auth.notif.refreshing", account.getName()));
			}
			account.refresh(msApi).thenAccept(a -> {
				if (!a.isExpired()) {
					login(a);
				}
			}).thenRun(this::save);
		} else {
			try {
				API.getInstance().shutdown();
				var mcAccessor = (MinecraftClientAccessor) mc;
				mcAccessor.axolotlclient$setSession(new User(account.getName(), UUIDHelper.fromUndashed(account.getUuid()), account.getAuthToken(), Optional.empty(), Optional.empty()));
				YggdrasilAuthenticationService authService;
				if (account.isOffline()) {
					authService = YggdrasilAuthenticationService.createOffline(mc.getProxy());
				} else {
					authService = new YggdrasilAuthenticationService(mc.getProxy());
				}
				var service = authService.createUserApiService(mc.getUser().getAccessToken());
				mcAccessor.axolotlclient$setServices(Services.create(authService, mc.gameDirectory));
				mcAccessor.axolotlclient$setUserApiService(service);
				var sourceAccessor = (DownloadedPackSourceAccessor) mc.getDownloadedPackSource();
				((ServerPackManagerAccessor) sourceAccessor.axolotlclient$getManager())
					.axolotlclient$setDownloader(sourceAccessor.axolotlclient$createDownloader(sourceAccessor.axolotlcleint$getDownloadQueue(), mc::schedule, mc.getUser(), mc.getProxy()));
				mcAccessor.axolotlclient$setSocialInteractionsManager(new PlayerSocialManager(mc, service));
				mcAccessor.axolotlclient$setPlayerKeyPairManager(ProfileKeyPairManager.create(service, mc.getUser(), mc.gameDirectory.toPath()));
				mcAccessor.axolotlclient$setChatReportingContext(ReportingContext.create(ReportEnvironment.local(), service));
				mcAccessor.axolotlclient$setProfileFuture(CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.nonCriticalIoPool()));
				((SplashManagerAccessor) mc.getSplashManager()).setUser(mc.getUser());
				save();
				current = account;
				Notifications.getInstance().addStatus(Component.translatable("auth.notif.title"), Component.translatable("auth.notif.login.successful", current.getName()));
				API.getInstance().startup(account);
			} catch (Exception e) {
				Notifications.getInstance().addStatus(Component.translatable("auth.notif.title"), Component.translatable("auth.notif.login.failed"));
			}
		}
	}

	@Override
	CompletableFuture<Account> showAccountsExpiredScreen(Account account) {
		Screen current = mc.screen;
		var fut = new CompletableFuture<Account>();
		mc.execute(() -> mc.setScreen(new ConfirmScreen((bl) -> {
			if (bl) {
				msApi.startDeviceAuth().thenRun(() -> fut.complete(account));
			} else {
				fut.cancel(true);
			}
			mc.setScreen(current);
		}, Component.translatable("auth"), Component.translatable("auth.accountExpiredNotice", account.getName()))));
		return fut;
	}

	@Override
	void displayDeviceCode(DeviceFlowData data) {
		mc.execute(() -> mc.setScreen(new DeviceCodeDisplayScreen(mc.screen, data)));
	}

	private void loadTexture(String uuid) {
		if (!loadingTexture.contains(uuid)) {
			loadingTexture.add(uuid);
			ThreadExecuter.scheduleTask(() -> {
				UUID uUID = UUIDHelper.fromUndashed(uuid);
				ProfileResult profileResult = mc.services().sessionService().fetchProfile(uUID, false);
				if (profileResult != null) {
					mc.getSkinManager().get(profileResult.profile()).thenAccept(playerSkin -> playerSkin.ifPresent(skin -> textures.put(uuid, skin.body().texturePath())));
				}
				loadingTexture.remove(uuid);
			});
		}
	}

	public Identifier getSkinTexture(Account account) {
		return getSkinTexture(account.getUuid());
	}

	public Identifier getSkinTexture(io.github.axolotlclient.api.types.User user) {
		return getSkinTexture(user.getUuid());
	}

	public Identifier getSkinTexture(String uuid) {
		if (!textures.containsKey(uuid)) {
			loadTexture(uuid);
			return Objects.requireNonNullElseGet(textures.get(uuid), () -> DefaultPlayerSkin.get(UUIDHelper.fromUndashed(uuid)).body().texturePath());
		}
		return textures.get(uuid);
	}
}
