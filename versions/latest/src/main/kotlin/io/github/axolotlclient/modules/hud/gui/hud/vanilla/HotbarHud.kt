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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import net.minecraft.resources.Identifier

class HotbarHud : TextHudEntry(182, 22, false) {
    init {
        supportsScaling = false
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        // this is just a matrix translate in InGameHudMixin
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        val pos = contentPos

        context.`br$drawCenteredString`(getName(), pos.x() + getWidth() / 2, pos.y() + getHeight() / 2 - 4, -1)
    }

    override fun getId(): Identifier = ID

    override fun overridesF3(): Boolean = true

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val list = ArrayList<Option<*>>()
        list.add(enabled)
        list.add(hide)
        list.add(anchor)
        return list
    }

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.BOTTOM_MIDDLE

    override fun getDefaultX(): Double = 0.5

    override fun getDefaultY(): Double = 0.96

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "hotbarhud")
    }
}
