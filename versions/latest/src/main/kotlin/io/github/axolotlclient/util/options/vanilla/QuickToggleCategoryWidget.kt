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

package io.github.axolotlclient.util.options.vanilla

import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.OptionCategoryImpl
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.BooleanWidget
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.CategoryWidget
import io.github.axolotlclient.util.options.ForceableBooleanOption
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

@Suppress("unused")
class QuickToggleCategoryWidget(x: Int, y: Int, width: Int, height: Int, category: OptionCategoryImpl) :
    CategoryWidget(x, y, width, height, category) {

    private var enabledButton: BooleanWidget? = null

    init {
        category.options
            .asSequence()
            .filterIsInstance<BooleanOption>()
            .firstOrNull { it.name == "enabled" }
            ?.let { booleanOption ->
                enabledButton = BooleanWidget(x + (width - 33), y + 3, 30, height - 5, booleanOption)
                enabledButton!!.active = !(booleanOption is ForceableBooleanOption && booleanOption.isForceOff)
            }
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        val button = enabledButton
        if (button != null && button.isMouseOver(mouseX, mouseY)) {
            this.isHovered = false
            return true
        }
        return super.isMouseOver(mouseX, mouseY)
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractContents(graphics, mouseX, mouseY, delta)

        val button = enabledButton
        if (button != null) {
            button.y = y + 2
            button.update()
            button.extractRenderState(graphics, mouseX, mouseY, delta)
        }
    }

    override fun extractScrollingStringOverContents(activeTextCollector: ActiveTextCollector, component: Component, i: Int) {
        val j = this.x + i
        var k = this.x + this.width - i
        if (enabledButton != null) {
            k -= enabledButton!!.width + 4
        }
        activeTextCollector.acceptScrollingWithDefaultCenter(component, j, k, this.y, this.y + this.height)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val button = enabledButton
        if (button != null && button.isHoveredOrFocused) {
            playDownSound(Minecraft.getInstance().soundManager)
            button.onPress(event)
            return true
        }
        return this.isHovered && super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!this.active || !this.visible) {
            return false
        } else if (!event.isSelection) {
            return false
        } else {
            this.playDownSound(Minecraft.getInstance().soundManager)
            onPress(event)
            return true
        }
    }
}
