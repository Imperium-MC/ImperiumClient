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

import java.util.UUID

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class AddOfflineScreen(private val parent: Screen?) : Screen(Component.translatable("auth.add.offline")) {

    private var nameInput: EditBox? = null

    override fun extractRenderState(graphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
        super.extractRenderState(graphics, i, j, f)
        graphics.text(
            font, Component.translatable("auth.add.offline.name"), (width / 2f - 100).toInt(),
            (height / 2f - 20).toInt(), -1
        )
        graphics.centeredText(this.font, this.title, this.width / 2, 20, -1)
    }

    override fun init() {
        val input = EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.empty())
        nameInput = input
        addRenderableWidget(input)

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { minecraft.gui.setScreen(parent) }
                .bounds(width / 2 - 155, height - 50, 150, 20).build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) {
                Auth.getInstance()
                    .addAccount(Account(nameInput!!.value, UUID.randomUUID().toString(), Account.OFFLINE_TOKEN))
                minecraft.gui.setScreen(parent)
            }.bounds(width / 2 + 5, height - 50, 150, 20).build()
        )
    }
}
