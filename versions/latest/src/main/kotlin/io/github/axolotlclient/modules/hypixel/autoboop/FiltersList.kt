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

package io.github.axolotlclient.modules.hypixel.autoboop

import com.google.common.collect.ImmutableList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class FiltersList(val screen: FilterListConfigurationScreen) : ContainerObjectSelectionList<FiltersList.Entry>(
    Minecraft.getInstance(), screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 24
) {
    private val spacer = SpacerEntry()
    private val newEntry = NewEntry()

    init {
        reload()
    }

    fun reload() {
        clearEntries()
        for (entry in screen.filters) {
            this.addEntry(FilterEntry(entry))
        }

        addEntry(spacer)
        addEntry(newEntry)
    }

    override fun getRowWidth(): Int = 340

    fun apply() {
        screen.filters.clear()
        screen.filters.addAll(
            children().stream().filter { e -> e is FilterEntry }
                .map { e -> e as FilterEntry }
                .map { e -> e.editBox.value }
                .filter { s -> !s.isBlank() }.toList()
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        children().stream().filter { e -> e is FilterEntry }
            .map { e -> (e as FilterEntry).editBox }.forEach { e -> e.setFocused(false) }
        return super.mouseClicked(event, doubleClick)
    }

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

    inner class FilterEntry(filter: String) : Entry() {
        val editBox: EditBox = EditBox(screen.font, 0, 0, 200, 20, Component.translatable("autoboop.filters.edit"))
        private val removeButton: Button

        init {
            editBox.setValue(filter)
            editBox.setMaxLength(16)
            this.removeButton = Button.builder(REMOVE_BUTTON_TITLE) {
                removeEntry(this)
                apply()
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

            this.editBox.setPosition(getContentX(), j)
            this.editBox.setWidth(i - getContentX() - 4)
            this.editBox.extractWidgetRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        }

        override fun children(): List<GuiEventListener> = ImmutableList.of(this.editBox, removeButton)

        override fun narratables(): List<NarratableEntry> = ImmutableList.of(this.editBox, removeButton)
    }

    inner class NewEntry : Entry() {

        private val addButton: Button

        init {
            this.addButton = Button.builder(Component.translatable("autoboop.filters.add")) {
                removeEntry(spacer)
                removeEntry(newEntry)
                addEntry(FilterEntry(""))
                addEntry(spacer)
                addEntry(newEntry)
                apply()
                setScrollAmount(maxScrollAmount().toDouble())
            }.bounds(0, 0, 150, 20)
                .build()
        }

        override fun narratables(): List<NarratableEntry> = listOf(addButton)

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val i = scrollBarX() - getContentWidth() / 2 - 10 - addButton.width / 2
            val j = getContentY() - 2
            this.addButton.setPosition(i, j)
            this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        }

        override fun children(): List<GuiEventListener> = listOf(addButton)
    }

    companion object {
        private val REMOVE_BUTTON_TITLE = Component.translatable("autoboop.filters.remove")
    }
}
