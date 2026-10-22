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

import io.github.axolotlclient.api.requests.GlobalDataRequest
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractTextAreaWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class NewsScreen(private val parent: Screen?) : Screen(Component.translatable("api.notes.title")) {

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        graphics.centeredText(font, title, width / 2, 20, -1)
    }

    override fun init() {
        GlobalDataRequest.get().thenAccept { data ->
            addRenderableWidget(
                NewsWidget(
                    25, 35, width - 50, height - 100,
                    Component.literal(data.notes.trim().replace(Regex("([^\\n])\\n([^\\n])"), "$1 $2"))
                )
            )
        }
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { minecraft.gui.setScreen(parent) }
                .bounds(width / 2 - 100, height - 45, 200, 20).build()
        )
    }

    private inner class NewsWidget(x: Int, y: Int, width: Int, height: Int, component: Component) :
        AbstractTextAreaWidget(x, y, width, height, component, AbstractScrollArea.defaultSettings(9), false, false) {

        private val lines: List<FormattedCharSequence>
        private val contentHeight: Int

        init {
            lines = font.split(message, getWidth() - 4)
            contentHeight = lines.size * font.lineHeight
        }

        override fun getInnerHeight(): Int {
            return contentHeight
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            var y = getY() + 2
            for (chsq in lines) {
                graphics.text(font, chsq, getX() + 2, y, -1)
                y += font.lineHeight
            }
        }

        override fun scrollRate(): Double {
            return font.lineHeight.toDouble()
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, message)
        }
    }
}
