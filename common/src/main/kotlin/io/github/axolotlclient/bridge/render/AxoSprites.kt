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

package io.github.axolotlclient.bridge.render

import io.github.axolotlclient.bridge.internal.BridgeUtil

// Bridge constant holder: the throwing initializers live in this class's <clinit>, which
// the version-side mixin (AxoSpritesMixin) cancels at HEAD and then assigns real values.
class AxoSprites {
	companion object {
		@JvmField
		val BADGE: AxoSprite = BridgeUtil.noImplValue()

		@JvmField
		val BARRIER_ITEM_ICON: AxoSprite = BridgeUtil.noImplValue()

		@JvmField
		val FURNACE_OFF: AxoSprite = BridgeUtil.noImplValue()

		@JvmField
		val FURNACE_ON: AxoSprite = BridgeUtil.noImplValue()

		@JvmField
		val MAGNET_ICON: AxoSprite = BridgeUtil.noImplValue()
	}
}
