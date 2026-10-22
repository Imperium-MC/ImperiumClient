/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.auth.skin

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.modules.auth.Account
import io.github.axolotlclient.modules.auth.MSApi

interface Asset {
    fun image(): ByteArray

    fun active(): Boolean

    fun equip(api: MSApi, account: Account): CompletableFuture<MSApi.MCProfile>

    fun sha256(): String

    interface Local : Asset {
        fun file(): Path
    }

    interface Online : Asset {
        fun url(): String

        fun supportsDownload(): Boolean = false
    }
}
