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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ActionBarHud : TextHudEntry(115, 13, false) {

    @JvmField
    val timeShown = IntegerOption("timeshown", 60, 40, 300)

    @JvmField
    val customTextColor = BooleanOption("customtextcolor", false)

    private val mc: Minecraft = super.client as Minecraft

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
    }

    fun render(graphics: GuiGraphicsExtractor, actionBar: Component, color: Int) {
        graphics.text(
            mc.font, actionBar,
            (contentPos.x() + (getContentWidth() / 2f).roundToInt() - mc.font.width(actionBar) / 2f).toInt(),
            (contentPos.y() + 3f).toInt(),
            if (customTextColor.get()) {
                (if (textColor.get().alpha == 255) Color(
                    textColor.get().red, textColor.get().green, textColor.get().blue, ARGB.alpha(color)
                ).toInt() else textColor.get().toInt())
            } else {
                color
            },
            shadow.get()
        )
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        graphics.`br$drawString`(
            PLACEHOLDER,
            (contentPos.x() + (getContentWidth() / 2f).roundToInt() - mc.`br$getFont`()
                .`br$getWidth`(PLACEHOLDER) / 2f).toInt(),
            (contentPos.y() + 3f).toInt(), -1, false
        )
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(timeShown)
        options.add(customTextColor)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("kronhud", "actionbarhud")
        private const val PLACEHOLDER = "Action Bar"
    }
}
