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

package io.github.axolotlclient.modules.hud.gui.keystrokes

import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.IntegerWidget
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud
import io.github.axolotlclient.modules.hud.gui.layout.Justification
import io.github.axolotlclient.util.DrawUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.min

class ConfigureKeyBindScreen(
    private val parent: Screen?, private val hud: KeystrokeHud, val stroke: KeystrokeHud.Keystroke, private val isAddScreen: Boolean
) : Screen(Component.translatable("keystrokes.stroke.configure_stroke")) {

    private val layout = HeaderAndFooterLayout(this)
    private val widthOption = IntegerOption("", stroke.bounds.width(), { v: Int -> stroke.bounds.width(v) }, 7, 100)
    private val heightOption = IntegerOption("", stroke.bounds.height(), { v: Int -> stroke.bounds.height(v) }, 7, 100)
    private var addButton: Button? = null
    private var synchronizeButton: Button? = null
    private var currentKey: StringWidget? = null

    override fun init() {
        layout.addTitleHeader(title, font)

        val body = LinearLayout.vertical().spacing(8)
        body.defaultCellSetting().alignVerticallyTop()
        val labelFrame = FrameLayout()
        labelFrame.setMinWidth(super.width)
        labelFrame.addChild(
            object : AbstractWidget(0, 0, 200, 40, Component.empty()) {
                override fun extractWidgetRenderState(
                    guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
                ) {
                    val rect = stroke.getRenderPosition()
                    guiGraphicsExtractor.pose().pushMatrix()
                    guiGraphicsExtractor.pose().translate(x.toFloat(), y.toFloat())
                    val scale = min(height / rect.height().toFloat(), width / rect.width().toFloat())
                    guiGraphicsExtractor.pose().translate(width / 2f - (rect.width() * scale) / 2f, 0f)
                    guiGraphicsExtractor.pose().scale(scale, scale)
                    guiGraphicsExtractor.pose().translate(-rect.x().toFloat(), -rect.y().toFloat())
                    DrawUtil.fillRect(guiGraphicsExtractor, rect, Colors.WHITE.withAlpha(128))
                    stroke.render(guiGraphicsExtractor)
                    guiGraphicsExtractor.pose().popMatrix()
                }

                override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
                }
            }.also { it.active = false }
        )
        body.addChild(labelFrame)
        currentKey = body.addChild(StringWidget(Component.empty(), font), LayoutSettings::alignHorizontallyCenter)

        val optionsFrame = FrameLayout()
        optionsFrame.setMinWidth(super.width)
        val contents = LinearLayout.horizontal().spacing(4)
        contents.defaultCellSetting().alignHorizontallyCenter()
        val names = contents.addChild(LinearLayout.vertical().spacing(8))
        val options = contents.addChild(LinearLayout.vertical().spacing(8))
        if (stroke.isLabelEditable()) {
            names.addChild(StringWidget(150, 20, Component.translatable("keystrokes.stroke.label"), font))
            val supportsSynchronization = stroke is KeystrokeHud.LabelKeystroke

            val labelLayout = options.addChild(LinearLayout.horizontal()).spacing(4)
            val label = labelLayout.addChild(
                EditBox(font, if (supportsSynchronization) 30 else 150, 20, Component.empty())
            )
            label.setValue(stroke.getLabel())
            label.setResponder { value: String -> stroke.setLabel(value) }
            if (supportsSynchronization) {
                val s = stroke as KeystrokeHud.LabelKeystroke
                synchronizeButton = labelLayout.addChild(
                    Button.builder(
                        Component.translatable(
                            "keystrokes.stroke.label.synchronize_with_key",
                            if (s.isSynchronizeLabel) CommonComponents.OPTION_ON else CommonComponents.OPTION_OFF
                        )
                    ) { b ->
                        s.setSynchronizeLabel(!s.isSynchronizeLabel)
                        b.setMessage(
                            Component.translatable(
                                "keystrokes.stroke.label.synchronize_with_key",
                                if (s.isSynchronizeLabel) CommonComponents.OPTION_ON else CommonComponents.OPTION_OFF
                            )
                        )
                        label.setEditable(!s.isSynchronizeLabel)
                        if (s.isSynchronizeLabel) {
                            label.setValue(stroke.getLabel())
                        }
                    }.width(58).build()
                )
                synchronizeButton!!.active = s.key != null
                label.setEditable(!s.isSynchronizeLabel)
                labelLayout.addChild(
                    CycleButton.builder(
                        java.util.function.Function<Justification, Component> { j -> Component.translatable(j.toString()) },
                        java.util.function.Supplier<Justification> { s.justification }
                    ).withValues(Justification.values().toList())
                        .create(
                            0, 0, 58, 20, Component.translatable("justification"),
                            CycleButton.OnValueChange { _, `val`: Justification -> s.justification = `val` }
                        )
                )
            }
        }
        if (stroke is KeystrokeHud.CustomRenderKeystroke) {
            val customRender = stroke
            names.addChild(StringWidget(150, 20, Component.translatable("keystrokes.stroke.graphics"), font))
            val graphicsLayout = options.addChild(LinearLayout.horizontal()).spacing(4)
            val sliderMin = 2
            val sliderMax = 25
            val sizeSlider = object : AbstractSliderButton(
                0, 0, 98, 20, Component.translatable("keystrokes.stroke.configure_graphics_size", customRender.getSize()),
                ((customRender.getSize() - sliderMin) / (sliderMax - sliderMin).toFloat()).toDouble()
            ) {
                override fun updateMessage() {
                    message = Component.translatable(
                        "keystrokes.stroke.configure_graphics_size", (value * (sliderMax - sliderMin) + sliderMin).toInt()
                    )
                }

                override fun applyValue() {
                    val size = (value * (sliderMax - sliderMin) + sliderMin).toInt()
                    this.value = ((size - sliderMin) / (sliderMax - sliderMin).toFloat()).toDouble()
                    customRender.setSize(size)
                }
            }
            graphicsLayout.addChild(sizeSlider)
            val widget = ConfigStyles.createWidget(0, 0, 48, 20, customRender.graphics) as AbstractButton
            graphicsLayout.addChild(
                Button.builder(Component.translatable("keystrokes.stroke.configure_graphics")) {
                    widget.onPress(KeyEvent(0, 0, 0))
                }.width(48).build()
            )
        }
        names.addChild(StringWidget(150, 20, Component.translatable("keystrokes.stroke.width"), font))
        options.addChild(IntegerWidget(0, 0, 150, 20, widthOption))
        names.addChild(StringWidget(150, 20, Component.translatable("keystrokes.stroke.height"), font))
        options.addChild(IntegerWidget(0, 0, 150, 20, heightOption))
        optionsFrame.addChild(contents)
        body.addChild(optionsFrame)

        val buttonsFrame = FrameLayout()
        buttonsFrame.setMinWidth(super.width)
        val row4 = LinearLayout.horizontal().spacing(8)
        row4.defaultCellSetting().alignHorizontallyCenter()
        row4.addChild(
            Button.builder(Component.translatable("keystrokes.stroke.configure_key")) {
                minecraft.gui.setScreen(KeyBindSelectionScreen(this, stroke))
            }.width(150).build()
        )
        row4.addChild(
            Button.builder(Component.translatable("keystrokes.stroke.configure_position")) {
                minecraft.gui.setScreen(KeystrokePositioningScreen(this, hud, stroke))
            }.width(150).build()
        )
        buttonsFrame.addChild(row4)
        body.addChild(buttonsFrame)

        layout.addToContents(body)

        val footer = LinearLayout.horizontal().spacing(8)
        if (isAddScreen) {
            addButton = footer.addChild(
                Button.builder(Component.translatable("keystrokes.stroke.add")) {
                    hud.keystrokes!!.add(stroke)
                    onClose()
                }.build()
            )
            addButton!!.active = false
        }
        footer.addChild(
            Button.builder(if (isAddScreen) CommonComponents.GUI_CANCEL else CommonComponents.GUI_BACK) { onClose() }.build()
        )

        layout.addToFooter(footer)

        layout.visitWidgets(this::addRenderableWidget)
        repositionElements()
    }

    override fun repositionElements() {
        if (isAddScreen && stroke.key != null) {
            addButton!!.active = true
        }
        currentKey!!.setWidth(super.width)
        if (stroke.key != null) {
            currentKey!!.setMessage(
                Component.translatable("keystrokes.stroke.key", stroke.key!!.translatedKeyMessage, Component.translatable(stroke.key!!.getName()))
            )
        } else {
            currentKey!!.setMessage(Component.empty())
        }
        if (synchronizeButton != null) {
            synchronizeButton!!.active = stroke.key != null
        }
        layout.arrangeElements()
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
        hud.saveKeystrokes()
    }
}
