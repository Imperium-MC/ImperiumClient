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
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.util.ItemUtil

class ResourceOverlay(private val mod: BedwarsMod) :
    BoxHudEntry(4 * 18 + 1, 18 + 1, true), DynamicallyPositionable {
    private val renderWhenRelevant = BooleanOption(ID.`br$getPath`() + ".renderWhenRelevant", true)
    private val hideIfEmpty = DefaultOptions.getHideIfEmpty()
    private val anchor = DefaultOptions.getAnchorPoint(this)

    override fun render(context: AxoRenderContext, delta: Float) {
        if (!renderWhenRelevant.get() || mod.inGame()) {
            if (hideIfEmpty.get() && RESOURCES.all { ItemUtil.getTotal(client, it) <= 0 }) {
                return
            }
            super.render(context, delta)
        }
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        draw(context) { s -> ItemUtil.getTotal(client, s) }
    }

    private fun draw(context: AxoRenderContext, countFunction: (AxoItem) -> Int) {
        val pos = contentPos
        var x = pos.x() + 1
        val y = pos.y() + 1
        for (item in RESOURCES) {
            val amount = countFunction(item)
            val stack = AxoItemStack.of(item, amount)
            if (amount > 0) {
                context.`br$renderGuiItemModel`(stack, x, y)
                context.`br$renderGuiItemOverlay`(stack, x, y, amount.toString(), -1, true)
                x += 18
            }
        }
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                val pos = contentPos
                context.`br$drawCenteredString`(
                    getName(),
                    pos.x() + getContentWidth() / 2,
                    pos.y() + getContentHeight() / 2 - context.`br$getFont`().`br$getFontHeight`() / 2,
                    -1
                )
                return
            }
        }
        draw(context) { PLACEHOLDER.getValue(it) }
    }

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(hideIfEmpty)
        options.add(renderWhenRelevant)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    companion object {
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "bedwars_resources")
        private val RESOURCES: List<AxoItem> =
            listOf(AxoItems.IRON_INGOT, AxoItems.GOLD_INGOT, AxoItems.DIAMOND, AxoItems.EMERALD)
        private val PLACEHOLDER: Map<AxoItem, Int> = mapOf(
            AxoItems.IRON_INGOT to 43,
            AxoItems.GOLD_INGOT to 7,
            AxoItems.DIAMOND to 7,
            AxoItems.EMERALD to 4
        )
    }
}
