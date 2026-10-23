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

import java.util.function.Consumer

import com.google.common.collect.ImmutableList
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.network.chat.Component
import org.apache.commons.lang3.ArrayUtils

class KeyBindSelectionList(
    val keyBindsScreen: KeyBindSelectionScreen, minecraft: Minecraft,
    private val selectionConsumer: Consumer<KeyMapping>
) : ContainerObjectSelectionList<KeyBindSelectionList.Entry>(
    minecraft, keyBindsScreen.width, keyBindsScreen.layout.getContentHeight(),
    keyBindsScreen.layout.getHeaderHeight(), ITEM_HEIGHT
) {

    init {
        val keyMappings = ArrayUtils.clone(minecraft.options.keyMappings)
        keyMappings.sort()
        var string: KeyMapping.Category? = null

        for (keyMapping in keyMappings) {
            val string2 = keyMapping.getCategory()
            if (string2 != string) {
                string = string2
                this.addEntry(CategoryEntry(string2.label()))
            }

            val component = Component.translatable(keyMapping.getName())
            this.addEntry(KeyEntry(keyMapping, component))
        }
    }

    override fun getRowWidth(): Int = 340

    inner class CategoryEntry(val name: Component) : Entry() {
        private val width: Int = this@KeyBindSelectionList.minecraft.font.width(this.name)

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            guiGraphicsExtractor.text(
                this@KeyBindSelectionList.minecraft.font, this.name,
                this@KeyBindSelectionList.width / 2 - this.width / 2, getContentY() + getContentHeight() - 9 - 1, -1
            )
        }

        override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null

        override fun children(): List<GuiEventListener> = emptyList()

        override fun narratables(): List<NarratableEntry> = ImmutableList.of(object : NarratableEntry {
            override fun narrationPriority(): NarrationPriority = NarrationPriority.HOVERED

            override fun updateNarration(narrationElementOutput: NarrationElementOutput) {
                narrationElementOutput.add(NarratedElementType.TITLE, this@CategoryEntry.name)
            }
        })
    }

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    inner class KeyEntry(private val key: KeyMapping, name: Component) : Entry() {
        private val boundKey: Component = key.translatedKeyMessage
        private val changeButton: Button
        private val nameWidget: StringWidget = StringWidget(name, minecraft.font)
            .setMaxWidth(100, StringWidget.TextOverflow.SCROLLING)

        init {
            this.changeButton = Button.builder(Component.translatable("keystrokes.key.select")) {
                selectionConsumer.accept(key)
                keyBindsScreen.onClose()
            }
                .bounds(0, 0, 75, 20)
                .build()
            changeButton.active = !(keyBindsScreen.stroke.key != null && key === keyBindsScreen.stroke.key)
        }

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val i = scrollBarX() - 10
            val j = getContentY() - 2
            val k = i - 5 - this.changeButton.width
            this.changeButton.setPosition(k, j)
            this.changeButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            this.nameWidget.setRectangle(getContentWidth() * 3 / 8, getContentHeight(), getContentX(), getContentY())
            this.nameWidget.setMaxWidth(getContentWidth() * 3 / 8, StringWidget.TextOverflow.SCROLLING)
            this.nameWidget.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            guiGraphicsExtractor.text(
                minecraft.font, boundKey, getContentX() + getContentWidth() / 2 - minecraft.font.width(boundKey) / 2,
                getContentY() + getContentHeight() / 2 - 9 / 2, Colors.GRAY.toInt()
            )
        }

        override fun children(): List<GuiEventListener> = ImmutableList.of(this.changeButton)

        override fun narratables(): List<NarratableEntry> = ImmutableList.of(this.changeButton)
    }

    companion object {
        private const val ITEM_HEIGHT = 20
    }
}
