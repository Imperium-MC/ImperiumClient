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

package io.github.axolotlclient.bridge.internal

import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffect
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.key.AxoKey
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.bridge.scores.AxoTeam
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoProfiler
import io.github.axolotlclient.bridge.util.AxoText
import org.jetbrains.annotations.ApiStatus

/**
 * Internal implementations for static platform methods.
 */
@Suppress("unused")
@ApiStatus.Internal
class PlatformImplInternal {
	companion object {
		@RequiresImpl
		@JvmStatic
		fun getWindow(): AxoWindow? {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getMinecraftClientInstance(): AxoMinecraftClient {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getProfiler(): AxoProfiler {
			throw BridgeUtil.noImpl()
		}

		// Array<out Any?> matches the Java `Object[] args` descriptor while accepting the
		// out-projected vararg arrays the AxoI18n companion passes.
		@RequiresImpl
		@JvmStatic
		fun getTranslatedString(nameKey: String, args: Array<out Any?>): String {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getMeasuringTimeMs(): Long {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getCurrentFps(): Int {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun tickCount(): Int {
			throw BridgeUtil.noImpl()
		}

		// constructors
		@RequiresImpl
		@JvmStatic
		fun createItemStack(item: AxoItem, count: Int): AxoItemStack {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun createIdentifier(ns: String, path: String): AxoIdentifier {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun parseIdentifier(id: String): AxoIdentifier {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun createKeyBinding(defaultKey: AxoKey?, name: String): AxoKeybinding {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun createStatusEffectInstance(effect: AxoStatusEffect, duration: Int): AxoStatusEffectInstance {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun createTexture(option: GraphicsOption): AxoSprite {
			throw BridgeUtil.noImpl()
		}

		@JvmStatic
		fun createEmpty(): AxoText.Mutable = createLiteral("")

		@RequiresImpl
		@JvmStatic
		fun createLiteral(text: String): AxoText.Mutable {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun createTranslatable(key: String, vararg args: Any?): AxoText.Mutable {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun stripText(text: String): String {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getTabNameFor(player: AxoPlayerListEntry): String {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun setTabListHeader(text: AxoText) {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun getTeamMemberDisplayName(team: AxoTeam, s: String): String {
			throw BridgeUtil.noImpl()
		}
	}
}
