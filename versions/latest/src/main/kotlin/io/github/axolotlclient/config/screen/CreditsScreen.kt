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

package io.github.axolotlclient.config.screen

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.credits.Credits
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.DrawUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.net.URI
import java.util.function.Supplier

class CreditsScreen(private val parent: Screen?) :
    Screen(Component.translatable("credits")) {

    private val bgm: SoundInstance = SimpleSoundInstance.forUI(SoundEvents.MUSIC_DISC_CHIRP.value(), 1f, 1f)
    private val haF = HeaderAndFooterLayout(this)
    private var initialized = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (AxolotlClient.config().someNiceBackground.get()) { // Credit to pridelib for the colors
            graphics.fill(0, 0, width, height / 6, 0xFFff0018.toInt())
            graphics.fill(0, height / 6, width, height * 2 / 6, 0xFFffa52c.toInt())
            graphics.fill(0, height * 2 / 6, width, height / 2, 0xFFffff41.toInt())
            graphics.fill(0, height * 2 / 3, width, height * 5 / 6, 0xFF0000f9.toInt())
            graphics.fill(0, height / 2, width, height * 2 / 3, 0xFF008018.toInt())
            graphics.fill(0, height * 5 / 6, width, height, 0xFF86007d.toInt())
        } else {
            super.extractBackground(graphics, mouseX, mouseY, delta)
        }
    }

    override fun init() {
        if (initialized) {
            haF.arrangeElements()
            haF.visitWidgets(this::addRenderableWidget)
            return
        }
        initialized = true
        haF.addTitleHeader(title, font)
        haF.addToContents(CreditsList(minecraft, haF.width, haF.contentHeight, haF.headerHeight, 25))

        val back = haF.addToFooter(Button.builder(CommonComponents.GUI_BACK) { buttonWidget -> onClose() }.build())

        haF.arrangeElements()
        haF.visitWidgets(this::addRenderableWidget)

        this.addRenderableWidget(
            Button.builder(
                Component.translatable("creditsBGM").append(": ")
                    .append(Component.translatable(if (AxolotlClient.config().creditsBGM.get()) "options.on" else "options.off"))
            ) { buttonWidget ->
                AxolotlClient.config().creditsBGM.toggle()
                AxolotlClient.getInstance().saveConfig()
                stopBGM()
                buttonWidget.setMessage(
                    Component.translatable("creditsBGM").append(": ").append(
                        Component.translatable(if (AxolotlClient.config().creditsBGM.get()) "options.on" else "options.off")
                    )
                )
            }.bounds(6, back.y, 100, 20).build()
        )
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
        stopBGM()
    }

    override fun repositionElements() {
        haF.arrangeElements()
    }

    override fun tick() {
        tickBGM()
    }

    fun tickBGM() {
        if (AxolotlClient.config().creditsBGM.get() && !minecraft.soundManager.isActive(bgm)) {
            minecraft.soundManager.play(bgm)
        }
    }

    private fun stopBGM() {
        minecraft.soundManager.stop(bgm)
    }

    private inner class CreditsList(
        minecraftClient: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        entryHeight: Int
    ) : ContainerObjectSelectionList<Entry>(minecraftClient, width, height, top, entryHeight) {

        init {
            addEntry(SpacerTitle("- - - - - - " + I18n.get("contributors") + " - - - - - -"))
            Credits.getContributors().forEach { credit -> addEntry(Credit(credit.name, *credit.things)) }

            addEntry(SpacerTitle("- - - - - - " + I18n.get("other_people") + " - - - - - -"))
            Credits.getOtherPeople().forEach { credit -> addEntry(Credit(credit.name, *credit.things)) }

            if (externalModuleCredits.isNotEmpty()) {
                addEntry(SpacerTitle("- - - - - - " + I18n.get("external_modules") + " - - - - - -"))
                externalModuleCredits.forEach { (s, s2) -> addEntry(Credit(s, *s2)) }
            }
        }
    }

    private abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    private inner class Credit(val name: String, vararg val things: String) : Entry() {

        private val c: Button = object : Button(
            0, 0, 200, 20, Component.literal(name),
            { minecraft.gui.setScreen(CreditOverlay(this@Credit)) },
            { supplier: Supplier<MutableComponent> -> supplier.get() }
        ) {
            private val hoveredMessage: Component = message.copy().withColor(ClientColors.SELECTOR_RED.toInt())

            override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
                if (isHoveredOrFocused) {
                    DrawUtil.outlineRect(graphics, x, y, width, height, ClientColors.ERROR.toInt())
                }
                //int i = this.active ? (isHoveredOrFocused ? ClientColors.SELECTOR_RED.toInt() : -1) : 10526880;
                //this.renderString(graphics, font, i | Mth.ceil(this.alpha * 255.0F) << 24);
                extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE))
            }

            override fun getMessage(): Component {
                return if (isHoveredOrFocused) hoveredMessage else super.getMessage()
            }
        }

        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            c.setPosition(contentX, contentY)
            c.extractRenderState(graphics, mouseX, mouseY, tickDelta)
        }

        override fun children(): List<GuiEventListener> {
            return listOf(c)
        }

        override fun narratables(): List<NarratableEntry> {
            return listOf(c)
        }
    }

    private inner class CreditOverlay(private val credit: Credit) : Screen(Component.literal(credit.name)) {

        override fun onClose() {
            minecraft.gui.setScreen(this@CreditsScreen)
        }

        override fun init() {
            var startY = 100
            for (t in credit.things) {
                val textWidth = font.`br$getWidth`(t)
                if (t.startsWith("http")) {
                    addRenderableWidget(
                        PlainTextButton(
                            width / 2 - textWidth / 2, startY, textWidth, 12,
                            Component.literal(t).withColor(ClientColors.SELECTOR_GREEN.toInt()),
                            { defaultHandleClickEvent(ClickEvent.OpenUrl(URI.create(t)), minecraft, this@CreditsScreen) },
                            font
                        )
                    )
                } else {
                    addRenderableOnly(
                        StringWidget(
                            width / 2 - textWidth / 2, startY, textWidth, 12,
                            Component.literal(t).withColor(ClientColors.SELECTOR_GREEN.toInt()), font
                        )
                    )
                }
                startY += 12
            }
            addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK) { buttonWidget -> onClose() }
                    .pos(width / 2 - 75, height - 33 / 2 - 10).build()
            )
        }

        override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            super.extractBackground(graphics, mouseX, mouseY, delta)
            DrawUtil.fillRect(
                graphics, 100, 50, width - 200, height - 100,
                ClientColors.DARK_GRAY.withAlpha(127).toInt()
            )
            DrawUtil.outlineRect(
                graphics, 100, 50, width - 200, height - 100,
                ClientColors.BLACK.toInt()
            )
        }

        override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            super.extractRenderState(graphics, mouseX, mouseY, delta)
            DrawUtil.drawCenteredString(graphics, font, credit.name, width / 2, 57, -16784327, true)
        }

        override fun tick() {
            this@CreditsScreen.tickBGM()
        }
    }

    private inner class SpacerTitle(private val name: String) : Entry() {

        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            DrawUtil.drawCenteredString(graphics, font, name, contentXMiddle, contentY, -128374, true)
        }

        override fun children(): List<GuiEventListener> {
            return emptyList()
        }

        override fun narratables(): List<NarratableEntry> {
            return emptyList()
        }
    }

    companion object {
        @JvmField
        val externalModuleCredits: HashMap<String, Array<String>> = HashMap()
    }
}
