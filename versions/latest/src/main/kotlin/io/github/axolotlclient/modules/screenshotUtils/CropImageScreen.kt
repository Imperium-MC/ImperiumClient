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

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.util.StyleColors
import io.github.axolotlclient.AxolotlClientConfig.impl.util.DrawUtil
import io.github.axolotlclient.mixin.EditBoxAccessor
import io.github.axolotlclient.util.ExtraCursorTypes
import io.github.axolotlclient.util.MathUtil
import io.github.axolotlclient.util.dialogs.DialogProvider
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.abs
import kotlin.math.roundToInt

class CropImageScreen internal constructor(
    private val parent: Screen?, private val image: ImageInstance, private val freeOnClose: Boolean
) : Screen(Component.translatable("gallery.image.crop.title")) {
    private val hfl = HeaderAndFooterLayout(this, 33, 80)
    private var imageWidget: ImageWidget? = null
    private var posX: EditBox? = null
    private var posY: EditBox? = null
    private var posW: EditBox? = null
    private var posH: EditBox? = null

    internal constructor(parent: Screen?, image: ImageInstance) : this(parent, image, false)

    override fun init() {
        hfl.addTitleHeader(title, font)
        val contents = hfl.addToContents(LinearLayout.vertical()).spacing(4)
        val widget = ImageWidget(hfl.x, hfl.headerHeight, hfl.width, hfl.contentHeight)
        imageWidget = widget
        contents.addChild(widget)
        val footer = hfl.addToFooter(LinearLayout.vertical()).spacing(4)
        val inputs = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4)
        inputs.addChild(StringWidget(Component.translatable("gallery.image.crop.position"), font)).setHeight(20)
        posX = inputs.addChild(
            object : EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.x")) {
                override fun insertText(input: String) {
                    if (!input.matches(DIGITS)) return
                    super.insertText(input)
                }
            }
        )
        posY = inputs.addChild(
            object : EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.y")) {
                override fun insertText(input: String) {
                    if (!input.matches(DIGITS)) return
                    super.insertText(input)
                }
            }
        )
        inputs.addChild(StringWidget(Component.translatable("gallery.image.crop.size"), font)).setHeight(20)
        posW = inputs.addChild(
            object : EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.width")) {
                override fun insertText(input: String) {
                    if (!input.matches(DIGITS)) return
                    super.insertText(input)
                }
            }
        )
        posH = inputs.addChild(
            object : EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.height")) {
                override fun insertText(input: String) {
                    if (!input.matches(DIGITS)) return
                    super.insertText(input)
                }
            }
        )
        posX!!.setResponder { _: String -> widget.updateFromEditBoxes() }
        posY!!.setResponder { _: String -> widget.updateFromEditBoxes() }
        posW!!.setResponder { _: String -> widget.updateFromEditBoxes() }
        posH!!.setResponder { _: String -> widget.updateFromEditBoxes() }
        widget.updateEditBoxContents()
        val footerLine2 = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4)
        val footerLine3 = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4)
        footerLine2.addChild(
            Button.builder(Component.translatable("gallery.image.crop.save_as")) {
                widget.getCopyOfSelection().use { crop: NativeImage ->
                    val defaultPath = if (image is ImageInstance.Local) {
                        image.location.parent
                    } else {
                        FabricLoader.getInstance().gameDir
                    }
                    DialogProvider.IMPL.save(
                        "Choose destination",
                        defaultPath.resolve(
                            image.filename.replace(".png", "_cropped-" + Util.getFilenameFormattedDateTime() + ".png")
                        ).toAbsolutePath(), ".png"
                    ).thenAccept { opt ->
                        if (opt.isPresent) {
                            val dest = opt.get()
                            try {
                                crop.writeToFile(dest)
                                AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                                    "gallery.image.crop.save_as.success", "gallery.image.crop.save_as.success.description",
                                    dest.fileName
                                )
                            } catch (e: Exception) {
                                AxolotlClientCommon.getInstance().logger.error("Failed to save cropped image!", e)
                                AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                                    "gallery.image.crop.save_as.failure", "gallery.image.crop.save_as.failure.description",
                                    dest
                                )
                            }
                        }
                    }
                }
            }.width(100).build()
        )
        footerLine2.addChild(
            Button.builder(Component.translatable("gallery.image.crop.save")) {
                val p = if (image is ImageInstance.Local) {
                    image.location.parent
                } else {
                    FabricLoader.getInstance().gameDir.resolve("screenshots")
                }
                val dest = p.resolve(
                    image.filename.replace(".png", "_cropped-" + Util.getFilenameFormattedDateTime() + ".png")
                )
                try {
                    widget.getCopyOfSelection().use { crop: NativeImage -> crop.writeToFile(dest) }
                    AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                        "gallery.image.crop.save.success", "gallery.image.crop.save.success.description", dest.fileName
                    )
                } catch (e: Exception) {
                    AxolotlClientCommon.getInstance().logger.error("Failed to save cropped image!", e)
                    AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                        "gallery.image.crop.save.failure", "gallery.image.crop.save.failure.description", dest.fileName
                    )
                }
            }.width(100).build()
        )
        footerLine2.addChild(
            Button.builder(Component.translatable("gallery.image.crop.copy")) {
                try {
                    widget.getCopyOfSelection().use { crop: NativeImage ->
                        ScreenshotCopying.copy(DrawUtil.writeToByteArray(crop))
                    }
                    AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                        "gallery.image.crop.copy.success", "gallery.image.crop.copy.success.description"
                    )
                } catch (e: Exception) {
                    AxolotlClientCommon.getInstance().logger.error("Failed to copy cropped image!", e)
                    AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                        "gallery.image.crop.copy.failure", "gallery.image.crop.save.copy.description"
                    )
                }
            }.width(100).build()
        )
        footerLine3.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())

        hfl.arrangeElements()
        hfl.visitWidgets(this::addRenderableWidget)
    }

    override fun extractRenderState(
        guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
    ) {
        super.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        guiGraphicsExtractor.blit(
            RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, 0, hfl.headerHeight - 2, 0.0f, 0.0f, this.width, 2, 32, 2
        )
        guiGraphicsExtractor.blit(
            RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - hfl.footerHeight, 0.0f, 0.0f,
            this.width, 2, 32, 2
        )
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun removed() {
        if (freeOnClose) {
            minecraft.textureManager.release(image.id)
        }
    }

    override fun repositionElements() {
        val widget = imageWidget
        if (widget != null) {
            widget.setHeight(hfl.contentHeight)
            widget.setWidth(hfl.width)
        }
        hfl.arrangeElements()
    }

    private inner class ImageWidget(x: Int, y: Int, width: Int, height: Int) :
        AbstractWidget(x, y, width, height, Component.empty()) {
        var dragX = 0f
        var dragY = 0f
        var dragX1 = 0f
        var dragY1 = 0f
        private var currentHandle: DragHandle? = null
        private val imgAspectRatio: Float = image.image.width.toFloat() / image.image.height
        private var imgX = 0
        private var imgY = 0
        private var imgWidth = 0
        private var imgHeight = 0
        private var scale = 1f
        private var translateX = 0f
        private var translateY = 0f
        private var clickDragOffsetX = 0f
        private var clickDragOffsetY = 0f

        init {
            setImgDimensions()
            dragX = imgX.toFloat()
            dragY = imgY.toFloat()
            dragX1 = dragX + imgWidth
            dragY1 = dragY + imgHeight
        }

        override fun setX(x: Int) {
            val diff0 = this.dragX - this.imgX
            val diff1 = this.dragX1 - this.imgX
            this.imgX = imgX - this.x + x
            this.dragX = imgX + diff0
            this.dragX1 = imgX + diff1
            super.setX(x)
            updateEditBoxContents()
        }

        override fun setY(y: Int) {
            val diff0 = this.dragY - imgY
            val diff1 = this.dragY1 - imgY
            imgY = imgY - this.y + y
            this.dragY = imgY + diff0
            this.dragY1 = imgY + diff1
            super.setY(y)
            updateEditBoxContents()
        }

        override fun setWidth(width: Int) {
            super.setWidth(width)
            setImgDimensions()
        }

        override fun setHeight(height: Int) {
            super.setHeight(height)
            setImgDimensions()
        }

        private fun setImgDimensions() {
            val dragAreaPercentageX0 = (this.dragX - imgX) / imgWidth
            val dragAreaPercentageX1 = (this.dragX1 - imgX) / imgWidth
            val dragAreaPercentageY0 = (this.dragY - imgY) / imgHeight
            val dragAreaPercentageY1 = (this.dragY1 - imgY) / imgHeight
            imgHeight = height - DRAG_HANDLE_RADIUS * 2
            val w = (imgHeight * imgAspectRatio).toInt()
            imgWidth = minOf(w, width - DRAG_HANDLE_RADIUS * 2)
            imgHeight = (imgWidth / imgAspectRatio).toInt()
            imgX = x + width / 2 - imgWidth / 2
            imgY = y + height / 2 - imgHeight / 2
            this.dragX = imgX + dragAreaPercentageX0 * imgWidth
            this.dragX1 = imgX + dragAreaPercentageX1 * imgWidth
            this.dragY = imgY + dragAreaPercentageY0 * imgHeight
            this.dragY1 = imgY + dragAreaPercentageY1 * imgHeight
            updateEditBoxContents()
        }

        override fun extractWidgetRenderState(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
        ) {
            guiGraphicsExtractor.`br$pushMatrix`()
            guiGraphicsExtractor.`br$pushScissor`(x, y, width, height)
            guiGraphicsExtractor.`br$translateMatrix`(this@CropImageScreen.width / 2f, this@CropImageScreen.height / 2f)
            guiGraphicsExtractor.`br$translateMatrix`(translateX, translateY)
            guiGraphicsExtractor.`br$scaleMatrix`(scale, scale)
            guiGraphicsExtractor.`br$translateMatrix`(-this@CropImageScreen.width / 2f, -this@CropImageScreen.height / 2f)
            guiGraphicsExtractor.blit(
                RenderPipelines.GUI_TEXTURED, image.id, imgX, imgY, 0f, 0f, imgWidth, imgHeight, imgWidth, imgHeight
            )
            // Can we have a stencil buffer please?
            guiGraphicsExtractor.`br$scaleMatrix`(1 / scale, 1 / scale)
            val scaledDragX = Math.round(dragX * scale)
            val scaledDragX1 = Math.round(dragX * scale + (dragX1 - dragX) * scale)
            val scaledDragY = Math.round(dragY * scale)
            val scaledDragY1 = Math.round(dragY * scale + (dragY1 - dragY) * scale)
            guiGraphicsExtractor.`br$pushMatrix`()
            guiGraphicsExtractor.`br$translateMatrix`(imgX * scale, imgY * scale)
            guiGraphicsExtractor.`br$fillRectWithCutout`(
                0, 0, Math.round(imgWidth * scale), Math.round(imgHeight * scale),
                Colors.DARK_GRAY.withAlpha(180).toInt(),
                Math.round(scaledDragX - imgX * scale), Math.round(scaledDragY - imgY * scale),
                scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY
            )
            guiGraphicsExtractor.`br$popMatrix`()

            val hoveredHandleColor = StyleColors.highlight().toInt()
            var hoveredHandle: DragHandle? = null
            if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                if (minecraft.hasControlDown()) {
                    guiGraphicsExtractor.requestCursor(CursorTypes.RESIZE_ALL)
                } else if (currentHandle != null) {
                    hoveredHandle = currentHandle
                    guiGraphicsExtractor.requestCursor(currentHandle!!.cursor)
                } else {
                    hoveredHandle = getHandle(getTransformedX(mouseX.toDouble()), getTransformedY(mouseY.toDouble()))
                    if (hoveredHandle != null) {
                        guiGraphicsExtractor.requestCursor(hoveredHandle.cursor)
                    }
                }
            }
            val handleSize = minOf(minOf(scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY) / 3, DRAG_HANDLE_RADIUS)
            val handleColor = StyleColors.highlight2().toInt()
            val xHandleW = maxOf(scaledDragX1 - scaledDragX - handleSize * 2 - 10, 0)
            val outlineWidth = 0.5f
            val handleRadius = handleSize * 3 / 4f
            val xHandleRadius = minOf(handleRadius, xHandleW / 2f)
            if (xHandleW > 0 && xHandleRadius > 1f) {
                val handleXStart = scaledDragX + (scaledDragX1 - scaledDragX) / 2 - xHandleW / 2
                guiGraphicsExtractor.`br$outlineRectRoundVarying`(
                    handleXStart, scaledDragY, xHandleW, handleSize,
                    if (hoveredHandle === DragHandle.TOP_CENTER) hoveredHandleColor else handleColor,
                    0f, xHandleRadius, xHandleRadius, 0f, outlineWidth
                )
                guiGraphicsExtractor.`br$outlineRectRoundVarying`(
                    handleXStart, scaledDragY1 - handleSize, xHandleW, handleSize,
                    if (hoveredHandle === DragHandle.BOTTOM_CENTER) hoveredHandleColor else handleColor,
                    xHandleRadius, 0f, 0f, xHandleRadius, outlineWidth
                )
            }
            val yHandleH = maxOf(scaledDragY1 - scaledDragY - handleSize * 2 - 10, 0)
            val yHandleRadius = minOf(handleRadius, yHandleH / 2f)
            if (yHandleH > 0 && yHandleRadius > 1f) {
                val handleYStart = scaledDragY + (scaledDragY1 - scaledDragY) / 2 - yHandleH / 2
                guiGraphicsExtractor.`br$outlineRectRoundVarying`(
                    scaledDragX, handleYStart, handleSize, yHandleH,
                    if (hoveredHandle === DragHandle.LEFT_CENTER) hoveredHandleColor else handleColor,
                    0f, 0f, yHandleRadius, yHandleRadius, outlineWidth
                )
                guiGraphicsExtractor.`br$outlineRectRoundVarying`(
                    scaledDragX1 - handleSize, handleYStart, handleSize, yHandleH,
                    if (hoveredHandle === DragHandle.RIGHT_CENTER) hoveredHandleColor else handleColor,
                    yHandleRadius, yHandleRadius, 0f, 0f, outlineWidth
                )
            }
            guiGraphicsExtractor.`br$outlineRect`(
                scaledDragX, scaledDragY, scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY,
                if (hoveredHandle === DragHandle.CENTER_CENTER) hoveredHandleColor else Colors.WHITE.toInt()
            )
            guiGraphicsExtractor.`axolotlclient_rendering$outlineCircle`(
                scaledDragX, scaledDragY,
                if (hoveredHandle === DragHandle.TOP_LEFT) hoveredHandleColor else handleColor, handleSize, outlineWidth
            )
            guiGraphicsExtractor.`axolotlclient_rendering$outlineCircle`(
                scaledDragX1, scaledDragY,
                if (hoveredHandle === DragHandle.TOP_RIGHT) hoveredHandleColor else handleColor, handleSize, outlineWidth
            )
            guiGraphicsExtractor.`axolotlclient_rendering$outlineCircle`(
                scaledDragX1, scaledDragY1,
                if (hoveredHandle === DragHandle.BOTTOM_RIGHT) hoveredHandleColor else handleColor, handleSize, outlineWidth
            )
            guiGraphicsExtractor.`axolotlclient_rendering$outlineCircle`(
                scaledDragX, scaledDragY1,
                if (hoveredHandle === DragHandle.BOTTOM_LEFT) hoveredHandleColor else handleColor, handleSize, outlineWidth
            )
            guiGraphicsExtractor.`br$popScissor`()
            guiGraphicsExtractor.`br$popMatrix`()
        }

        override fun playDownSound(handler: SoundManager) {
        }

        override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null

        override fun narrationPriority(): NarrationPriority = NarrationPriority.NONE

        fun getCopyOfSelection(): NativeImage {
            val trueDragX = Math.round((dragX - imgX) / imgWidth * image.image.width)
            val trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image.width)
            val trueDragY = Math.round((dragY - imgY) / imgHeight * image.image.height)
            val trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image.height)
            val img = NativeImage(trueDragX1 - trueDragX, trueDragY1 - trueDragY, false)
            image.image.copyRect(
                img, trueDragX, trueDragY, 0, 0, trueDragX1 - trueDragX, trueDragY1 - trueDragY, false, false
            )
            return img
        }

        override fun onDrag(event: MouseButtonEvent, mouseX: Double, mouseY: Double) {
            if (event.hasControlDownWithQuirk()) {
                translateX += mouseX.toFloat()
                translateY += mouseY.toFloat()
                return
            }
            val transformedX = getTransformedX(event.x).toFloat()
            val transformedY = getTransformedY(event.y).toFloat()
            when (currentHandle) {
                DragHandle.TOP_LEFT -> {
                    dragX = transformedX + clickDragOffsetX
                    dragY = transformedY + clickDragOffsetY
                }

                DragHandle.TOP_CENTER -> dragY = transformedY

                DragHandle.TOP_RIGHT -> {
                    dragX1 = transformedX + clickDragOffsetX
                    dragY = transformedY + clickDragOffsetY
                }

                DragHandle.LEFT_CENTER -> dragX = transformedX + clickDragOffsetX

                DragHandle.RIGHT_CENTER -> dragX1 = transformedX + clickDragOffsetX

                DragHandle.BOTTOM_LEFT -> {
                    dragY1 = transformedY + clickDragOffsetY
                    dragX = transformedX + clickDragOffsetX
                }

                DragHandle.BOTTOM_CENTER -> dragY1 = transformedY + clickDragOffsetY

                DragHandle.BOTTOM_RIGHT -> {
                    dragX1 = transformedX + clickDragOffsetX
                    dragY1 = transformedY + clickDragOffsetY
                }

                DragHandle.CENTER_CENTER -> {
                    val newMouseX = transformedX - clickDragOffsetX
                    val newMouseY = transformedY - clickDragOffsetY
                    val dragW = dragX1 - dragX
                    val dragH = dragY1 - dragY
                    dragX = MathUtil.clamp(newMouseX, imgX.toFloat(), (imgX + imgWidth).toFloat())
                    dragY = MathUtil.clamp(newMouseY, imgY.toFloat(), (imgY + imgHeight).toFloat())
                    dragX1 = dragX + dragW
                    dragY1 = dragY + dragH
                    if (dragX1 >= imgX + imgWidth) {
                        dragX1 = (imgX + imgWidth).toFloat()
                        dragX = dragX1 - dragW
                    }
                    if (dragY1 >= imgY + imgHeight) {
                        dragY1 = (imgY + imgHeight).toFloat()
                        dragY = dragY1 - dragH
                    }
                    updateEditBoxContents()
                    return
                }

                null -> {
                    super.onDrag(event, mouseX, mouseY)
                    return
                }
            }
            clampCrop()
            updateEditBoxContents()
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            if (minecraft.hasControlDown()) {
                if (scrollY > 0) {
                    if (scale > 40f) return false
                    scale *= 2f
                    val offsetX = this@CropImageScreen.width / 2f + translateX
                    val offsetY = this@CropImageScreen.height / 2f + translateY
                    val mirroredOnOffsetX = offsetX - (mouseX - offsetX)
                    val mirroredOnOffsetY = offsetY - (mouseY - offsetY)
                    translateX = (mirroredOnOffsetX - this@CropImageScreen.width / 2f).toFloat()
                    translateY = (mirroredOnOffsetY - this@CropImageScreen.height / 2f).toFloat()
                } else {
                    if (scale < 0.2f) return false
                    scale /= 2f
                    val offsetX = this@CropImageScreen.width / 2f + translateX
                    val offsetY = this@CropImageScreen.height / 2f + translateY
                    val mirroredOnOffsetX = offsetX + (mouseX - offsetX) / 2f
                    val mirroredOnOffsetY = offsetY + (mouseY - offsetY) / 2f
                    translateX = (mirroredOnOffsetX - this@CropImageScreen.width / 2f).toFloat()
                    translateY = (mirroredOnOffsetY - this@CropImageScreen.height / 2f).toFloat()
                }
                return true
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        override fun onRelease(event: MouseButtonEvent) {
            if (currentHandle != null) {
                super.playDownSound(minecraft.soundManager)
            }
            currentHandle = null
            super.onRelease(event)
        }

        private fun clampCrop() {
            dragX = MathUtil.clamp(dragX, imgX.toFloat(), (imgX + imgWidth).toFloat())
            dragX1 = MathUtil.clamp(dragX1, imgX.toFloat(), (imgX + imgWidth).toFloat())
            dragY = MathUtil.clamp(dragY, imgY.toFloat(), (imgY + imgHeight).toFloat())
            dragY1 = MathUtil.clamp(dragY1, imgY.toFloat(), (imgY + imgHeight).toFloat())
            if (dragX >= dragX1) {
                val x = dragX
                dragX = dragX1
                dragX1 = x
                currentHandle = currentHandle?.mirrorX()
            }
            if (dragY >= dragY1) {
                val y = dragY
                dragY = dragY1
                dragY1 = y
                currentHandle = currentHandle?.mirrorY()
            }
        }

        override fun onClick(event: MouseButtonEvent, isDoubleClick: Boolean) {
            val mouseX = getTransformedX(event.x)
            val mouseY = getTransformedY(event.y)
            currentHandle = getHandle(mouseX, mouseY)
            if (currentHandle != null) {
                clickDragOffsetX = currentHandle!!.getXOffset(mouseX.toFloat(), this)
                clickDragOffsetY = currentHandle!!.getYOffset(mouseY.toFloat(), this)
            }
            super.onClick(event, isDoubleClick)
        }

        private fun getHandle(mouseXIn: Double, mouseYIn: Double): DragHandle? {
            var mouseX = mouseXIn
            var mouseY = mouseYIn
            mouseX *= scale.toDouble()
            mouseY *= scale.toDouble()
            val scaledDragX = dragX * scale
            val scaledDragX1 = dragX * scale + (dragX1 - dragX) * scale
            val scaledDragY = dragY * scale
            val scaledDragY1 = dragY * scale + (dragY1 - dragY) * scale
            val handleSize = minOf(
                minOf((scaledDragX1 - scaledDragX), (scaledDragY1 - scaledDragY)) / 3, DRAG_HANDLE_RADIUS.toFloat()
            )
            val handleSizeSq = handleSize * handleSize
            if (MathUtil.distSq(mouseX, mouseY, scaledDragX.toDouble(), scaledDragY.toDouble()) < handleSizeSq) {
                return DragHandle.TOP_LEFT
            } else if (MathUtil.distSq(
                    mouseX, mouseY, scaledDragX1.toDouble(), scaledDragY.toDouble()
                ) < handleSizeSq
            ) {
                return DragHandle.TOP_RIGHT
            } else if (MathUtil.distSq(
                    mouseX, mouseY, scaledDragX1.toDouble(), scaledDragY1.toDouble()
                ) < handleSizeSq
            ) {
                return DragHandle.BOTTOM_RIGHT
            } else if (MathUtil.distSq(
                    mouseX, mouseY, scaledDragX.toDouble(), scaledDragY1.toDouble()
                ) < handleSizeSq
            ) {
                return DragHandle.BOTTOM_LEFT
            }
            val xHandleW = maxOf(scaledDragX1 - scaledDragX - handleSize * 2 - 10, 0f)
            val handleRadius = handleSize * 3 / 4f
            val xHandleRadius = minOf(handleRadius, xHandleW / 2f)
            if (xHandleW > 0 && xHandleRadius > 1f) {
                val handleXStart = scaledDragX + (scaledDragX1 - scaledDragX) / 2 - xHandleW / 2f
                if (isOverRoundedRect(
                        handleXStart, scaledDragY, xHandleW, handleSize, 0f, xHandleRadius, xHandleRadius, 0f,
                        mouseX, mouseY
                    )
                ) {
                    return DragHandle.TOP_CENTER
                } else if (isOverRoundedRect(
                        handleXStart, scaledDragY1 - handleSize, xHandleW, handleSize, xHandleRadius, 0f, 0f,
                        xHandleRadius, mouseX, mouseY
                    )
                ) {
                    return DragHandle.BOTTOM_CENTER
                }
            }
            val yHandleH = maxOf(scaledDragY1 - scaledDragY - handleSize * 2 - 10, 0f)
            val yHandleRadius = minOf(handleRadius, yHandleH / 2f)
            if (yHandleH > 0 && yHandleRadius > 1f) {
                val handleYStart = scaledDragY + (scaledDragY1 - scaledDragY) / 2 - yHandleH / 2f
                if (isOverRoundedRect(
                        scaledDragX, handleYStart, handleSize, yHandleH, 0f, 0f, yHandleRadius, yHandleRadius,
                        mouseX, mouseY
                    )
                ) {
                    return DragHandle.LEFT_CENTER
                } else if (isOverRoundedRect(
                        scaledDragX1 - handleSize, handleYStart, handleSize, yHandleH, yHandleRadius,
                        yHandleRadius, 0f, 0f, mouseX, mouseY
                    )
                ) {
                    return DragHandle.RIGHT_CENTER
                }
            }
            if (mouseX >= scaledDragX && mouseX <= scaledDragX + (scaledDragX1 - scaledDragX) && mouseY >= scaledDragY &&
                mouseY <= scaledDragY + (scaledDragY1 - scaledDragY)
            ) {
                return DragHandle.CENTER_CENTER
            }
            return null
        }

        private fun isOverRoundedRect(
            x: Float, y: Float, width: Float, height: Float, roundingTL: Float, roundingBL: Float,
            roundingBR: Float, roundingTR: Float, mouseX: Double, mouseY: Double
        ): Boolean {
            val x1 = x + width
            val y1 = y + height
            if (!(mouseX >= x && mouseX <= x1 && mouseY >= y && mouseY <= y1)) {
                return false // short-circuit as this check is cheap
            }
            // need to check in segments
            // top-left arc
            if (roundingTL > 0 && mouseX >= x && mouseX <= x + roundingTL && mouseY >= y && mouseY <= y + roundingTL) {
                return MathUtil.distSq(
                    (x + roundingTL).toDouble(), (y + roundingTL).toDouble(), mouseX, mouseY
                ) <= roundingTL * roundingTL
            }
            // top-right arc
            if (roundingTR > 0 && mouseX >= x1 - roundingTR && mouseX <= x1 && mouseY >= y && mouseY <= y + roundingTR) {
                return MathUtil.distSq(
                    (x1 - roundingTR).toDouble(), (y + roundingTR).toDouble(), mouseX, mouseY
                ) <= roundingTR * roundingTR
            }
            // bottom-left arc
            if (roundingBL > 0 && mouseX >= x && mouseX <= x + roundingBL && mouseY >= y1 - roundingBL && mouseY <= y1) {
                return MathUtil.distSq(
                    (x + roundingBL).toDouble(), (y1 - roundingBL).toDouble(), mouseX, mouseY
                ) <= roundingBL * roundingBL
            }
            // bottom-right arc
            if (roundingBR > 0 && mouseX >= x1 - roundingBR && mouseX <= x1 && mouseY >= y1 - roundingBR && mouseY <= y1) {
                return MathUtil.distSq(
                    (x1 - roundingBR).toDouble(), (y1 - roundingBR).toDouble(), mouseX, mouseY
                ) <= roundingBR * roundingBR
            }
            return true
        }

        private fun getTransformedX(mouseX: Double): Double =
            (mouseX - translateX - this@CropImageScreen.width / 2f) / scale + this@CropImageScreen.width / 2f

        private fun getTransformedY(mouseY: Double): Double =
            (mouseY - translateY - this@CropImageScreen.height / 2f) / scale + this@CropImageScreen.height / 2f

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        }

        fun updateEditBoxContents() {
            val trueDragX = Math.round((dragX - imgX) / imgWidth * image.image.width)
            val trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image.width)
            val trueDragY = Math.round((dragY - imgY) / imgHeight * image.image.height)
            val trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image.height)
            val x = posX
            if (x != null && !x.isFocused) {
                val res = (x as EditBoxAccessor).getResponder()
                x.setResponder { _ -> }
                x.setValue(trueDragX.toString())
                if (res != null) x.setResponder(res)
            }
            val y = posY
            if (y != null && !y.isFocused) {
                val res = (y as EditBoxAccessor).getResponder()
                y.setResponder { _ -> }
                y.setValue(trueDragY.toString())
                if (res != null) y.setResponder(res)
            }
            val w = posW
            if (w != null && !w.isFocused) {
                val res = (w as EditBoxAccessor).getResponder()
                w.setResponder { _ -> }
                w.setValue((trueDragX1 - trueDragX).toString())
                if (res != null) w.setResponder(res)
            }
            val h = posH
            if (h != null && !h.isFocused) {
                val res = (h as EditBoxAccessor).getResponder()
                h.setResponder { _ -> }
                h.setValue((trueDragY1 - trueDragY).toString())
                if (res != null) h.setResponder(res)
            }
        }

        fun updateFromEditBoxes() {
            val xBox = posX ?: return
            val yBox = posY ?: return
            val wBox = posW ?: return
            val hBox = posH ?: return
            val xValue = xBox.value
            val yValue = yBox.value
            val wValue = wBox.value
            val hValue = hBox.value
            if (xValue.isEmpty() || yValue.isEmpty() || wValue.isEmpty() || hValue.isEmpty()) return
            val pX = xValue.toInt()
            val pY = yValue.toInt()
            val pW = wValue.toInt()
            val pH = hValue.toInt()
            val iW = image.image.width
            val iH = image.image.height
            if (pX < 0 || pX > iW || pY < 0 || pY > iH || pW < 0 || pW > iW || pH < 0 || pH > iH) return
            val dragAreaPercentageX0 = pX.toFloat() / iW
            val dragAreaPercentageY0 = pY.toFloat() / iH
            val dragAreaPercentageX1 = (pX + pW).toFloat() / iW
            val dragAreaPercentageY1 = (pY + pH).toFloat() / iH
            dragX = MathUtil.clamp(imgX + dragAreaPercentageX0 * imgWidth, imgX.toFloat(), (imgX + imgWidth).toFloat())
            dragX1 = MathUtil.clamp(imgX + dragAreaPercentageX1 * imgWidth, imgX.toFloat(), (imgX + imgWidth).toFloat())
            dragY = MathUtil.clamp(imgY + dragAreaPercentageY0 * imgHeight, imgY.toFloat(), (imgY + imgHeight).toFloat())
            dragY1 = MathUtil.clamp(imgY + dragAreaPercentageY1 * imgHeight, imgY.toFloat(), (imgY + imgHeight).toFloat())
            val wScale = imgWidth / (dragX1 - dragX)
            val hScale = imgHeight / (dragY1 - dragY)
            scale = if (abs(wScale - 1) < abs(hScale - 1)) wScale else hScale
            translateX = -this@CropImageScreen.width / 2f - scale * (-this@CropImageScreen.width / 2f + dragX) + x + width / 2f - (dragX1 - dragX) * scale / 2f
            translateY = -this@CropImageScreen.height / 2f - scale * (-this@CropImageScreen.height / 2f + dragY) + y + height / 2f - (dragY1 - dragY) * scale / 2f
            updateEditBoxContents()
        }

    }

    private enum class DragHandle(val cursor: CursorType) {
        TOP_LEFT(ExtraCursorTypes.RESIZE_NWSE),
        TOP_CENTER(CursorTypes.RESIZE_NS),
        TOP_RIGHT(ExtraCursorTypes.RESIZE_NESW),
        LEFT_CENTER(CursorTypes.RESIZE_EW),
        RIGHT_CENTER(CursorTypes.RESIZE_EW),
        BOTTOM_LEFT(ExtraCursorTypes.RESIZE_NESW),
        BOTTOM_CENTER(CursorTypes.RESIZE_NS),
        BOTTOM_RIGHT(ExtraCursorTypes.RESIZE_NWSE),
        CENTER_CENTER(CursorTypes.RESIZE_ALL);

        fun mirrorX(): DragHandle = when (this) {
            TOP_LEFT -> TOP_RIGHT
            TOP_RIGHT -> TOP_LEFT
            LEFT_CENTER -> RIGHT_CENTER
            RIGHT_CENTER -> LEFT_CENTER
            BOTTOM_LEFT -> BOTTOM_RIGHT
            BOTTOM_RIGHT -> BOTTOM_LEFT
            else -> this
        }

        fun mirrorY(): DragHandle = when (this) {
            TOP_LEFT -> BOTTOM_LEFT
            TOP_CENTER -> BOTTOM_CENTER
            TOP_RIGHT -> BOTTOM_RIGHT
            BOTTOM_LEFT -> TOP_LEFT
            BOTTOM_CENTER -> TOP_CENTER
            BOTTOM_RIGHT -> TOP_RIGHT
            else -> this
        }

        fun getXOffset(mouseX: Float, w: ImageWidget): Float = when (this) {
            TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT -> w.dragX - mouseX
            TOP_CENTER, BOTTOM_CENTER -> 0f
            CENTER_CENTER -> mouseX - w.dragX
            TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT -> w.dragX1 - mouseX
        }

        fun getYOffset(mouseY: Float, w: ImageWidget): Float = when (this) {
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> w.dragY - mouseY
            LEFT_CENTER, RIGHT_CENTER -> 0f
            CENTER_CENTER -> mouseY - w.dragY
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> w.dragY1 - mouseY
        }
    }

    companion object {
        private const val DRAG_HANDLE_RADIUS = 7
        private val DIGITS = Regex("\\d*")
    }
}
