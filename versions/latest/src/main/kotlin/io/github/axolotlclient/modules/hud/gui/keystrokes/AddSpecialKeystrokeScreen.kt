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

package io.github.axolotlclient.modules.hud.gui.keystrokes

import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class AddSpecialKeystrokeScreen(private val lastScreen: Screen?, val hud: KeystrokeHud) :
    Screen(TITLE) {

    private var keyBindsList: SpecialKeystrokeSelectionList? = null
    val layout = HeaderAndFooterLayout(this)

    override fun init() {
        layout.addTitleHeader(title, font)
        this.keyBindsList = this.layout.addToContents(SpecialKeystrokeSelectionList(this, this.minecraft!!))

        val linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8))
        linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE) { onClose() }.build())
        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.keyBindsList!!.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        minecraft!!.gui.setScreen(lastScreen)
    }

    companion object {
        private val TITLE = Component.translatable("keystrokes.stroke.add.special")
    }
}
