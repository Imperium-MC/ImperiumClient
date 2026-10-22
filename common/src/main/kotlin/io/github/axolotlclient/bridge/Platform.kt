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

import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.bridge.util.AxoText

interface Platform {
	companion object {
		@JvmStatic
		fun getMeasuringTimeMs(): Long = PlatformImplInternal.getMeasuringTimeMs()

		@JvmStatic
		fun createTexture(option: GraphicsOption): AxoSprite = PlatformImplInternal.createTexture(option)

		@JvmStatic
		fun tickCount(): Int = PlatformImplInternal.tickCount()

		@JvmStatic
		fun getTabNameFor(player: AxoPlayerListEntry): String = PlatformImplInternal.getTabNameFor(player)

		@JvmStatic
		fun setTabListHeader(text: AxoText) = PlatformImplInternal.setTabListHeader(text)
	}
}
