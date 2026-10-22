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

package io.github.axolotlclient.modules.chat

import io.github.axolotlclient.AxolotlClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * The small camera button on the chat screen. The icon is drawn vector-style with plain
 * fill rectangles — a camera body with a viewfinder hump and a lens ring.
 */
class ChatScreenshotButton(x: Int, y: Int, size: Int) :
    AbstractWidget(x, y, size, size, Component.translatable("chat_screenshot_button")) {
    init {
        setTooltip(Tooltip.create(Component.translatable("chat_screenshot_button")))
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hover = isHovered
        val size: Int = width
        val left: Int = getX()
        val top: Int = getY()

        // Backdrop plate.
        graphics.fill(left, top, left + size, top + size, (if (hover) BACKDROP_HOVER else BACKDROP).toInt())

        // Vector camera, sized relative to the button so a 12px and a 20px button both work.
        val unit = max(1, size / 6)
        val iconColor = if (hover) ICON_HOVER else ICON
        val bodyLeft = left + unit
        val bodyTop = top + 2 * unit
        val bodyRight = left + size - unit
        val bodyBottom = top + size - unit

        // Body.
        graphics.fill(bodyLeft, bodyTop, bodyRight, bodyBottom, iconColor)
        // Viewfinder hump on top of the body.
        graphics.fill(left + 2 * unit, top + unit, left + size - 2 * unit, bodyTop.toInt(), iconColor)
        // Lens: a dark ring with the body color left in its center.
        val lensLeft = left + size / 2 - unit
        val lensRight = left + size / 2 + 2 * unit
        val lensTop = top + size / 2 - unit
        val lensBottom = top + size / 2 + 2 * unit
        graphics.fill(lensLeft, lensTop, lensRight, lensBottom, ICON_DARK)
        graphics.fill(
            lensLeft + unit / 2 + unit % 2, lensTop + unit / 2 + unit % 2,
            lensRight - unit / 2 - unit % 2, lensBottom - unit / 2 - unit % 2, iconColor
        )
    }

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        if (AxolotlClient.config().chatScreenshotButton.get()) {
            ChatScreenshot.capture(Minecraft.getInstance())
        }
        // the down-click sound is played by AbstractWidget.mouseClicked already
    }

    override fun isFocused(): Boolean = false // the chat input must never lose focus to this button

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? =
        null // not reachable by keyboard navigation

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    companion object {
        /** Icon/backdrop colors (ARGB). */
        private const val BACKDROP = 0x66000000
        private const val BACKDROP_HOVER = 0x99000000
        private const val ICON = 0xFFE0E0E0.toInt()
        private const val ICON_HOVER = 0xFFFFFFFF.toInt()
        private const val ICON_DARK = 0xFF303030.toInt()
    }
}
