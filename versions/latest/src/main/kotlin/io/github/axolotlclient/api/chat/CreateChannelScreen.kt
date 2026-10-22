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
package io.github.axolotlclient.api.chat

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.Persistence
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutElement
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class CreateChannelScreen(private val parent: Screen?) :
    Screen(Component.translatable("api.chat.groups.create")) {

    override fun init() {
        val layout = HeaderAndFooterLayout(this)
        layout.addTitleHeader(title, font)

        val content = LinearLayout.horizontal().spacing(8)
        content.defaultCellSetting().alignVerticallyTop()
        content.defaultCellSetting().alignHorizontallyCenter()

        val names = content.addChild(LinearLayout.vertical().spacing(20 - font.lineHeight + 8 - 5))
        names.defaultCellSetting().paddingTop(5)
        val options = content.addChild(LinearLayout.vertical().spacing(8))
        val nameField = EditBox(font, 150, 20, Component.empty())
        names.addChild(text("api.chat.groups.name"))
        options.addChild(nameField)

        val count = AtomicReference<Supplier<Int>?>()
        val duration = AtomicReference<Supplier<Long>?>()

        val countDisabler = AtomicReference<Consumer<Boolean>?>()
        val durationDisabler = AtomicReference<Consumer<Boolean>?>()

        val persistence = CycleButton.builder(
            Function { type: Persistence.Type ->
                Component.translatable("api.chat.persistence." + type.id)
            },
            Persistence.Type.CHANNEL
        )
            .withValues(*Persistence.Type.entries.toTypedArray()).displayOnlyValue()
            .create(Component.empty()) { _, obj: Persistence.Type ->
                when (obj) {
                    Persistence.Type.COUNT_DURATION -> {
                        countDisabler.get()!!.accept(true)
                        countDisabler.get()!!.accept(true)
                    }
                    Persistence.Type.DURATION -> {
                        durationDisabler.get()!!.accept(true)
                        countDisabler.get()!!.accept(false)
                    }
                    Persistence.Type.COUNT -> {
                        countDisabler.get()!!.accept(true)
                        durationDisabler.get()!!.accept(false)
                    }
                    Persistence.Type.CHANNEL -> {
                        countDisabler.get()!!.accept(false)
                        durationDisabler.get()!!.accept(false)
                    }
                }
            }
        names.addChild(text("api.chat.groups.persistence"))
        options.addChild(persistence)
        names.addChild(text("api.chat.groups.persistence.count", "api.chat.groups.persistence.count.tooltip"))
        countDisabler.set(
            sliderAssembly(
                Consumer { element: LayoutElement -> options.addChild(element) },
                Function { value: Double -> (value * 100.0).toInt() },
                Consumer { count.set(it) }
            )
        )
        names.addChild(text("api.chat.groups.persistence.duration", "api.chat.groups.persistence.duration.tooltip"))
        durationDisabler.set(
            sliderAssembly(
                Consumer { element: LayoutElement -> options.addChild(element) },
                Function { value: Double -> (value * 100.0).toLong() },
                Consumer { duration.set(it) }
            )
        )
        countDisabler.get()!!.accept(false)
        durationDisabler.get()!!.accept(false)

        val namesInput = options.addChild(EditBox(font, 150, 20, Component.empty()))
        names.addChild(text("api.chat.groups.participants"))

        layout.addToContents(content)

        val footer = LinearLayout.horizontal().spacing(8)
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL) { minecraft.gui.setScreen(parent) }.build())
        footer.addChild(
            Button.builder(CommonComponents.GUI_DONE) {
                ChannelRequest.createChannel(
                    nameField.value, Persistence.of(
                        persistence.value, count.get()!!.get(),
                        duration.get()!!.get()
                    ),
                    *namesInput.value.split(",")
                        .filter { s -> s.isNotEmpty() }
                        .toTypedArray()
                ).thenRun { minecraft.execute(Runnable { minecraft.gui.setScreen(parent) }) }
            }.build()
        )
        layout.addToFooter(footer)

        layout.arrangeElements()
        layout.visitWidgets(this::addRenderableWidget)
    }

    private fun <T> sliderAssembly(
        layoutConsumer: Consumer<LayoutElement>,
        valueFunc: Function<Double, T>,
        value: Consumer<Supplier<T>>
    ): Consumer<Boolean> {
        val currentVal = AtomicReference<T?>()
        val slider = object : AbstractSliderButton(0, 0, 128, 20, Component.empty(), 0.0) {
            public override fun updateMessage() {
                // `this.value` = the slider position field (what the Java original read)
                message = Component.literal(valueFunc.apply(this.value).toString())
            }

            public override fun applyValue() {
                currentVal.set(valueFunc.apply(this.value))
            }

            public override fun setValue(v: Double) {
                this.value = v
            }
        }
        slider.updateMessage()
        slider.applyValue()
        @Suppress("UNCHECKED_CAST") value.accept(Supplier { currentVal.get() as T })
        val text = EditBox(font, 128, 20, Component.empty())
        val textButton = SpriteIconButton.builder(
            Component.translatable("slider.text_input"),
            {
                if (slider.visible) {
                    text.setPosition(slider.x, slider.y)
                    text.setWidth(slider.width)
                    text.setHeight(slider.height)
                    text.setValue(currentVal.get().toString())
                } else {
                    runCatching { slider.setValue(text.value.toDouble()) }
                }
                slider.visible = !slider.visible
                text.visible = !slider.visible
            },
            true
        ).sprite(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "cursor"), 8, 8).size(20, 20).build()
        val assembly = LinearLayout.horizontal().spacing(2)
        assembly.addChild(slider)
        assembly.addChild(textButton)
        text.visible = false
        addRenderableWidget(text)
        layoutConsumer.accept(assembly)
        return Consumer { v: Boolean ->
            slider.active = v
            textButton.active = v
            text.active = v
        }
    }

    private fun text(translationKey: String): LayoutElement {
        return StringWidget(150, font.lineHeight, Component.translatable(translationKey), font)
    }

    private fun text(translationKey: String, tooltipKey: String): LayoutElement {
        val widget = StringWidget(150, font.lineHeight, Component.translatable(translationKey), font)
        widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)))
        return widget
    }
}
