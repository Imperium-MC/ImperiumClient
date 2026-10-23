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

package io.github.axolotlclient.modules.waypoints

import io.github.axolotlclient.api.SimpleTextInputScreen
import io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.floor

/**
 * Waypoint management screen (Alpine "waypoints" module, parity spec M2): lists every
 * waypoint with its coordinates and dimension, lets the player delete, enable/disable
 * (double-click or button) and add a new waypoint at the current position.
 */
class WaypointsScreen(private val parent: Screen?) :
    Screen(Component.translatable("waypoints.screen.title")) {

    private var list: WaypointListWidget? = null
    private lateinit var deleteButton: Button
    private lateinit var toggleButton: Button

    override fun init() {
        val list = addRenderableWidget(
            WaypointListWidget(this, minecraft, width, height, 40, height - 64, 26)
        )
        this.list = list
        list.populate()

        addRenderableWidget(
            Button.builder(Component.translatable("waypoints.add")) {
                minecraft.gui.setScreen(
                    SimpleTextInputScreen(
                        this,
                        Component.translatable("waypoints.add.title"),
                        Component.translatable("waypoints.add.name"),
                        WaypointsHud::addWaypointAtCurrentPosition
                    )
                )
            }.bounds(width / 2 - 155, height - 52, 150, 20).build()
        )

        toggleButton = addRenderableWidget(
            Button.builder(Component.translatable("waypoints.toggle")) {
                val entry = list.selected
                if (entry != null) {
                    val wp = entry.waypoint
                    WaypointManager.getInstance().replace(wp, wp.withEnabled(!wp.enabled))
                    refresh()
                }
            }.bounds(width / 2 + 5, height - 52, 150, 20).build()
        )

        deleteButton = addRenderableWidget(
            Button.builder(Component.translatable("selectServer.delete")) {
                val entry = list.selected
                if (entry != null) {
                    WaypointManager.getInstance().remove(entry.waypoint)
                    refresh()
                }
            }.bounds(width / 2 - 155, height - 28, 150, 20).build()
        )

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { minecraft.gui.setScreen(parent) }
                .bounds(width / 2 + 5, height - 28, 150, 20).build()
        )

        updateButtonStates()
    }

    private fun refresh() {
        minecraft.gui.setScreen(WaypointsScreen(parent))
    }

    private fun select(entry: WaypointEntry) {
        list!!.setSelected(entry)
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val selected = list!!.selected != null
        deleteButton.active = selected
        toggleButton.active = selected
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.centeredText(font, title, width / 2, 20, -1)
    }

    private class WaypointListWidget(
        val screen: WaypointsScreen, client: Minecraft, width: Int, height: Int, top: Int, bottom: Int,
        entryHeight: Int
    ) : ObjectSelectionList<WaypointEntry>(client, width, bottom - top, top, entryHeight) {

        fun populate() {
            WaypointManager.getInstance().waypoints.forEach { waypoint -> addEntry(WaypointEntry(screen, waypoint)) }
        }

        override fun getRowWidth(): Int = super.getRowWidth() + 100

        override fun isFocused(): Boolean = screen.focused == this
    }

    private class WaypointEntry(private val screen: WaypointsScreen, val waypoint: Waypoint) :
        ObjectSelectionList.Entry<WaypointEntry>() {

        private val client = Minecraft.getInstance()

        override fun getNarration(): Component = Component.literal(waypoint.name)

        override fun extractContent(
            graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float
        ) {
            val x = contentX
            val y = contentY

            // color swatch
            graphics.fill(x, y + 2, x + 8, y + 10, 0xFF000000.toInt() or waypoint.color)
            graphics.fill(x + 1, y + 3, x + 7, y + 9, waypoint.color)

            val state = if (waypoint.enabled) "waypoints.enabled" else "waypoints.disabled"
            graphics.text(client.font, waypoint.name, x + 14, y, waypoint.color)
            graphics.text(
                client.font, Component.translatable(state), x + 14 + client.font.width(waypoint.name) + 6, y,
                if (waypoint.enabled) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
            )

            val coords = "${floor(waypoint.x).toInt()} / ${floor(waypoint.y).toInt()} / ${floor(waypoint.z).toInt()}"
            val dimension = waypoint.dimension.removePrefix("minecraft:")
            graphics.text(client.font, "$coords  ($dimension)", x + 14, y + 12, 0xFFAAAAAA.toInt())
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            screen.select(this)
            if (doubleClick) {
                WaypointManager.getInstance().replace(waypoint, waypoint.withEnabled(!waypoint.enabled))
                screen.refresh()
            }
            return false
        }
    }
}
