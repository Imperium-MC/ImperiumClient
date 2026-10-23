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

import java.net.URI

import io.github.axolotlclient.util.OSUtil
import io.github.axolotlclient.util.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class DeviceCodeDisplayScreen(private val parent: Screen?, data: DeviceFlowData) :
    Screen(Component.translatable("auth.add")) {

    private val verificationUri: String
    private val userCode: String
    private val message: List<FormattedCharSequence>
    private var ticksLeft: Int
    private var status: Component
    private var working = false
    private val qrCode: net.minecraft.resources.Identifier
    private val data: DeviceFlowData

    init {
        this.message = Minecraft.getInstance().font.split(Component.literal(data.message), 400)
        this.verificationUri = data.verificationUri
        this.userCode = data.userCode
        this.ticksLeft = data.expiresIn * 20
        this.status = Component.translatable(
            "auth.time_left", ((ticksLeft / 20) / 60).toString() + "m" + ((ticksLeft / 20) % 60) + "s"
        )
        this.qrCode = Util.getTexture(data.qrCode, "device_auth_" + data.userCode)
        data.statusConsumer = DeviceFlowData.StatusConsumer { s: String ->
            if (s == "auth.finished") {
                minecraft!!.execute(Runnable { minecraft!!.gui.setScreen(parent) })
            }
            working = true
            clearWidgets()
            status = Component.translatable(s)
        }
        this.data = data
    }

    override fun removed() {
        data.cancel()
    }

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.translatable("auth.copy_and_open")) {
                minecraft!!.keyboardHandler.setClipboard(userCode)
                OSUtil.getOS().open(URI.create(verificationUri))
            }.bounds(width / 2 - 100, height / 2, 200, 20).build()
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        graphics.centeredText(font, title, width / 2, 25, -1)

        var y = height / 4
        for (orderedText in message) {
            graphics.centeredText(font, orderedText, width / 2, y, -1)
            y += 10
        }
        graphics.centeredText(
            font,
            if (working) status else Component.translatable(
                "auth.time_left", ((ticksLeft / 20) / 60).toString() + "m" + ((ticksLeft / 20) % 60) + "s"
            ),
            width / 2, y + 10, -1
        )

        y = height / 2 + 30
        if (height - y > 40) {
            val qrImageSize = height - y - 20
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, qrCode, width / 2 - qrImageSize / 2, y, 0f, 0f,
                qrImageSize, qrImageSize, qrImageSize, qrImageSize
            )
        }
    }

    override fun tick() {
        ticksLeft--
    }
}
