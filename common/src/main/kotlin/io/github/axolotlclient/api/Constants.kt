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
package io.github.axolotlclient.api

object Constants {
    // Completely disable these features and hide their existence
    @JvmField
    val ENABLED = true

    private const val DEVELOPMENT = false

    @JvmField
    val API_URL: String = if (DEVELOPMENT) "http://localhost:8000" else "https://api.axolotlclient.com/v1/"

    @JvmField
    val TERMS = "https://axolotlclient.com/terms"

    // The Delay between Status updates, in seconds. Discord uses 15 seconds so we will as well.
    @JvmField
    val STATUS_UPDATE_DELAY = 15

    // When set to true, no requests will be sent
    @JvmField
    val TESTING = false
}
