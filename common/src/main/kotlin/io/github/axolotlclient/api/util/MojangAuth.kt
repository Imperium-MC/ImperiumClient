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

package io.github.axolotlclient.api.util

import io.github.axolotlclient.api.API
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.modules.auth.Account
import org.apache.commons.lang3.RandomStringUtils
import java.math.BigInteger
import java.security.MessageDigest

object MojangAuth {

    @JvmStatic
    fun authenticate(account: Account): String? {
        try {
            val serverId = minecraftSha1(RandomStringUtils.random(40).toByteArray(Charsets.UTF_8))
            PlatformDispatch.`api$login`(UUIDHelper.fromUndashed(account.uuid), account.authToken, serverId)
            return serverId
        } catch (e: Exception) {
            API.getInstance().logDetailed("MojangAuth Exception: ", e)
        }
        return null
    }

    private fun minecraftSha1(vararg bytes: ByteArray): String? {
        var length = 0
        for (arr in bytes) {
            length += arr.size
        }
        val data = ByteArray(length)

        var index = 0

        for (arr in bytes) {
            val size = arr.size
            System.arraycopy(arr, 0, data, index, size)
            index += size
        }

        return try {
            BigInteger(MessageDigest.getInstance("SHA-1").digest(data)).toString(16)
        } catch (ignored: Exception) {
            null
        }
    }
}
