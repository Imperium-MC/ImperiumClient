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

package io.github.axolotlclient.bridge.key

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.internal.RequiresImpl

/**
 * An abstract representation of a keybind
 */
interface AxoKeybinding {
	@RequiresImpl
	fun `br$registerOnClicked`(runnable: Runnable) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$registerOnReleased`(runnable: Runnable) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$registerOnConsumeClick`(runnable: Runnable) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getBoundKey`(): AxoKey {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$isPressed`(): Boolean {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$consumeClick`(): Boolean {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$click`() {
		throw BridgeUtil.noImpl()
	}

	companion object {
		@JvmStatic
		fun create(defaultKey: AxoKey?, name: String): AxoKeybinding =
			PlatformImplInternal.createKeyBinding(defaultKey, name)
	}
}
