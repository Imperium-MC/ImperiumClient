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

package io.github.axolotlclient.bridge.mixin

import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.mixin.MinecraftServerAccessor
import io.github.axolotlclient.modules.hypixel.autoboop.FilterListConfigurationScreen
import io.github.axolotlclient.modules.hypixel.bedwars.SessionStatsHudEntryConfigScreen
import io.github.axolotlclient.util.ThreadExecuter
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.FaviconTexture
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.server.network.EventLoopGroupHolder
import org.apache.commons.lang3.mutable.MutableInt
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Overwrite
import org.spongepowered.asm.mixin.Unique

@Mixin(PlatformDispatch.Companion::class, remap = false)
abstract class PlatformDispatchMixin {
	private companion object {
		// @Unique field names may not contain `$` in Kotlin; mixin only requires uniqueness.
		@Unique
		private val axoPinger = ServerStatusPinger()

		@Unique
		private fun getRealTimeServerPing(server: net.minecraft.client.multiplayer.ServerData, currentServerPing: MutableInt) {
			ThreadExecuter.scheduleTask(Runnable {
				try {
					axoPinger.pingServer(
						server,
						{},
						{ currentServerPing.setValue(server.ping) },
						EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport())
					)
				} catch (ignored: Exception) {
				}
			})
		}
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge.
	 */
	@Overwrite
	fun `pingHud$updatePing`(currentServerPing: MutableInt) {
		val minecraft = Minecraft.getInstance()
		if (minecraft.currentServer != null) {
			getRealTimeServerPing(minecraft.currentServer!!, currentServerPing)
		} else if (minecraft.hasSingleplayerServer()) {
			currentServerPing.setValue(1)
		}
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge.
	 */
	@Overwrite
	fun `ipHud$getServerIcon`(): AxoSprite.Dynamic? {
		val minecraft = Minecraft.getInstance()

		val icon = if (minecraft.currentServer == null) {
			FaviconTexture.forWorld(
				minecraft.textureManager,
				(minecraft.singleplayerServer as MinecraftServerAccessor).getStorageSource().levelId
			)
		} else {
			FaviconTexture.forServer(minecraft.textureManager, minecraft.currentServer!!.ip)
		}

		try {
			val img: NativeImage
			if (minecraft.currentServer == null) {
				val i = minecraft.singleplayerServer!!.status!!.favicon
				if (i.isEmpty) {
					return null
				} else {
					img = NativeImage.read(i.get()!!.iconBytes()!!)
				}
			} else {
				img = NativeImage.read(minecraft.currentServer!!.iconBytes!!)
			}
			icon.upload(img)
		} catch (e: Throwable) {
			icon.close()
			throw e
		}

		// Local class converted to an object expression; implements the same two interfaces.
		val impl = object : AxoSprite.Dynamic, AxoSpriteImpl {
			override fun draw(client: Minecraft, stack: GuiGraphicsExtractor, sX: Int, sY: Int, sW: Int, sH: Int, color: Int) {
				stack.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), sX, sY, 0f, 0f, sW, sH, sW, sH, color)
			}

			override fun close() {
				minecraft.execute(icon::close)
			}
		}

		return impl
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge.
	 */
	@Overwrite
	fun `autoBoop$openFiltersScreen`(filters: List<String>) {
		Minecraft.getInstance().gui.setScreen(FilterListConfigurationScreen(filters, Minecraft.getInstance().gui.screen()))
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge.
	 */
	@Overwrite
	fun `bedwars$sessionstats$openEntryConfigScreen`() {
		Minecraft.getInstance().gui.setScreen(SessionStatsHudEntryConfigScreen(Minecraft.getInstance().gui.screen()))
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge in preparation for sandboxing
	 */
	@Overwrite
	fun `api$login`(profileId: java.util.UUID, token: String, serverId: String?) {
		Minecraft.getInstance().services().sessionService().joinServer(profileId, token, serverId)
	}
}
