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

import java.io.File
import java.nio.file.Files
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

import com.mojang.blaze3d.Blaze3D
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption
import io.github.axolotlclient.api.API
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.notifications.Notifications
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.ChatFormatting
import net.minecraft.client.Screenshot
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

class ScreenshotUtils : AbstractModule() {

    private val actions: LinkedHashMap<BooleanSupplier, Action> = CommonUtil.make(LinkedHashMap()) { actions ->
        actions.put(BooleanSupplier { true }, Action("copyAction", ChatFormatting.AQUA, "copy_image") {
            ScreenshotCopying.copy(it)
        })

        actions.put(BooleanSupplier { true }, Action("deleteAction", ChatFormatting.LIGHT_PURPLE, "delete_image") {
            file ->
            try {
                Files.delete(file)
                io.github.axolotlclient.util.Util.addMessageToChatHud(
                    Component.literal(I18n.get("screenshot_deleted").replace("<name>", file.fileName.toString()))
                )
            } catch (e: Exception) {
                AxolotlClientCommon.getInstance().logger.warn("Couldn't delete Screenshot " + file.fileName.toString())
            }
        })

        actions.put(BooleanSupplier { true }, Action("openAction", ChatFormatting.WHITE, "open_image") {
            Blaze3D.openPath(it)
        })

        actions.put(
            BooleanSupplier { true },
            Action("viewInGalleryAction", ChatFormatting.LIGHT_PURPLE, "view_in_gallery") { file ->
                try {
                    val instance: ImageInstance = ImageInstance.LocalImpl(file)
                    client.submit(
                        Runnable { client.gui.setScreen(ImageScreen.create(null, CompletableFuture.completedFuture(instance), true)) }
                    )
                } catch (ignored: Exception) {
                    io.github.axolotlclient.util.Util.addMessageToChatHud(
                        Component.translatable("screenshot.gallery.view.error")
                    )
                }
            }
        )

        actions.put(
            BooleanSupplier { API.getInstance().isAuthenticated() },
            Action("uploadAction", ChatFormatting.AQUA, "upload_image") { ImageShare.getInstance().uploadImage(it) }
        )
    }

    private val category = io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory.create("screenshotUtils")
    private val enabled = BooleanOption("enabled", false)
    private val mode = EnumOption("screenshot_utils.mode", Mode::class.java, Mode.CHAT)
    private val autoExec = StringArrayOption(
        "autoExec", CommonUtil.make(ArrayList<String>()) { names ->
            names.add("off")
            actions.forEach { (_, action) -> names.add(action.translationKey) }
        }.toTypedArray(), "off"
    )

    @JvmField
    val toastBorderColor = ColorOption("screenshot_utils.mode.toast.border_color", Colors.WHITE)

    @JvmField
    val screenshotCropBinding: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "screenshot_utils.screenshot_and_crop")

    override fun init() {
        category.add(
            enabled, mode, autoExec,
            GenericOption("imageViewer", "openViewer") {
                client.gui.setScreen(GalleryScreen(client.gui.screen()))
            }, toastBorderColor
        )

        AxolotlClient.config().general.add(category)
        screenshotCropBinding.`br$registerOnConsumeClick` {
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget()) { img ->
                val instance = ImageInstance.Memory(img)
                val parent = client.gui.screen()
                client.gui.setScreen(CropImageScreen(parent, instance, true))
            }
        }
    }

    fun onScreenshotTaken(text: MutableComponent, shot: File): MutableComponent {
        if (enabled.get()) {
            val t = getUtilsText(shot.toPath())
            if (t != null) {
                return text.append("\n").append(t) as MutableComponent
            }
        }
        return text
    }

    private fun getUtilsText(file: java.nio.file.Path): Component? {
        val autoex = autoExec.get() != "off"
        val mode = this.mode.get()
        if (mode.isToast) {
            try {
                Notifications.getInstance().addStatus(ScreenshotToast(ImageInstance.LocalImpl(file)))
            } catch (e: Exception) {
                Notifications.getInstance().addStatus("screenshotUtils", "failed_to_load_toast")
            }
        }
        if (autoex) {
            actions.forEach { (condition, action) ->
                if (condition.asBoolean && autoExec.get() == action.translationKey) {
                    CompletableFuture.runAsync(
                        { action.getClickEvent(file).doAction() },
                        CompletableFuture.delayedExecutor(2, TimeUnit.MILLISECONDS, AxoMinecraftClient.getInstance())
                    )
                }
            }
        }
        if (autoex || !mode.isChat) return null

        val message = Component.empty()
        actions.forEach { (condition, action) ->
            if (condition.asBoolean) {
                message.append(action.getText(file)).append(" ")
            }
        }
        return message
    }

    private enum class Mode(val isChat: Boolean, val isToast: Boolean) {
        CHAT(true, false),
        TOAST(false, true),
        CHAT_AND_TOAST(true, true);

        override fun toString(): String = "screenshot_utils.mode." + super.toString().lowercase(Locale.ROOT)
    }

    fun interface OnActionCall {
        fun doAction(file: java.nio.file.Path)
    }

    data class Action(
        val translationKey: String, val formatting: ChatFormatting, val hoverTextKey: String,
        val clickEvent: OnActionCall
    ) {
        fun getText(file: java.nio.file.Path): Component =
            Component.translatable(translationKey).setStyle(
                Style.EMPTY.withColor(formatting)
                    .withClickEvent(getClickEvent(file))
                    .withHoverEvent(HoverEvent.ShowText(Component.translatable(hoverTextKey)))
            )

        fun getClickEvent(file: java.nio.file.Path): CustomClickEvent = CustomClickEvent(clickEvent, file)
    }

    class CustomClickEvent(private val action: OnActionCall, private val file: java.nio.file.Path?) : ClickEvent {

        fun doAction() {
            if (file != null) {
                action.doAction(file)
            } else {
                AxolotlClientCommon.getInstance().logger.warn(
                    "How'd you manage to do this? Now there's a screenshot ClickEvent without a File attached to it!"
                )
            }
        }

        override fun action(): ClickEvent.Action = ClickEvent.Action.OPEN_FILE
    }

    companion object {
        private val Instance = ScreenshotUtils()

        @JvmStatic
        fun getInstance(): ScreenshotUtils = Instance
    }
}
