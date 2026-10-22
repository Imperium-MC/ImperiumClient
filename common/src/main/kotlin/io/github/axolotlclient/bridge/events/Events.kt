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

package io.github.axolotlclient.bridge.events

import io.github.axolotlclient.bridge.commands.Commands
import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.entity.AxoPlayer
import io.github.axolotlclient.bridge.events.types.PlayerDirectionChangeEvent
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent
import io.github.axolotlclient.bridge.events.types.ScoreboardRenderEvent
import io.github.axolotlclient.bridge.events.types.WorldLoadEvent
import io.github.axolotlclient.bridge.key.AxoKey
import io.github.axolotlclient.bridge.network.AxoServerData
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Events...
 */
object Events {
	@JvmField
	val PLAYER_ATTACK: EventBus<BiConsumer<AxoPlayer, AxoEntity>> = EventBus.broadcast2()

	@JvmField
	val PLAYER_HURT: EventBus<BiConsumer<AxoPlayer, AxoEntity?>> = EventBus.broadcast2()

	@JvmField
	val PLAYER_DIRECTION_CHANGE: EventBus<Consumer<PlayerDirectionChangeEvent>> = EventBus.broadcast1()

	@JvmField
	val UPDATE_TIME: EventBus<Consumer<Long>> = EventBus.broadcast1()

	@JvmField
	val KEY_INPUT: EventBus<Consumer<AxoKey>> = EventBus.broadcast1()

	@JvmField
	val CLIENT_START: EventBus<Runnable> = EventBus.broadcast0()

	@JvmField
	val CLIENT_READY: EventBus<Runnable> = EventBus.broadcast0()

	@JvmField
	val CLIENT_STOP: EventBus<Runnable> = EventBus.broadcast0()

	@JvmField
	val TICK: EventBus<Runnable> = EventBus.broadcast0()

	@JvmField
	val END_RESOURCE_RELOAD: EventBus<Runnable> = EventBus.broadcast0()

	// Nullable payload: ClientPlayNetworkHandler#getServerData() is @Nullable in vanilla and the
	// Java original passed it through unchecked.
	@JvmField
	val CONNECTION_PLAY_READY: EventBus<Consumer<AxoServerData?>> = EventBus.broadcast1()

	@JvmField
	val DISCONNECT: EventBus<Runnable> = EventBus.broadcast0()

	@JvmField
	val RECEIVE_CHAT_MESSAGE: EventBus<Consumer<ReceiveChatMessageEvent>> = EventBus.broadcast1()

	@JvmField
	val COMMAND_REGISTER: EventBus<Consumer<Commands>> = EventBus.broadcast1()

	@JvmField
	val SCOREBOARD_RENDER_EVENT: EventBus<Consumer<ScoreboardRenderEvent>> = EventBus.broadcast1()

	@JvmField
	val WORLD_LOAD_EVENT: EventBus<Consumer<WorldLoadEvent>> = EventBus.broadcast1()
}
