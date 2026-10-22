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

package io.github.axolotlclient.util.notifications

import com.google.common.collect.ImmutableList
import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max

class AxolotlClientToast private constructor(
    private val title: Component,
    private val messageLines: List<FormattedCharSequence>,
    private val width: Int
) : Toast {

    private var wantedVisibility: Toast.Visibility = Toast.Visibility.HIDE

    constructor(title: Component, message: Component?) : this(
        title,
        nullToEmpty(message),
        max(
            Toast.DEFAULT_WIDTH,
            2 * MARGIN + 15 + max(
                Minecraft.getInstance().font.width(title),
                if (message == null) 0 else Minecraft.getInstance().font.width(message)
            )
        )
    )

    override fun width(): Int {
        return this.width
    }

    override fun height(): Int {
        return 2 * MARGIN + max(this.messageLines.size, 1) * LINE_SPACING
    }

    override fun getWantedVisibility(): Toast.Visibility {
        return this.wantedVisibility
    }

    override fun update(toastManager: ToastManager, visibilityTime: Long) {
        val time = DISPLAY_TIME_MILLIS * toastManager.notificationDisplayTimeMultiplier
        this.wantedVisibility = if (visibilityTime < time) Toast.Visibility.SHOW else Toast.Visibility.HIDE
    }

    override fun extractRenderState(guiGraphicsExtractor: GuiGraphicsExtractor, font: Font, visibilityTime: Long) {
        guiGraphicsExtractor.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height())
        guiGraphicsExtractor.blit(
            RenderPipelines.GUI_TEXTURED, AxolotlClientCommon.BADGE_PATH as Identifier, 4, 4, 0f, 0f, 15, 15, 15, 15
        )
        val textOffset = 22
        if (this.messageLines.isEmpty()) {
            guiGraphicsExtractor.text(font, this.title, textOffset, LINE_SPACING, -256, false)
        } else {
            guiGraphicsExtractor.text(font, this.title, textOffset, 7, -256, false)

            for (i in this.messageLines.indices) {
                guiGraphicsExtractor.text(font, this.messageLines[i], textOffset, 18 + i * LINE_SPACING, -1, false)
            }
        }
    }

    companion object {
        private val BACKGROUND_SPRITE: Identifier =
            Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "toast/axolotlclient")
        private const val DISPLAY_TIME_MILLIS = 5000
        private const val MAX_LINE_SIZE = 200
        private const val LINE_SPACING = 12
        private const val MARGIN = 10

        @JvmStatic
        fun multiline(minecraft: Minecraft, title: Component, message: Component): AxolotlClientToast {
            val font = minecraft.font
            val list = font.split(message, MAX_LINE_SIZE)
            val i = minOf(
                MAX_LINE_SIZE,
                max(font.width(title), list.maxOfOrNull { font.width(it) } ?: MAX_LINE_SIZE)
            )
            return AxolotlClientToast(title, list, i + 2 * MARGIN + 15)
        }

        private fun nullToEmpty(message: Component?): ImmutableList<FormattedCharSequence> {
            return if (message == null) ImmutableList.of() else ImmutableList.of(message.visualOrderText)
        }
    }
}
