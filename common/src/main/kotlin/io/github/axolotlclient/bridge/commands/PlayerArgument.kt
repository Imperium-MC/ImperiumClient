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

package io.github.axolotlclient.bridge.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import java.util.Locale
import java.util.Objects
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class PlayerArgument : ArgumentType<PlayerArgument.PlayerInfo> {
	override fun parse(stringReader: StringReader): PlayerInfo = PlayerInfo(stringReader.readUnquotedString())

	override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
		val remaining = builder.remaining.lowercase(Locale.ROOT)
		AxoMinecraftClient.getInstance().`br$getOnlinePlayers`().stream()
			.map { obj: AxoPlayerListEntry -> obj.`br$getName`() }
			.filter { name: String -> NAME_REGEX.matcher(name).matches() }
			.filter { name: String -> name.lowercase(Locale.ROOT).startsWith(remaining) }
			.forEach { name: String -> builder.suggest(name) }

		return builder.buildFuture()
	}

	// @get:JvmName keeps the record-style playerName() accessor of the Java original.
	class PlayerInfo(@get:JvmName("playerName") val playerName: String) {
		private var uuid: CompletableFuture<Optional<String>>? = null

		fun uuid(): CompletableFuture<Optional<String>> {
			if (uuid == null) {
				uuid = UUIDHelper.USERNAME_TO_UUID.getAsync(playerName)
			}

			return uuid!!
		}

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is PlayerInfo) return false
			return playerName == other.playerName && uuid == other.uuid
		}

		override fun hashCode(): Int = Objects.hash(playerName, uuid)

		override fun toString(): String =
			"PlayerInfo[playerName=$playerName, uuid=$uuid]"
	}

	companion object {
		private val NAME_REGEX = Pattern.compile("[a-zA-Z0-9_]{2,16}")

		@JvmStatic
		fun get(context: CommandContext<*>, name: String): PlayerInfo = context.getArgument(name, PlayerInfo::class.java)

		@JvmStatic
		fun player(): PlayerArgument = PlayerArgument()
	}
}
