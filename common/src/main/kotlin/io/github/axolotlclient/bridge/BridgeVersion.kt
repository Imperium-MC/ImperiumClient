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

import io.github.axolotlclient.AxolotlClientCommon
import net.fabricmc.loader.api.FabricLoader

// The property keeps the lombok getter name getName() of the Java original (the field
// itself was called `name`, which Kotlin enums may not redeclare).
enum class BridgeVersion(@get:JvmName("getName") val versionName: String) {
	V1_8("1.8.9"),
	V1_20("1.20"),
	V1_21("1.21"),
	V1_21_11("1.21.11"),
	V26_1("26.1"),
	V26_2("26.2"),
	V26_3("26.3");

	fun isCurrent(): Boolean = this == version()

	companion object {
		// We can't use the standard mechanism of dispatching platform specific logic since the mixin plugin
		// will read the bridge version, which will cause re-entrance errors
		private val VERSION = valueOf(
			FabricLoader.getInstance()
				.getModContainer(AxolotlClientCommon.MODID)
				.orElseThrow()
				.metadata
				.getCustomValue("axolotlclient:bridge_impl_version")
				.asString
		)

		@JvmStatic
		fun version(): BridgeVersion = VERSION

		@JvmStatic
		fun is26OrLater(): Boolean = !version().versionName.startsWith("1.")
	}
}
