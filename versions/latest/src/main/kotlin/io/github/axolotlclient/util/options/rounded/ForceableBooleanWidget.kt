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

package io.github.axolotlclient.util.options.rounded

import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.widgets.PillBooleanWidget
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.InputWithModifiers

@Suppress("unused")
class ForceableBooleanWidget(x: Int, y: Int, width: Int, height: Int, private val option: ForceableBooleanOption) :
    PillBooleanWidget(x, y, width, height, option) {

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        this.active = !option.isForceOff
        super.extractContents(graphics, mouseX, mouseY, delta)
    }

    override fun onPress(mods: InputWithModifiers) {
        if (!option.isForceOff) {
            super.onPress(mods)
        }
    }
}
