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

import java.util.OptionalLong

import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.MathUtil
import io.github.axolotlclient.util.duck.ToastExtension
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.renderer.RenderPipelines

class ScreenshotToast(private val image: ImageInstance) : Toast, ToastExtension {
    private val width: Int =
        (image.image.width * (TOAST_HEIGHT / image.image.height.toFloat())).toInt()

    override fun width(): Int = 2 + width

    override fun height(): Int = 2 + TOAST_HEIGHT

    override fun getWantedVisibility(): Toast.Visibility = wantedVisibility

    private var wantedVisibility: Toast.Visibility = Toast.Visibility.HIDE

    override fun update(toastManager: ToastManager, visibilityTime: Long) {
        val time = DISPLAY_TIME_MILLIS * toastManager.notificationDisplayTimeMultiplier
        this.wantedVisibility = if (visibilityTime < time) Toast.Visibility.SHOW else Toast.Visibility.HIDE
    }

    override fun extractRenderState(guiGraphicsExtractor: GuiGraphicsExtractor, font: Font, visibilityTime: Long) {
        val color = ScreenshotUtils.getInstance().toastBorderColor.get().toInt()
        guiGraphicsExtractor.fill(0, 0, width(), height(), color)
        val prog = MathUtil.lerp(MathUtil.clamp(visibilityTime / 300f, 0f, 1f), 1f, 0f)
        guiGraphicsExtractor.blit(
            RenderPipelines.GUI_TEXTURED, image.id, 1, 1, 0f, 0f, width, TOAST_HEIGHT, width, TOAST_HEIGHT
        )
        guiGraphicsExtractor.`br$fillRect`(
            1, 1, width, TOAST_HEIGHT, ClientColors.ARGB.color(prog * ClientColors.ARGB.alphaFloat(color), color)
        )
    }

    override fun `axolotlclient$animationDuration`(): OptionalLong = OptionalLong.of(300)

    companion object {
        private const val TOAST_HEIGHT = 56
        private const val DISPLAY_TIME_MILLIS = 3500L
    }
}
