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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.util.ItemUtil

/**
 * Obsidian inventory count (Alpine "obsidian_count" module, parity spec M4): total
 * obsidian across the player's inventory. Alpine carries no own settings for this
 * beyond the shared HUD style block; the inventory is scanned once per tick instead
 * of per frame (ArrowHud pattern).
 */
class ObsidianCountHud : SimpleTextHudEntry(true) {
    private val hideIfEmpty: BooleanOption = DefaultOptions.getHideIfEmpty()

    private var count = 0

    override fun tickable(): Boolean = true

    override fun tick() {
        count = ItemUtil.getTotal(client, AxoItems.OBSIDIAN)
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (client.`br$getPlayer`() == null) {
            return
        }
        if (hideIfEmpty.get() && count == 0) {
            return
        }
        super.render(ctx, delta)
    }

    override fun getValue(): String = count.toString()

    override fun getPlaceholderValue(): String = "64"

    override fun getLabel(): String = AxoI18n.translate("obsidiancounthud.obsidian")

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hideIfEmpty)
        return options
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("imperiumclient", "obsidiancounthud")
    }
}
