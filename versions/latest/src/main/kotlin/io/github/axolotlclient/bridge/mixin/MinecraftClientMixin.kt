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

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import io.github.axolotlclient.bridge.AxoGameOptions
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.AxoSession
import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.render.AxoFont
import io.github.axolotlclient.bridge.resource.AxoResourceManager
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.bridge.world.AxoWorld
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.Gui
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.Collections
import java.util.Optional
import java.util.UUID

@Mixin(Minecraft::class)
abstract class MinecraftClientMixin : AxoMinecraftClient {
	@Shadow
	var player: LocalPlayer? = null

	@Shadow
	var level: ClientLevel? = null

	@Shadow
	@field:Final
	val font: Font? = null

	@Shadow
	@field:Final
	val options: net.minecraft.client.Options? = null

	@Shadow
	abstract fun getCurrentServer(): ServerData?

	@Shadow
	@field:Final
	val gui: Gui? = null

	@Shadow
	@field:Final
	private val user: User? = null

	@Shadow
	abstract fun getResourceManager(): ResourceManager

	@Shadow
	abstract fun getCameraEntity(): Entity?

	@Shadow
	abstract fun hasSingleplayerServer(): Boolean

	override fun `br$getPlayer`(): AxoPlayer? = player

	override fun `br$getWorld`(): AxoWorld? = level

	override fun `br$getFont`(): AxoFont = font!!

	override fun `br$getGameOptions`(): AxoGameOptions = options!!

	override fun `br$isLocalServer`(): Boolean = hasSingleplayerServer()

	override fun `br$getServerAddress`(): String? = getCurrentServer()?.ip

	override fun `br$getServerName`(): String? = getCurrentServer()?.name

	override fun `br$getOnlinePlayers`(): Collection<out AxoPlayerListEntry> = player!!.connection.onlinePlayers

	override fun `br$getOnlinePlayer`(uuid: UUID): Optional<AxoPlayerListEntry> =
		Optional.ofNullable(player!!.connection.getPlayerInfo(uuid))

	override fun `br$getOnlinePlayer`(name: String): Optional<AxoPlayerListEntry> =
		Optional.ofNullable(player!!.connection.getPlayerInfo(name))

	override fun `br$sendToClient`(msg: AxoText) {
		gui!!.hud.chat.addClientSystemMessage(msg as Component)
	}

	override fun `br$sendToServer`(msg: String) {
		if (msg.startsWith("/")) {
			player!!.connection.sendCommand(msg.substring(1))
		} else {
			player!!.connection.sendChat(msg)
		}
	}

	override fun `br$getSession`(): AxoSession {
		// TODO... -?
		return AxoSession(user!!.name, user!!.profileId.toString(), user!!.accessToken)
	}

	override fun `br$reinitScreen`() {
		if (gui!!.screen() != null) {
			val screen = gui!!.screen()
			screen!!.init(screen.width, screen.height)
		}
	}

	override fun `br$getResourceManager`(): AxoResourceManager = getResourceManager()

	override fun `br$getScreen`(): Any? = gui!!.screen()

	override fun `br$notifyLevelRenderer`() {
	}

	override fun `br$getCameraEntity`(): AxoEntity? = getCameraEntity()

	override fun `br$getSidebar`(): List<String> {
		val lines: MutableList<String> = ArrayList()
		val client = Minecraft.getInstance()
		val clientLevel = client.level ?: return lines

		val scoreboard: Scoreboard = clientLevel.scoreboard
		val sidebar: Objective? = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
		if (sidebar == null) {
			return lines
		}

		var scores: Collection<PlayerScoreEntry> = scoreboard.listPlayerScores(sidebar)
		val list = scores.stream()
			.filter { input: PlayerScoreEntry? -> input != null && !input!!.isHidden }
			.toList()

		scores = if (list.size > 15) {
			Lists.newArrayList(Iterables.skip(list, scores.size - 15))
		} else {
			list
		}

		for (score in scores) {
			val team: PlayerTeam? = scoreboard.getPlayerTeam(score.owner())
			if (team == null) {
				return lines
			}
			val text = team.playerPrefix.string + team.playerSuffix.string
			if (text.trim().isNotEmpty()) {
				lines.add(text)
			}
		}

		lines.add(sidebar.getDisplayName().string)
		lines.reverse()

		return lines
	}
}
