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

import java.io.ByteArrayInputStream
import java.nio.file.Files

import com.google.common.hash.Hashing
import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.util.Util
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

class ImageShare private constructor() : ImageNetworking() {

    override fun uploadImage(file: java.nio.file.Path) {
        Util.addMessageToChatHud(Component.translatable("imageUploadStarted"))
        upload(file).whenCompleteAsync { downloadUrl, _ ->
            if (downloadUrl.isEmpty()) {
                Util.addMessageToChatHud(Component.translatable("imageUploadFailure"))
            } else {
                Util.addMessageToChatHud(
                    Component.translatable("imageUploadSuccess").append(" ").append(
                        Component.literal(downloadUrl).setStyle(
                            Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withUnderlined(true)
                                .withClickEvent(ClickEvent.CopyToClipboard(downloadUrl))
                                .withHoverEvent(
                                    HoverEvent.ShowText(Component.translatable("clickToCopy"))
                                )
                        )
                    )
                )
            }
        }
    }

    fun downloadImage(url: String): java.util.concurrent.CompletableFuture<ImageInstance?> =
        download(url).thenApply { data ->
            if (data != ImageNetworking.ImageData.EMPTY) {
                try {
                    ByteArrayInputStream(data.data).use { input ->
                        val remote = ImageInstance.RemoteImpl(
                            NativeImage.read(input), data.name, data.uploader, data.sharedAt, ensureUrl(url).orElseThrow()
                        )
                        try {
                            val local = GalleryScreen.SCREENSHOTS_DIR.resolve(remote.filename)
                            val hash = Hashing.goodFastHash(32)
                            if (Files.exists(local) && hash.hashBytes(data.data) == hash.hashBytes(
                                    Files.readAllBytes(local)
                                )
                            ) {
                                return@thenApply remote.toShared(local)
                            }
                        } catch (ignored: Exception) {
                        }
                        return@thenApply remote
                    }
                } catch (ignored: Exception) {
                }
            }
            null
        }

    companion object {
        private val Instance = ImageShare()

        @JvmStatic
        fun getInstance(): ImageShare = Instance
    }
}
