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

import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.net.http.WebSocket

import io.github.axolotlclient.util.ThreadExecuter

class ClientEndpoint : WebSocket.Listener {

    private val buf = StringBuilder()

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        buf.append(data)
        if (last) {
            val s = buf.toString()
            ThreadExecuter.scheduleTask(Runnable { API.getInstance().onMessage(s) })
            buf.setLength(0)
        }
        webSocket.request(1)
        return super.onText(webSocket, data, last)
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
        webSocket.request(1)
        API.getInstance().onClose(statusCode, reason)
        return super.onClose(webSocket, statusCode, reason)
    }

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
        API.getInstance().onOpen(webSocket)
    }

    override fun onError(webSocket: WebSocket?, error: Throwable) {
        API.getInstance().onError(error)
        super.onError(webSocket, error)
    }

    override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
        webSocket.request(1)
        return super.onPing(webSocket, message)
    }
}
