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

package io.github.axolotlclient.modules.screenshotUtils

import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.util.HorizontalGradientRectangleRenderState
import io.github.axolotlclient.util.MathUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

class LoadingImageScreen(
    private val parent: Screen?,
    private val future: CompletableFuture<Void>, private val freeOnClose: Boolean
) : Screen(Component.translatable("gallery.image.loading.title")) {
    private val loadStart = Util.getMillis().toFloat()

    override fun init() {
        val layout = HeaderAndFooterLayout(this)
        val header = layout.addToHeader(LinearLayout.vertical()).spacing(4)
        header.defaultCellSetting().alignHorizontallyCenter()
        header.addChild(StringWidget(title, font))

        val buttonWidth = 75
        val imageWidth = layout.width - 20 - buttonWidth - 4
        val imageHeight = layout.contentHeight

        val contents = layout.addToContents(LinearLayout.horizontal().spacing(4))
        val footer = layout.addToFooter(LinearLayout.horizontal().spacing(4))
        contents.addChild(LoadingWidget(imageWidth, imageHeight))
        val actions = contents.addChild(LinearLayout.vertical()).spacing(4)
        actions.addChild(LoadingWidget(buttonWidth, 20))
        actions.addChild(LoadingWidget(buttonWidth, 20))

        footer.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())

        layout.arrangeElements()
        layout.visitWidgets(this::addRenderableWidget)
    }

    override fun onClose() {
        if (freeOnClose) {
            future.cancel(false)
        }
        minecraft.gui.setScreen(parent)
    }

    private fun drawHorizontalGradient(guiGraphicsExtractor: GuiGraphicsExtractor, x1: Int, y1: Int, y2: Int, x2: Int) {
        HorizontalGradientRectangleRenderState.create(
            guiGraphicsExtractor, x1, y1, x2, y2, Companion.bgColor, Companion.accent
        ).submit()
    }

    private inner class LoadingWidget(width: Int, height: Int) : AbstractWidget(0, 0, width, height, Component.empty()) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
        ) {
            guiGraphicsExtractor.fill(x, y, right, bottom, bgColor)
            drawHorizontalGradient(
                guiGraphicsExtractor, x, y, bottom,
                MathUtil.lerp(
                    MathUtil.easeInOutCubic(((Util.getMillis() - loadStart) % 1000f / 1000f).toDouble()).toFloat(),
                    x, right
                )
            )
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        }
    }

    companion object {
        private val bgColor = Colors.DARK_GRAY.toInt()
        private val accent = Colors.GRAY.withBrightness(0.5f).withAlpha(128).toInt()
    }
}
