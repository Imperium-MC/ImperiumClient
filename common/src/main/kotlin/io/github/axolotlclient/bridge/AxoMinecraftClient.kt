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

package io.github.axolotlclient.bridge

import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.render.AxoFont
import io.github.axolotlclient.bridge.resource.AxoResourceManager
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.bridge.world.AxoWorld
import org.jetbrains.annotations.Contract
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executor

interface AxoMinecraftClient : Executor {
	@RequiresImpl
	@Contract(pure = true)
	fun `br$getPlayer`(): AxoPlayer? {
		throw BridgeUtil.noImpl()
	}

	// Nullable: the latest impl returns the client level field, null when not in a world.
	@RequiresImpl
	fun `br$getWorld`(): AxoWorld? {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getFont`(): AxoFont {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getGameOptions`(): AxoGameOptions {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getSession`(): AxoSession {
		throw BridgeUtil.noImpl()
	}

	// Nullable: the latest impl maps Optional.ofNullable(getCurrentServer()).map(...).orElse(null).
	@RequiresImpl
	fun `br$getServerAddress`(): String? {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getServerName`(): String? {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$isLocalServer`(): Boolean {
		throw BridgeUtil.noImpl()
	}

	// @JvmWildcard keeps the Java-generic signature Collection<? extends AxoPlayerListEntry>
	// the mixins override with (bare `out` projections are dropped from JVM signatures in
	// return position).
	@RequiresImpl
	fun `br$getOnlinePlayers`(): Collection<@JvmWildcard AxoPlayerListEntry> {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getOnlinePlayer`(uuid: UUID): Optional<AxoPlayerListEntry> {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getOnlinePlayer`(name: String): Optional<AxoPlayerListEntry> {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Sends a message *as* the client to the server.
	 *
	 * @param msg the message
	 */
	@RequiresImpl
	fun `br$sendToServer`(msg: String) {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Sends a message to the client.
	 *
	 * @param msg the message
	 */
	@RequiresImpl
	fun `br$sendToClient`(msg: AxoText) {
		throw BridgeUtil.noImpl()
	}

	/**
	 * Re-initializes the current screen
	 */
	@RequiresImpl
	fun `br$reinitScreen`() {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getResourceManager`(): AxoResourceManager {
		throw BridgeUtil.noImpl()
	}

	/*
	 * Return type is an opaque object because we do not have a gui bridge currently.
	 */
	// Nullable: the latest impl returns the current screen or null when none is open.
	@RequiresImpl
	fun `br$getScreen`(): Any? {
		throw BridgeUtil.noImpl()
	}

	// Nullable: Minecraft#getCameraEntity() is null before a camera entity exists.
	@RequiresImpl
	fun `br$getCameraEntity`(): AxoEntity? {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$notifyLevelRenderer`() {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getSidebar`(): List<String> {
		throw BridgeUtil.noImpl()
	}

	companion object {
		@JvmStatic
		fun getInstance(): AxoMinecraftClient = PlatformImplInternal.getMinecraftClientInstance()

		@JvmStatic
		fun getCurrentFps(): Int = PlatformImplInternal.getCurrentFps()
	}
}
