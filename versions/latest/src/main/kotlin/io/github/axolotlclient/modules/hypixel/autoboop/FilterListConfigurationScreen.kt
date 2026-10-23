/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hypixel.autoboop

import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class FilterListConfigurationScreen(
    filters: List<String>,
    private val parent: Screen?
) : Screen(Component.translatable("autoboop.filters.configure")) {

    // AutoBoop always hands over its own mutable filter list and the screen edits it in
    // place (the reset button and apply-on-close write straight back into it, exactly like
    // the Java original did through its List-typed alias).
    internal val filters: MutableList<String> = filters as MutableList<String>

    val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this)
    private val filtersList: FiltersList = FiltersList(this)

    override fun init() {
        layout.addTitleHeader(title, font)
        layout.addToContents(filtersList)
        val resetButton = Button.builder(Component.translatable("autoboop.filters.clear")) {
            filters.clear()
            filtersList.reload()
            AxolotlClientCommon.getInstance().saveConfig()
        }.build()
        val linearLayout = layout.addToFooter(LinearLayout.horizontal().spacing(8))
        linearLayout.addChild(resetButton)
        linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE) { onClose() }.build())
        layout.visitWidgets(this::addRenderableWidget)
        repositionElements()
    }

    override fun repositionElements() {
        layout.arrangeElements()
        filtersList.updateSize(width, layout)
        filtersList.reload()
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
        filtersList.apply()
        AxolotlClientCommon.getInstance().saveConfig()
    }
}
