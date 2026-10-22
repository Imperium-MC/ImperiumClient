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

package io.github.axolotlclient.modules.inventorySearch

import java.util.Locale

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.AbstractModule
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import kotlin.math.max

/**
 * Alpine parity: a search bar overlaid on any open container screen. Typing highlights the slots
 * whose item name matches (case-insensitive "contains") and dims the rest. The overlay is purely
 * visual — the container keeps its layout and stays usable; only key events are captured while
 * the search field is up.
 */
class InventorySearch : AbstractModule() {
    @JvmField
    val enabled = BooleanOption("enabled", true)
    @JvmField
    val searchBarPosition = EnumOption("search_bar_position", BarPosition::class.java, BarPosition.TOP)
    @JvmField
    val searchBarWidth = IntegerOption("search_bar_width", 140, 60, 300)
    @JvmField
    val searchBarHeight = IntegerOption("search_bar_height", 16, 10, 24)
    @JvmField
    val highlightSlot = BooleanOption("highlight_slot", true)
    @JvmField
    val highlightColor = ColorOption("highlight_color", Color(0xFFD4AF37.toInt()))
    @JvmField
    val dimColor = ColorOption("dim_color", Color(0x96000000.toInt()))

    @JvmField
    val searchKey: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.inventory_search.open")

    private val category = OptionCategory.create("inventorySearch")

    private var active = false
    private var searchField: EditBox? = null

    override fun init() {
        category.add(enabled, searchBarPosition, searchBarWidth, searchBarHeight, highlightSlot, highlightColor, dimColor)

        AxolotlClient.config().general.add(category)
    }

    fun isActive(): Boolean = active

    /**
     * Key routing for container screens. The overlay opens on the bound search key; while it is up,
     * every key event is swallowed by the search field so nothing leaks into the container (no
     * hotbar swaps, no E-to-close). Escape or the search key again dismisses it.
     */
    fun onKeyPressed(event: KeyEvent): Boolean {
        if (!enabled.get()) {
            return false
        }
        val key = searchKey as KeyMapping
        if (active) {
            if (event.key() == InputConstants.KEY_ESCAPE || key.matches(event)) {
                close()
            } else if (searchField != null) {
                searchField!!.keyPressed(event)
            }
            return true
        }
        if (key.matches(event)) {
            open()
            return true
        }
        return false
    }

    fun onCharTyped(event: CharacterEvent): Boolean {
        if (active && searchField != null) {
            searchField!!.charTyped(event)
        }
        return active
    }

    /** Clicks on the search bar focus it; everything else falls through to the container. */
    fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || searchField == null) {
            return false
        }
        val x = event.x().toDouble()
        val y = event.y().toDouble()
        if (x >= searchField!!.getX() && x < searchField!!.getX() + searchField!!.getWidth() &&
            y >= searchField!!.getY() && y < searchField!!.getY() + searchField!!.getHeight()
        ) {
            searchField!!.setFocused(true)
            searchField!!.onClick(event, doubleClick)
            return true
        }
        return false
    }

    fun renderOverlay(
        screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor,
        mouseX: Int, mouseY: Int, partialTick: Float, leftPos: Int, topPos: Int, imageWidth: Int, imageHeight: Int
    ) {
        if (!active || !enabled.get() || searchField == null) {
            return
        }
        positionBar(screen, leftPos, topPos, imageWidth, imageHeight)

        val query = searchField!!.value.trim().lowercase(Locale.ROOT)
        if (query.isNotEmpty()) {
            val ctx = graphics as AxoRenderContext
            val dim = dimColor.get().toInt()
            val fill = highlightColor.get().withAlpha(0x40).toInt()
            val outline = highlightColor.get().toInt()
            val size = AbstractContainerMenu.SLOT_SIZE
            for (slot in screen.getMenu().slots) {
                val x = leftPos + slot.x
                val y = topPos + slot.y
                if (!slot.hasItem() || !matches(slot, query)) {
                    graphics.fill(x, y, x + size, y + size, dim)
                } else if (highlightSlot.get()) {
                    ctx.`br$fillRectRound`(x, y, size, size, fill, 2f)
                    ctx.`br$outlineRectRound`(x, y, size, size, outline, 2f)
                }
            }
        }
        // Drawn after the slot tints, still below the carried item and the tooltip.
        searchField!!.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick)
    }

    fun onScreenRemoved() {
        close()
    }

    private fun open() {
        active = true
        searchField = EditBox(
            client.font, 0, 0, searchBarWidth.get(), searchBarHeight.get(),
            Component.translatable("inventory_search.hint")
        )
        searchField!!.setMaxLength(64)
        searchField!!.setHint(Component.translatable("inventory_search.hint"))
        searchField!!.setCanLoseFocus(false)
        searchField!!.setFocused(true)
    }

    private fun close() {
        active = false
        searchField = null
    }

    private fun positionBar(screen: AbstractContainerScreen<*>, leftPos: Int, topPos: Int, imageWidth: Int, imageHeight: Int) {
        val width = searchBarWidth.get()
        val height = searchBarHeight.get()
        val x = leftPos + (imageWidth - width) / 2
        val y = if (searchBarPosition.get() == BarPosition.TOP) topPos - height - 4 else topPos + imageHeight + 4
        searchField!!.setX(x)
        searchField!!.setY(Mth.clamp(y, 2, max(2, screen.height - height - 2)))
    }

    private fun matches(slot: Slot, query: String): Boolean {
        val name = ChatFormatting.stripFormatting(slot.item.hoverName.string)
        return name != null && name.lowercase(Locale.ROOT).contains(query)
    }

    /** Current query text (empty while the overlay is closed). */
    fun query(): String = if (searchField == null) "" else searchField!!.value

    /** Number of slots the current query matches; -1 for a non-container screen or empty query. */
    fun matchingSlots(screen: Screen): Int {
        if (screen !is AbstractContainerScreen<*>) {
            return -1
        }
        val query = query().trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return -1
        }
        var count = 0
        for (slot in screen.getMenu().slots) {
            if (slot.hasItem() && matches(slot, query)) {
                count++
            }
        }
        return count
    }

    enum class BarPosition {
        TOP,
        BOTTOM;

        override fun toString(): String = "search_bar_position." + name.lowercase(Locale.ROOT)
    }

    companion object {
        private val Instance = InventorySearch()

        @JvmStatic
        fun getInstance(): InventorySearch = Instance
    }
}
