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
import java.util.stream.IntStream
import java.util.stream.Stream

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.util.CommonUtil

class InventoryHud : BoxHudEntry(164, 56, true), DynamicallyPositionable {
    private val dynamic = BooleanOption("dynamic", false)
    private val itemBackground = BooleanOption("inventoryhud.item_background", true)
    private val itemBackgroundColor = ColorOption("inventoryhud.item_background_color", backgroundColor!!.getDefault())
    private val alwaysShowItemBackgrounds = BooleanOption("inventoryhud.always_show_item_backgrounds", false)
    private val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(AnchorPoint.MIDDLE_MIDDLE, this)

    init {
        val max = (ITEM_TILE_SIZE + 2) / 2
        backgroundRounding = IntegerOption("background_rounding", max, 1, max)
    }

    override fun getDefaultX(): Double = 0.5

    override fun getDefaultY(): Double = 0.76

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (dynamic.get()) {
            var render = false
            for (stack in client.`br$getPlayer`()!!.`br$getInventory`().`br$getNonEquipmentItems`()) {
                if (stack != null && !stack.`br$isEmpty`()) {
                    render = true
                    break
                }
            }

            if (!render) return
        }
        super.render(ctx, delta)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        render(graphics, client.`br$getPlayer`()!!.`br$getInventory`().`br$getNonEquipmentItems`())
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                val pos = contentPos
                graphics.`br$drawCenteredString`(
                    getName(), pos.x() + getContentWidth() / 2, pos.y() + getContentHeight() / 2, -1
                )
                return
            }
        }
        render(graphics, PLACEHOLDER.get())
    }

    private fun render(graphics: AxoRenderContext, inventorySlots: List<AxoItemStack?>) {
        val pos = contentPos
        val x = pos.x() + 2
        val y = pos.y() + 2

        for (i in inventorySlots.indices) {
            val stack = inventorySlots[i]
            //if (stack != null && !stack.br$isEmpty()) {
            renderStack(graphics, x + (i % 9) * ITEM_SIZE, y + (i / 9) * ITEM_SIZE, stack)
            //}
        }
    }

    private fun renderStack(graphics: AxoRenderContext, x: Int, y: Int, itemStack: AxoItemStack?) {
        val empty = itemStack == null || itemStack.`br$isEmpty`()
        if ((!empty || alwaysShowItemBackgrounds.get()) && itemBackground.get() && itemBackgroundColor.get().alpha > 0) {
            if (roundBackground!!.get()) {
                graphics.`br$fillRectRound`(
                    x, y, ITEM_TILE_SIZE, ITEM_TILE_SIZE, itemBackgroundColor.get(),
                    minOf(backgroundRounding!!.get().toFloat(), ITEM_TILE_SIZE / 2f)
                )
            } else {
                graphics.`br$fillRect`(x, y, ITEM_TILE_SIZE, ITEM_TILE_SIZE, itemBackgroundColor.get().toInt())
            }
        }
        if (empty) return

        graphics.`br$renderGuiItemModel`(itemStack!!, x, y)
        graphics.`br$renderGuiItemOverlay`(itemStack, x, y, "")
    }

    override fun getId(): AxoIdentifier = ID

    override fun getAnchor(): AnchorPoint = anchor.get()

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.addAll(listOf(anchor, hide, dynamic, itemBackground, itemBackgroundColor, alwaysShowItemBackgrounds))
        return options
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "inventoryhud")
        private val PLACEHOLDER: Supplier<List<AxoItemStack?>> = CommonUtil.memoize {
            Stream.of(
                IntStream.range(0, 9).mapToObj<AxoItemStack?> { AxoItemStack.of(AxoItems.STONE) },
                IntStream.range(0, 9).mapToObj<AxoItemStack?> { null },
                Stream.of(
                    AxoItemStack.of(AxoItems.STONE_SWORD),
                    AxoItemStack.of(AxoItems.STONE_PICKAXE),
                    AxoItemStack.of(AxoItems.STONE_AXE),
                    AxoItemStack.of(AxoItems.STONE_SHOVEL),
                    AxoItemStack.of(AxoItems.STONE_HOE),
                    null, null, null,
                    AxoItemStack.of(AxoItems.GLOWSTONE_DUST, 63)
                )
            ).flatMap { x -> x }.toList()
        }

        private const val ITEM_SIZE = 18
        private const val ITEM_TILE_SIZE = 16
    }
}
