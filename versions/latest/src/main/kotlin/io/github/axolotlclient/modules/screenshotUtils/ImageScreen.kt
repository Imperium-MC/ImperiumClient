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

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

import com.mojang.blaze3d.Blaze3D
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.impl.util.DrawUtil
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.notifications.Notifications
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.layouts.SpacerElement
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ImageScreen private constructor(
    private val parent: Screen?, private val image: ImageInstance, private val freeOnClose: Boolean
) : Screen(Component.literal(image.filename)) {

    override fun init() {
        val layout = HeaderAndFooterLayout(this)
        val header = layout.addToHeader(LinearLayout.vertical()).spacing(4)
        header.defaultCellSetting().alignHorizontallyCenter()
        header.addChild(StringWidget(title, font))

        val remote = image as? ImageInstance.Remote
        val sharedAt = remote?.sharedAt
        if (remote != null && sharedAt != null) {
            layout.setHeaderHeight(38)
            val uploader = header.addChild(
                StringWidget(
                    Component.translatable(
                        "gallery.image.upload_details",
                        "",
                        sharedAt.atZone(ZoneId.systemDefault())
                            .format(AxolotlClientCommon.getInstance().getConfig().dateTimeFormatter)
                    ), font
                )
            )
            UUIDHelper.tryGetUsernameAsync(remote.uploader ?: "").thenAccept { name ->
                uploader.setMessage(
                    Component.translatable(
                        "gallery.image.upload_details", name,
                        sharedAt.atZone(ZoneId.systemDefault())
                            .format(AxolotlClientCommon.getInstance().getConfig().dateTimeFormatter)
                    )
                )
                uploader.setWidth(font.width(uploader.message))
            }
        }

        val buttonWidth = 75
        val imgAspectRatio = image.image.width / image.image.height.toDouble()
        val imageWidth = minOf(
            (layout.contentHeight * imgAspectRatio).toInt(), layout.width - buttonWidth - 4 - 20
        )
        val imageHeight = (imageWidth / imgAspectRatio).toInt()

        val contents = layout.addToContents(LinearLayout.horizontal().spacing(4))
        if (width / 2 > (imageWidth / 2) + buttonWidth + 4) {
            contents.addChild(SpacerElement(buttonWidth + 4, imageHeight))
        }
        val footer = layout.addToFooter(LinearLayout.horizontal().spacing(4))
        contents.addChild(ImageElement(imageWidth, imageHeight))
        val actions = contents.addChild(LinearLayout.vertical()).spacing(4)
        val local = image as? ImageInstance.Local
        if (local != null) {
            val loc = local.location
            if (API.getInstance().isAuthenticated() && remote == null) {
                actions.addChild(
                    Button.builder(Component.translatable("gallery.image.upload")) { b ->
                        b.active = false
                        ImageShare.getInstance().upload(loc).thenAccept { s ->
                            if (s.isEmpty()) {
                                Notifications.getInstance()
                                    .addStatus("gallery.image.upload.failure", "gallery.image.upload.failure.description")
                            } else {
                                minecraft.execute(
                                    Runnable {
                                        minecraft.gui.setScreen(
                                            ImageScreen(
                                                parent, local.toShared(
                                                s, API.getInstance().self!!.uuid, Instant.now()
                                            ), freeOnClose
                                            )
                                        )
                                    }
                                )
                                minecraft.keyboardHandler.setClipboard(s)
                                Notifications.getInstance()
                                    .addStatus("gallery.image.upload.success", "gallery.image.upload.success.description", s)
                            }
                        }
                    }.width(buttonWidth).build()
                )
            }
            actions.addChild(
                Button.builder(Component.translatable("gallery.image.copy")) { ScreenshotCopying.copy(loc) }
                    .width(buttonWidth).build()
            )
            actions.addChild(
                Button.builder(Component.translatable("gallery.image.open.external")) { Blaze3D.openPath(loc) }
                    .width(buttonWidth).build()
            )
            actions.addChild(
                Button.builder(Component.translatable("gallery.image.delete")) {
                    try {
                        Files.delete(loc)
                        Notifications.getInstance()
                            .addStatus("gallery.image.delete.success", "gallery.image.delete.success.description", loc)
                    } catch (e: Exception) {
                        Notifications.getInstance()
                            .addStatus("gallery.image.delete.failure", "gallery.image.delete.failure.description", loc)
                        AxolotlClientCommon.getInstance().logger.warn("Failed to delete image!", e)
                    }
                }.width(buttonWidth).build()
            )
        }
        if (remote != null) {
            if (local == null) {
                actions.addChild(
                    Button.builder(Component.translatable("gallery.image.save")) { b ->
                        b.active = false
                        try {
                            val out = saveSharedImage(remote)
                            minecraft.gui.setScreen(ImageScreen(parent, remote.toShared(out), freeOnClose))
                        } catch (e: Exception) {
                            Notifications.getInstance()
                                .addStatus("gallery.image.save.failure", "gallery.image.save.failure.description", e.message)
                            AxolotlClientCommon.getInstance().logger.warn("Failed to save shared image!", e)
                        }
                    }.width(buttonWidth).build()
                )
                actions.addChild(
                    Button.builder(Component.translatable("gallery.image.copy")) {
                        try {
                            ScreenshotCopying.copy(DrawUtil.writeToByteArray(image.image))
                        } catch (e: Exception) {
                            Notifications.getInstance()
                                .addStatus("gallery.image.copy.failure", "gallery.image.copy.failure.description", e.message)
                            AxolotlClientCommon.getInstance().logger.warn("Failed to copy shared image!", e)
                        }
                    }.width(buttonWidth).build()
                )
            }
            actions.addChild(
                Button.builder(Component.translatable("gallery.image.open.external.browser")) {
                    Blaze3D.openUri(URI.create(remote.url))
                }.width(buttonWidth).build()
            )
            actions.addChild(
                Button.builder(Component.translatable("gallery.image.copy_url")) {
                    minecraft.keyboardHandler.setClipboard(remote.url)
                }.width(buttonWidth).build()
            )
        }
        actions.addChild(
            Button.builder(Component.translatable("gallery.image.crop")) {
                minecraft.gui.setScreen(CropImageScreen(this, image))
            }.width(buttonWidth).build()
        )

        footer.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())

        layout.arrangeElements()
        layout.visitWidgets(this::addRenderableWidget)
    }

    override fun onClose() {
        if (freeOnClose) {
            minecraft.textureManager.release(image.id)
        }
        minecraft.gui.setScreen(parent)
    }

    private fun saveSharedImage(remote: ImageInstance.Remote): Path {
        val out = GalleryScreen.SCREENSHOTS_DIR.resolve("shared")
            .resolve(remote.uploader ?: "")
            .resolve(remote.filename)
        val infoJson = out.resolveSibling(remote.filename + ".json")

        Files.createDirectories(out.parent)
        remote.image.writeToFile(out)
        val json: Any = mapOf("uploader" to remote.uploader, "shared_at" to remote.sharedAt)
        Files.writeString(infoJson, GsonHelper.GSON.toJson(json))
        return out
    }

    private inner class ImageElement(width: Int, height: Int) :
        AbstractWidget(0, 0, width, height, Component.empty()) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
        ) {
            guiGraphicsExtractor.blit(
                RenderPipelines.GUI_TEXTURED, image.id, x, y, 0f, 0f, this.width, this.height, this.width, this.height
            )
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        }
    }

    companion object {
        fun create(parent: Screen?, future: CompletableFuture<ImageInstance?>, freeOnClose: Boolean): Screen? {
            if (future.isDone) {
                val instance = future.join()
                return if (instance != null) ImageScreen(parent, instance, freeOnClose) else parent
            }
            return LoadingImageScreen(
                parent, future.thenAccept { i: ImageInstance? ->
                    if (i != null) {
                        Minecraft.getInstance().execute(
                            Runnable { Minecraft.getInstance().gui.setScreen(ImageScreen(parent, i, freeOnClose)) }
                        )
                    } else {
                        Minecraft.getInstance().execute(Runnable { Minecraft.getInstance().gui.setScreen(parent) })
                    }
                }, freeOnClose
            )
        }
    }
}
