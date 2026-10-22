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

import java.nio.file.Files

import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.util.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util as McUtil
import net.fabricmc.loader.api.FabricLoader
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The capture half of the chat screenshot button: reads the main render target back, crops
 * it to the chat rectangle, and saves it under a `chat-` namespace in the screenshots
 * directory.
 */
object ChatScreenshot {
    /** A pixel rectangle inside a screenshot image. */
    @JvmRecord
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Maps the chat rectangle from GUI units to screenshot pixels.
     */
    @JvmStatic
    fun cropRect(
        imageWidth: Int, imageHeight: Int, guiScale: Double, chatWidth: Int, chatHeight: Int, guiHeight: Int, pad: Int
    ): Rect {
        val left = clampPixel(-pad, guiScale, imageWidth)
        val right = clampPixel(chatWidth + pad, guiScale, imageWidth)
        val bottom = clampPixel(guiHeight - 40 + pad, guiScale, imageHeight)
        val top = clampPixel(guiHeight - 40 - chatHeight - pad, guiScale, imageHeight)
        // Keep the origin one pixel inside the image even in the degenerate case.
        val x = min(max(0, left), max(0, imageWidth - 1))
        val y = min(max(0, top), max(0, imageHeight - 1))
        val w = max(1, min(right, imageWidth) - x)
        val h = max(1, min(bottom, imageHeight) - y)
        return Rect(x, y, w, h)
    }

    private fun clampPixel(guiUnits: Int, guiScale: Double, imageBound: Int): Int =
        max(0, min(imageBound, (guiUnits * guiScale).roundToInt()))

    /**
     * Captures the chat region of the current frame. Must run on the render/client thread
     * (the chat screen button's click handler).
     */
    @JvmStatic
    fun capture(client: Minecraft) {
        val target = try {
            client.gameRenderer.mainRenderTarget()
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Chat screenshot: render target unreachable", e)
            return
        }
        val chatWidth = ChatComponent.getWidth(client.options.chatWidth().get())
        val chatScale = max(0.0, client.options.chatScale().get().toDouble())
        val chatHeight = ceil(ChatComponent.getHeight(client.options.chatHeightFocused().get()) * chatScale).toInt()
        val rect = cropRect(
            target.width, target.height, client.getWindow().getGuiScale().toDouble(),
            chatWidth, chatHeight, client.getWindow().getGuiScaledHeight(), 2
        )
        val fullFallback =
            rect.width <= 1 || rect.height <= 1 || rect.width >= target.width - 1 && rect.height >= target.height - 1
        Screenshot.takeScreenshot(target) { image -> cropAndSave(image, rect, fullFallback) }
    }

    private fun cropAndSave(image: NativeImage, rect: Rect, fullFallback: Boolean) {
        image.use {
            val result: NativeImage = if (fullFallback) {
                image
            } else {
                val cropped = NativeImage(rect.width, rect.height, false)
                cropped.copyRect(image, rect.x, rect.y, rect.width, rect.height, 0, 0, false, false)
                cropped
            }
            val dest = destination()
            CompletableFuture.runAsync(
                {
                    try {
                        result.writeToFile(dest.toFile())
                        Util.addMessageToChatHud(Component.translatable("chat_screenshot.saved"))
                    } catch (e: java.io.IOException) {
                        AxolotlClientCommon.getInstance().logger.warn("Chat screenshot: write failed", e)
                        Util.addMessageToChatHud(Component.translatable("chat_screenshot.failed"))
                    } finally {
                        if (result !== image) {
                            result.close()
                        }
                    }
                },
                McUtil.ioPool()
            )
        }
    }

    /** `screenshots/chat-<datetime>[_n].png`, collision-suffixed like vanilla's getFile. */
    private fun destination(): java.nio.file.Path {
        val dir = FabricLoader.getInstance().gameDir.resolve(Screenshot.SCREENSHOT_DIR)
        val stamp = McUtil.getFilenameFormattedDateTime()
        for (i in 1 until 1000) {
            val candidate = dir.resolve("chat-" + stamp + (if (i == 1) "" else "_$i") + ".png")
            if (!Files.exists(candidate)) {
                return candidate
            }
        }
        return dir.resolve("chat-" + stamp + "-" + System.nanoTime() + ".png")
    }
}
