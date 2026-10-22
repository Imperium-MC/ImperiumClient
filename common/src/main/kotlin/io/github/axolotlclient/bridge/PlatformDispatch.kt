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

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl
import io.github.axolotlclient.bridge.render.AxoSprite
import org.apache.commons.lang3.mutable.MutableInt
import java.util.UUID

/**
 * Logic to dispatch to the platform implementation whenever it is not worth it to try and abstract things in a more
 * granular manner.
 *
 *
 * TODO: it may be more advisable to not route everything through a trampoline class, instead overwriting the callee
 */
class PlatformDispatch {
	companion object {
		@RequiresImpl
		@JvmStatic
		fun `pingHud$updatePing`(currentServerPing: MutableInt) {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun `ipHud$getServerIcon`(): AxoSprite.Dynamic? {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun `autoBoop$openFiltersScreen`(filters: List<String>) {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		@JvmStatic
		fun `bedwars$sessionstats$openEntryConfigScreen`() {
			throw BridgeUtil.noImpl()
		}

		// @Throws keeps the `throws Exception` of the Java original (Java callers catch it).
		// serverId is nullable because the Java original accepted the nullable digest output
		// MojangAuth passes through.
		@Throws(Exception::class)
		@RequiresImpl
		@JvmStatic
		fun `api$login`(profileId: UUID, token: String, serverId: String?) {
			throw BridgeUtil.noImpl()
		}
	}
}
