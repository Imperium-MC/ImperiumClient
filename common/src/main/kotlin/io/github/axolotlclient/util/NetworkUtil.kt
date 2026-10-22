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

package io.github.axolotlclient.util

import com.github.mizosoft.methanol.Methanol
import io.github.axolotlclient.AxolotlClientCommon
import java.net.http.HttpClient
import java.time.Duration

object NetworkUtil {
    private const val UA_PATTERN = "AxolotlClient/%s Minecraft/%s"

    @JvmStatic
    fun getUserAgent(): String = getUserAgent(false)

    @JvmStatic
    fun getUserAgent(includeContact: Boolean): String {
        val ua = UA_PATTERN.format(AxolotlClientCommon.VERSION, AxolotlClientCommon.GAME_VERSION)
        if (includeContact) {
            return "$ua contact: moehreag<at>gmail.com"
        }
        return ua
    }

    @JvmStatic
    fun createHttpClient(): HttpClient = createHttpClient(false)

    @JvmStatic
    fun createHttpClient(includeContact: Boolean): HttpClient =
        Methanol.newBuilder()
            .userAgent(getUserAgent(includeContact))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .requestTimeout(Duration.ofMinutes(1))
            .executor(ThreadExecuter.service())
            .build()
}
