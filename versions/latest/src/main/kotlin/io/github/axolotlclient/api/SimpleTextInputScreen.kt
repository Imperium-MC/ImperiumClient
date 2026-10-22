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

import java.util.function.Consumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

open class SimpleTextInputScreen(
    private val parent: Screen?,
    title: Component,
    private val inputLabel: Component,
    private val consumer: Consumer<String>
) : Screen(title) {

    private var input: EditBox? = null

    override fun init() {
        val input = EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, inputLabel)
        this.input = input
        addRenderableWidget(input)

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { minecraft.gui.setScreen(parent) }
                .bounds(width / 2 - 155, height - 50, 150, 20).build()
        )
        val done = addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) {
                if (!input.value.isBlank()) {
                    consumer.accept(input.value)
                }
                minecraft.gui.setScreen(parent)
            }.bounds(width / 2 + 5, height - 50, 150, 20).build()
        )
        input.setResponder { s -> done.active = !s.isBlank() }
        done.active = false
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
        super.extractRenderState(graphics, i, j, f)
        graphics.text(font, inputLabel, (width / 2f - 100).toInt(), (height / 2f - 20).toInt(), -1)
        graphics.centeredText(font, this.title, this.width / 2, 20, -1)
    }
}
