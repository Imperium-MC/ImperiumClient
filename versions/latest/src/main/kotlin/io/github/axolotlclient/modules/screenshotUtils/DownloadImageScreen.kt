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

import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.Layout
import net.minecraft.client.gui.layouts.LayoutElement
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class DownloadImageScreen(private val parent: Screen) : Screen(Component.translatable("viewScreenshot")) {
    override fun init() {
        val hFL = ImprovedHeaderAndFooterLayout(this)

        hFL.addTitleHeader(title, font)
        val urlBox = EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.translatable("pasteURL"))
        urlBox.setSuggestion(I18n.get("pasteURL"))
        urlBox.setResponder { s: String ->
            if (s.isEmpty()) {
                urlBox.setSuggestion(I18n.get("pasteURL"))
            } else {
                urlBox.setSuggestion("")
            }
        }
        urlBox.setMaxLength(63)
        val linear = hFL.addToContents(LinearLayout.horizontal().spacing(4))
        linear.defaultCellSetting().alignVerticallyMiddle()
        linear.addChild(urlBox)
        linear.addChild(
            SpriteIconButton.builder(Component.translatable("download"), {
                val url = urlBox.value.trim()
                if (url.isEmpty()) {
                    return@builder
                }
                minecraft.gui.setScreen(
                    ImageScreen.create(this, ImageShare.getInstance().downloadImage(url), true)
                )
            }, true)
                .sprite(SPRITE, 20, 20)
                .width(20).build()
        ).setPosition(width / 2 + 100 + 4, height / 2 - 10)

        hFL.addToFooter(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())

        hFL.arrangeElements()
        hFL.visitWidgets(this::addRenderableWidget)
        setInitialFocus(urlBox)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    class ImprovedHeaderAndFooterLayout(private val screen: Screen) : Layout {
        var headerHeight: Int
        var footerHeight: Int

        private val headerFrame = FrameLayout()
        private val footerFrame = FrameLayout()
        private val contentsFrame = FrameLayout()

        constructor(screen: Screen, height: Int) : this(screen, height, height)

        constructor(screen: Screen, headerHeight: Int, footerHeight: Int) : this(screen) {
            this.headerHeight = headerHeight
            this.footerHeight = footerHeight
        }

        init {
            headerHeight = DEFAULT_HEADER_AND_FOOTER_HEIGHT
            footerHeight = DEFAULT_HEADER_AND_FOOTER_HEIGHT
            this.headerFrame.defaultChildLayoutSetting().align(0.5f, 0.5f)
            this.footerFrame.defaultChildLayoutSetting().align(0.5f, 0.5f)
        }

        override fun setX(x: Int) {
        }

        override fun setY(y: Int) {
        }

        override fun getX(): Int = 0

        override fun getY(): Int = 0

        override fun getWidth(): Int = this.screen.width

        override fun getHeight(): Int = this.screen.height

        fun getContentHeight(): Int = this.screen.height - this.headerHeight - this.footerHeight

        override fun visitChildren(visitor: Consumer<LayoutElement>) {
            this.headerFrame.visitChildren(visitor)
            this.contentsFrame.visitChildren(visitor)
            this.footerFrame.visitChildren(visitor)
        }

        override fun arrangeElements() {
            val i = this.headerHeight
            val j = this.footerHeight
            this.headerFrame.setMinWidth(this.screen.width)
            this.headerFrame.setMinHeight(i)
            this.headerFrame.setPosition(0, 0)
            this.headerFrame.arrangeElements()
            this.footerFrame.setMinWidth(this.screen.width)
            this.footerFrame.setMinHeight(j)
            this.footerFrame.arrangeElements()
            this.footerFrame.setY(this.screen.height - j)
            contentsFrame.setMinHeight(getContentHeight()) // This line makes it possible to center things vertically in this layout. Thanks mojang.
            this.contentsFrame.setMinWidth(this.screen.width)
            this.contentsFrame.arrangeElements()
            val k = i + CONTENT_MARGIN_TOP
            val l = this.screen.height - j - this.contentsFrame.height
            this.contentsFrame.setPosition(0, minOf(k, l))
        }

        fun addTitleHeader(message: Component, font: Font) {
            this.headerFrame.addChild(StringWidget(message, font))
        }

        fun <T : LayoutElement> addToFooter(child: T): T = this.footerFrame.addChild(child)

        fun <T : LayoutElement> addToContents(child: T): T = this.contentsFrame.addChild(child)

        override fun removeChildren() {
            headerFrame.removeChildren()
            contentsFrame.removeChildren()
            footerFrame.removeChildren()
        }

        companion object {
            const val DEFAULT_HEADER_AND_FOOTER_HEIGHT = 33
            private const val CONTENT_MARGIN_TOP = 30
        }
    }

    companion object {
        private val SPRITE = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "go")
    }
}
