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

import java.util.Objects
import java.util.function.Supplier
import java.util.stream.Stream

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemClass
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.ItemUtil

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ArrowHud : TextHudEntry(20, 22, true) {
    private val dynamic = BooleanOption("dynamic", false)
    private val allArrowTypes = BooleanOption("allArrowTypes", false)
    private val hideIfEmpty: BooleanOption = DefaultOptions.getHideIfEmpty()

    private var arrows = 0
    private var currentArrow: AxoItemStack? = if (BridgeVersion.is26OrLater()) null else AxoItemStack.of(AxoItems.ARROW)

    override fun render(graphics: AxoRenderContext, delta: Float) {
        val player = client.`br$getPlayer`()

        if (dynamic.get() && player != null) {
            val mainHand = player.`br$getInventory`().`br$getMainHand`().`br$getItem`()

            if (!mainHand.`br$is`(AxoItemClass.RANGED_WEAPON)) {
                if (BridgeVersion.version() == BridgeVersion.V1_8) {
                    return
                }
                val offHand = player.`br$getInventory`().`br$getOffHand`().`br$getItem`()
                if (!offHand.`br$is`(AxoItemClass.RANGED_WEAPON)) {
                    return
                }
            }
        }
        if (currentArrow == null) return
        if (hideIfEmpty.get() && !isAllArrowTypes() && currentArrow!!.`br$isEmpty`()) {
            return
        }

        super.render(graphics, delta)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val pos = contentPos
        graphics.`br$renderGuiItemModel`(currentArrow!!, pos.x() + 2, pos.y() + 2)
        graphics.`br$renderGuiItemOverlay`(currentArrow!!, pos.x() + 2, pos.y() + 2, arrows.toString())
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                val pos = contentPos
                graphics.`br$drawCenteredString`(
                    getName(), pos.x() + getContentWidth() / 2, pos.y() + getContentHeight() / 2, textColor.get()
                )
                return
            }
        }
        val pos = contentPos
        val dummy = DUMMY.get()
        graphics.`br$renderGuiItemModel`(dummy, pos.x() + 2, pos.y() + 2)
        graphics.`br$renderGuiItemOverlay`(dummy, pos.x() + 2, pos.y() + 2, "64")
    }

    override fun tickable(): Boolean = true

    private fun isAllArrowTypes(): Boolean = BridgeVersion.version() != BridgeVersion.V1_8 && allArrowTypes.get()

    override fun tick() {
        if (client.`br$getPlayer`() != null) {
            val projectileItem = client.`br$getPlayer`()!!.`br$getProjectileItem`()
            if (!isAllArrowTypes() && projectileItem != null) {
                currentArrow = AxoItemStack.of(projectileItem)
            } else {
                currentArrow = AxoItemStack.of(AxoItems.ARROW)
            }
        }

        if (isAllArrowTypes()) {
            arrows = ARROW_TYPES.stream().mapToInt { x: AxoItem -> ItemUtil.getTotal(client, x) }.sum()
        } else {
            if (currentArrow == null) return
            arrows = ItemUtil.getTotal(client, currentArrow!!.`br$getItem`())
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(dynamic)
        options.add(hideIfEmpty)

        if (BridgeVersion.version() != BridgeVersion.V1_8) {
            options.add(allArrowTypes)
        }

        return options
    }

    override fun getId(): AxoIdentifier = ID

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "arrowhud")
        private val ARROW_TYPES: List<AxoItem> = Stream.of<AxoItem?>(
            AxoItems.ARROW,
            AxoItems.TIPPED_ARROW,
            AxoItems.SPECTRAL_ARROW
        ).filter { Objects.nonNull(it) }.map { it!! }.toList()
        private val DUMMY: Supplier<AxoItemStack> = CommonUtil.memoize { AxoItemStack.of(AxoItems.ARROW, 1) }
    }
}
