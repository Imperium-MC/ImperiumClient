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

package io.github.axolotlclient.modules.auth

import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.api.util.Graphics
import io.github.axolotlclient.AxolotlClientConfig.impl.util.GraphicsImpl
import io.nayuki.qrcodegen.QrCode

class DeviceFlowData(
    val message: String,
    val verificationUri: String,
    val deviceCode: String,
    val userCode: String,
    val expiresIn: Int,
    val interval: Int
) {
    var statusConsumer: StatusConsumer? = null
    var cancelled: Boolean = false
        private set

    fun setStatus(status: String) {
        statusConsumer?.emit(status)
    }

    val qrCode: Graphics
        get() {
            val qr = QrCode.encodeText("$verificationUri?code=$userCode", QrCode.Ecc.MEDIUM)
            val size = qr.size
            val borderWidth = 1
            val gr = GraphicsImpl(size + borderWidth * 2, size + borderWidth * 2)
            for (x in 0 until gr.getWidth()) {
                for (y in 0 until gr.getHeight()) {
                    gr.setPixelColor(x, y, if (qr.getModule(x - borderWidth, y - borderWidth)) Colors.BLACK else Colors.WHITE)
                }
            }
            return gr
        }

    fun cancel() {
        cancelled = true
    }

    fun interface StatusConsumer {
        fun emit(status: String)
    }
}
