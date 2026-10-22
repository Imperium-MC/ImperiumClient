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
package io.github.axolotlclient.api

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.jetbrains.annotations.NotNull

class ContextMenuContainer : Renderable, GuiEventListener, NarratableEntry {

    var menu: ContextMenu? = null

    fun removeMenu() {
        menu = null
    }

    fun hasMenu(): Boolean {
        return menu != null
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        menu?.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun isFocused(): Boolean {
        return menu?.isFocused ?: false
    }

    override fun setFocused(focused: Boolean) {
        menu?.setFocused(focused)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        menu?.mouseMoved(mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (menu != null) {
            if (!menu!!.isMouseOver(event.x(), event.y())) {
                removeMenu()
                return true
            }
            if (menu!!.mouseClicked(event, doubleClick)) removeMenu()
            return true
        }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        return menu?.mouseReleased(event) ?: false
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        return menu?.mouseDragged(event, deltaX, deltaY) ?: false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amountX: Double, amountY: Double): Boolean {
        return menu?.mouseScrolled(mouseX, mouseY, amountX, amountY) ?: false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (menu != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                removeMenu()
                return true
            }
            return menu!!.keyPressed(event)
        }
        return false
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        return menu?.keyReleased(event) ?: false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        return menu?.charTyped(event) ?: false
    }

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
        return menu?.nextFocusPath(event) ?: super<GuiEventListener>.nextFocusPath(event)
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return menu?.isMouseOver(mouseX, mouseY) ?: false
    }

    override fun getCurrentFocusPath(): ComponentPath? {
        return menu?.getCurrentFocusPath() ?: super<GuiEventListener>.getCurrentFocusPath()
    }

    override fun getRectangle(): @NotNull ScreenRectangle {
        return menu?.rectangle ?: super<GuiEventListener>.getRectangle()
    }

    override fun narrationPriority(): NarrationPriority {
        return menu?.narrationPriority() ?: NarrationPriority.NONE
    }

    override fun updateNarration(builder: NarrationElementOutput) {
        menu?.updateNarration(builder)
    }
}
