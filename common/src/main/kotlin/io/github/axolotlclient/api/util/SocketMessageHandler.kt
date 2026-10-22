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
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.bridge.util.AxoI18n

interface SocketMessageHandler {

    fun isApplicable(target: String): Boolean

    fun handle(response: Response) {
    }

    fun translate(key: String, vararg args: Any?): String {
        return AxoI18n.translate(key, *args)
    }

    fun notification(titleKey: String, descKey: String, vararg args: Any?) {
        API.getInstance().notificationProvider.addStatus(titleKey, descKey, *args)
    }
}
