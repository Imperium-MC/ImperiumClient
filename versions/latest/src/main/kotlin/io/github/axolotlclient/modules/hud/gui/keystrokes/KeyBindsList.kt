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

import com.google.common.collect.ImmutableList
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.network.chat.Component
import kotlin.math.min

class KeyBindsList(val keyBindsScreen: KeystrokesScreen) : ContainerObjectSelectionList<KeyBindsList.Entry>(
    Minecraft.getInstance(), keyBindsScreen.width, keyBindsScreen.layout.getContentHeight(),
    keyBindsScreen.layout.getHeaderHeight(), 24
) {
    private var maxNameWidth = 0

    init {
        reload()
    }

    fun reload() {
        clearEntries()
        for (keyMapping in keyBindsScreen.hud.keystrokes!!) {

            val component = Component.translatable(keyMapping.key!!.getName())
            val i = minecraft.font.width(component)
            if (i > this.maxNameWidth) {
                this.maxNameWidth = i
            }

            this.addEntry(KeyEntry(keyMapping))
        }

        addEntry(SpacerEntry())
        addEntry(NewEntry())
    }

    override fun getRowWidth(): Int = 340

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    class SpacerEntry : Entry() {

        override fun narratables(): List<NarratableEntry> = emptyList()

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
        }

        override fun children(): List<GuiEventListener> = emptyList()

        override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null
    }

    inner class KeyEntry(private val key: KeystrokeHud.Keystroke) : Entry() {
        private val name: Component = key.key!!.translatedKeyMessage
        private val configureButton: Button
        private val removeButton: Button

        init {
            this.configureButton = Button.builder(CONFIGURE_BUTTON_TITLE) {
                minecraft.gui.setScreen(ConfigureKeyBindScreen(keyBindsScreen, keyBindsScreen.hud, key, false))
            }
                .bounds(0, 0, 75, 20)
                .build()
            this.removeButton = Button.builder(REMOVE_BUTTON_TITLE) {
                removeEntry(this)
                keyBindsScreen.removeKey(key)
                refreshScrollAmount()
            }.bounds(0, 0, 50, 20)
                .build()
        }

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val i = scrollBarX() - removeButton.width - 10
            val j = getContentY() - 2
            this.removeButton.setPosition(i, j)
            this.removeButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            val k = i - this.configureButton.width
            this.configureButton.setPosition(k, j)
            this.configureButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            guiGraphicsExtractor.pose().pushMatrix()
            val rect = key.getRenderPosition()
            val scale = min(getContentHeight() / rect.height().toFloat(), 100 / rect.width().toFloat())
            guiGraphicsExtractor.pose().translate(getContentX().toFloat(), getContentY().toFloat())
            guiGraphicsExtractor.pose().scale(scale, scale)
            guiGraphicsExtractor.pose().translate(-rect.x().toFloat(), -rect.y().toFloat())
            key.render(guiGraphicsExtractor)
            guiGraphicsExtractor.pose().popMatrix()
            guiGraphicsExtractor.text(
                minecraft.font, name, getContentX() + getContentWidth() / 2 - minecraft.font.width(name) / 2,
                getContentY() + getContentHeight() / 2 - 9 / 2, Colors.GRAY.toInt()
            )
        }

        override fun children(): List<GuiEventListener> = ImmutableList.of(this.configureButton, removeButton)

        override fun narratables(): List<NarratableEntry> = ImmutableList.of(this.configureButton, removeButton)
    }

    inner class NewEntry : Entry() {
        private val addButton: Button
        private val addSpecialButton: Button
        private val addCustomButton: Button

        init {
            this.addButton = Button.builder(Component.translatable("keystrokes.stroke.add")) {
                minecraft.gui.setScreen(
                    ConfigureKeyBindScreen(keyBindsScreen, keyBindsScreen.hud, keyBindsScreen.hud.newStroke(), true)
                )
            }
                .bounds(0, 0, 100, 20)
                .build()
            this.addSpecialButton = Button.builder(Component.translatable("keystrokes.stroke.add.special")) {
                minecraft.gui.setScreen(AddSpecialKeystrokeScreen(keyBindsScreen, keyBindsScreen.hud))
            }
                .width(100).build()
            this.addCustomButton = Button.builder(Component.translatable("keystrokes.stroke.add.custom")) {
                minecraft.gui.setScreen(
                    ConfigureKeyBindScreen(keyBindsScreen, keyBindsScreen.hud, keyBindsScreen.hud.newCustomStroke(), true)
                )
            }
                .width(100).build()
        }

        override fun narratables(): List<NarratableEntry> = listOf(addCustomButton, addSpecialButton, addButton)

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            var i = scrollBarX() - getContentWidth() / 2 - (300 + 8) / 2 - 10
            val j = getContentY() - 2
            this.addCustomButton.setPosition(i, j)
            this.addCustomButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            i += addCustomButton.width + 4
            this.addSpecialButton.setPosition(i, j)
            this.addSpecialButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            i += addSpecialButton.width + 4
            this.addButton.setPosition(i, j)
            this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        }

        override fun children(): List<GuiEventListener> = listOf(addCustomButton, addSpecialButton, addButton)
    }

    companion object {
        private val CONFIGURE_BUTTON_TITLE = Component.translatable("keystrokes.stroke.configure")
        private val REMOVE_BUTTON_TITLE = Component.translatable("keystrokes.stroke.remove")
    }
}
