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

package io.github.axolotlclient.bridge.entity.effect

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl

// Bridge constant holder: the throwing initializers live in this class's <clinit>, which
// the version-side mixin (AxoStatusEffectsMixin) cancels at HEAD and then assigns real values.
@Suppress("unused")
class AxoStatusEffects {
	companion object {
		@RequiresImpl
		@JvmField
		val REGEN: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val HASTE: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val SPEED: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val JUMP_BOOST: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val BLINDNESS: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val MINING_FATIGUE: AxoStatusEffect = BridgeUtil.noImplValue()

		@RequiresImpl
		@JvmField
		val HEALTH_BOOST: AxoStatusEffect = BridgeUtil.noImplValue()
	}
}
