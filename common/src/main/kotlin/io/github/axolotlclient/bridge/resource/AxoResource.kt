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

package io.github.axolotlclient.bridge.resource

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl
import java.io.BufferedReader

interface AxoResource {
	// @Throws keeps the `throws IOException` of the Java original: the Java mixins override
	// these with throws clauses and module callers catch IOException.
	@Throws(java.io.IOException::class)
	@RequiresImpl
	fun `br$asStream`(): java.io.InputStream {
		throw BridgeUtil.noImpl()
	}

	@Throws(java.io.IOException::class)
	@RequiresImpl
	fun `br$asReader`(): BufferedReader {
		throw BridgeUtil.noImpl()
	}
}
