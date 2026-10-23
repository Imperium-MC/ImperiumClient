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

package io.github.axolotlclient.modules.hypixel.bedwars

import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class SessionStatsHudEntryConfigScreen(private val parent: Screen?) : Screen(tr("title")) {

    private val layout = HeaderAndFooterLayout(this)
    private val hud: SessionStatisticsOverlay = BedwarsMod.getInstance().sessionStatsOverlay
    private val list: EntryList = EntryList(minecraft!!, layout.width, layout.contentHeight, layout.headerHeight, 24)

    override fun onClose() {
        minecraft!!.gui.setScreen(parent)
        AxolotlClientCommon.getInstance().saveConfig()
    }

    override fun init() {
        layout.addTitleHeader(title, font)
        layout.addToContents(list)
        layout.addToFooter(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())
        repositionElements()
        layout.visitWidgets(this::addRenderableWidget)
    }

    override fun repositionElements() {
        layout.arrangeElements()
        list.updateSize(width, layout)
    }

    private inner class EntryList(
        minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int
    ) : ContainerObjectSelectionList<ListEntry>(minecraft, width, height, y, itemHeight) {

        private val rowWidth: Int =
            hud.entries.stream().mapToInt { e -> font.width(e.name as Component) }.max().orElse(100) + 180

        init {
            hud.entries.forEach { e -> addEntry(ListEntry(e, this)) }
        }

        fun moveEntry(entry: ListEntry, direction: Int) {
            val index = children().indexOf(entry)
            val next = index + direction
            swap(index, next)
            val v = hud.entries[next]
            hud.entries[index] = v
            hud.entries[next] = entry.entry
        }

        override fun getRowWidth(): Int = rowWidth
    }

    private inner class ListEntry(
        val entry: SessionStatisticsOverlay.SessionStatsEntry, val list: EntryList
    ) : ContainerObjectSelectionList.Entry<ListEntry>() {
        private val name: StringWidget
        private val showHide: CycleButton<Boolean>
        private val up: Button
        private val down: Button

        init {
            name = StringWidget(entry.name as Component, font)
                .setMaxWidth(150, StringWidget.TextOverflow.SCROLLING)
            showHide = CycleButton.booleanBuilder(tr("entry.hide"), tr("entry.show"), entry.enabled.get())
                .displayOnlyValue()
                .create(0, 0, 50, 20, Component.empty()) { _, v: Boolean -> entry.enabled.set(v) }
            up = Button.builder(tr("entry.move_up")) {
                minecraft!!.execute(Runnable { list.moveEntry(this, -1) })
            }.width(50).build()
            down = Button.builder(tr("entry.move_down")) {
                minecraft!!.execute(Runnable { list.moveEntry(this, 1) })
            }.width(50).build()
        }

        override fun narratables(): List<NarratableEntry> = listOf(name, showHide, up, down)

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, isHovering: Boolean, partialTick: Float
        ) {
            val x = getContentX()
            val y = getContentY()
            name.setPosition(x, y + getContentHeight() / 2 - name.height / 2)
            val right = getContentRight()
            down.setPosition(right - down.width, y)
            up.setPosition(down.x - up.width, y)
            showHide.setPosition(up.x - showHide.width, y)

            val size = list.children().size
            val index = list.children().indexOf(this)

            down.active = index != size - 1
            up.active = index != 0

            name.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            down.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            up.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            showHide.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        }

        override fun children(): List<GuiEventListener> = listOf(name, showHide, up, down)
    }

    companion object {
        private fun tr(key: String): Component = Component.translatable("bedwars.session_stats.configure.$key")
    }
}
