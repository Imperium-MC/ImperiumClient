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

package io.github.axolotlclient.mixin;

import java.util.concurrent.CompletableFuture;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {

	@Accessor("fps")
	static int axolotlclient$getCurrentFps() {
		return 0;
	}

	/**
	 * The vanilla right-click path (item use): runs the same hand loop and sends the same
	 * use packets a physical right-click would. Used by the server-gated auto-fish module
	 * to reel and recast.
	 */
	@Invoker("startUseItem")
	void axolotlclient$startUseItem();

	/**
	 * The vanilla left-click path (attack): miss-time, spectator and item guards, attack
	 * packet plus hand swing. Used by the server-gated auto-click module.
	 */
	@Invoker("startAttack")
	boolean axolotlclient$startAttack();

	@Accessor("user")
	@Mutable
	void axolotlclient$setSession(User session);

	@Accessor("playerSocialManager")
	@Mutable
	void axolotlclient$setSocialInteractionsManager(PlayerSocialManager manager);

	@Accessor("profileKeyPairManager")
	@Mutable
	void axolotlclient$setPlayerKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	@Mutable
	void axolotlclient$setChatReportingContext(ReportingContext context);

	@Accessor("userApiService")
	@Mutable
	void axolotlclient$setUserApiService(UserApiService service);

	@Accessor("profileFuture")
	@Mutable
	void axolotlclient$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Accessor("remoteFriendListUpdateHandler")
	@Mutable
	void axolotlclient$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler updateHandler);

	@Accessor("services")
	@Mutable
	void axolotlclient$setServices(Services services);

	@Accessor("offlineDeveloperMode")
	boolean axolotlclient$isOfflineDevMode();
}
