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

package io.github.axolotlclient.modules.hud.gui.hud.item

import java.util.function.Supplier

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.ItemUtil

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ItemUpdateHud : TextHudEntry(200, 11 * 6 - 2, true) {
    private val timeout = IntegerOption("timeout", 6, 1, 60)
    private val bracketColor = ColorOption("itemupdatehud.bracket_color", Colors.DARK_GRAY)
    private val hideIfEmpty: BooleanOption = DefaultOptions.getHideIfEmpty()
    private var oldItems: List<ItemUtil.ItemStorage> = ArrayList()
    private var removed: ArrayList<ItemUtil.TimedItemStorage> = ArrayList()
    private var added: ArrayList<ItemUtil.TimedItemStorage> = ArrayList()

    override fun tickable(): Boolean = true

    override fun tick() {
        if (client.`br$getWorld`() != null) {
            update()
        }
    }

    fun update() {
        this.removed = ItemUtil.removeOld(removed, timeout.get() * 1000)
        this.added = ItemUtil.removeOld(added, timeout.get() * 1000)
        updateAdded()
        updateRemoved()
        oldItems = ItemUtil.storageFromItem(ItemUtil.getItems(client))
    }

    private fun updateAdded() {
        ItemUtil.compare(ItemUtil.storageFromItem(ItemUtil.getItems(client)), oldItems).stream()
            .map { it.timed() }
            .forEach { stack ->
                val item = ItemUtil.getTimedItemFromItem(stack.stack, this.added)
                if (item.isPresent) {
                    item.get().incrementTimes(stack.times)
                } else {
                    this.added.add(stack)
                }
            }

        this.added.sortBy { it.getPassedTime() }
    }

    private fun updateRemoved() {
        ItemUtil.compare(oldItems, ItemUtil.storageFromItem(ItemUtil.getItems(client))).stream()
            .map { it.timed() }
            .forEach { stack ->
                val item = ItemUtil.getTimedItemFromItem(stack.stack, this.removed)
                if (item.isPresent) {
                    item.get().incrementTimes(stack.times)
                } else {
                    this.removed.add(stack)
                }
            }

        this.removed.sortBy { it.getPassedTime() }
    }

    private fun renderInternal(
        context: AxoRenderContext,
        added: List<ItemUtil.TimedItemStorage>,
        removed: List<ItemUtil.TimedItemStorage>
    ) {
        val openBracket = AxoText.literal("[").`br$color`(bracketColor.get().toInt())
        val closingBracket = AxoText.literal("]").`br$color`(bracketColor.get().toInt())
        val deltaY = context.`br$getFont`().`br$getFontHeight`() + 2

        val pos = contentPos
        var lastY = 1
        var entryCount = 0

        for (item in added) {
            if (entryCount > 5) {
                return
            }

            val message = AxoText.literal("+ ")
                .`br$append`(openBracket)
                .`br$append`(item.times)
                .`br$append`(closingBracket)
                .`br$append`(" ")
                .`br$append`(item.stack.`br$getHoverName`())

            context.`br$drawString`(message, pos.x, pos.y + lastY, ClientColors.SELECTOR_GREEN.toInt(), shadow.get())

            lastY += deltaY
            entryCount++
        }

        for (item in removed) {
            if (entryCount > 5) {
                return
            }

            val message = AxoText.literal("- ")
                .`br$append`(openBracket)
                .`br$append`(item.times)
                .`br$append`(closingBracket)
                .`br$append`(" ")
                .`br$append`(item.stack.`br$getHoverName`())

            context.`br$drawString`(message, pos.x, pos.y + lastY, ClientColors.SELECTOR_RED.toInt(), shadow.get())

            lastY += deltaY
            entryCount++
        }
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (hideIfEmpty.get() && added.isEmpty() && removed.isEmpty()) {
            return
        }
        super.render(ctx, delta)
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        renderInternal(context, added, removed)
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                val pos = contentPos
                context.`br$drawCenteredString`(
                    getName(), pos.x() + getContentWidth() / 2, pos.y() + getContentHeight() / 2, textColor.get()
                )
                return
            }
        }
        renderInternal(context, PLACEHOLDER_ADDED.get(), PLACEHOLDER_REMOVED.get())
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hideIfEmpty)
        options.add(timeout)
        options.add(bracketColor)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "itemupdatehud")

        private val PLACEHOLDER_ADDED: Supplier<List<ItemUtil.TimedItemStorage>> = CommonUtil.memoize {
            listOf(ItemUtil.TimedItemStorage(AxoItemStack.of(AxoItems.DIAMOND, 2), 0))
        }

        private val PLACEHOLDER_REMOVED: Supplier<List<ItemUtil.TimedItemStorage>> = CommonUtil.memoize {
            listOf(ItemUtil.TimedItemStorage(AxoItemStack.of(AxoItems.EMERALD, 3), 0))
        }
    }
}
