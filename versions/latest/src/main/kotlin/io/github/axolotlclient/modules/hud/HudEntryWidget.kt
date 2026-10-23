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

package io.github.axolotlclient.modules.hud

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.util.ClientColors
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

class HudEntryWidget(private val entry: HudEntry) : Renderable, GuiEventListener, NarratableEntry {
    private var focused = false
    private var moving = false

    override fun setFocused(focused: Boolean) {
        this.focused = focused
        if (!focused) {
            moving = false
        }
    }

    override fun isFocused(): Boolean = entry.isEnabled() && focused

    override fun narrationPriority(): NarrationPriority =
        if (isFocused) NarrationPriority.FOCUSED else if (entry.isHovered()) NarrationPriority.HOVERED else NarrationPriority.NONE

    override fun updateNarration(narrationElementOutput: NarrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("hud.entry.name", entry.getName()))
        if (moving) {
            narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("hud.entry.usage.move"))
        } else {
            narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("hud.entry.usage"))
        }
    }

    override fun getRectangle(): ScreenRectangle {
        if (!entry.isEnabled()) {
            return ScreenRectangle.empty()
        }
        return ScreenRectangle(entry.getTrueX(), entry.getTrueY(), entry.getTrueWidth(), entry.getTrueHeight())
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = entry.isEnabled() && entry.isHovered()

    override fun extractRenderState(guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (isFocused) {
            val bounds = entry.getTrueBounds()
            guiGraphicsExtractor.`br$outlineRect`(
                bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
                if (moving) ClientColors.SELECTOR_RED.toInt() else -1
            )
        }
    }

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
        if (!entry.isEnabled()) {
            return null
        }
        return if (!this.isFocused) ComponentPath.leaf(this) else null
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val mc = Minecraft.getInstance()
        var consume = false
        val keyCode = event.key()
        if (moving) {
            consume = true
            val step = if (mc.hasControlDown()) 10 else 1
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_SPACE) {
                moving = false
            } else if (keyCode == InputConstants.KEY_UP) {
                entry.setY(entry.getRawTrueY() - step + entry.offsetTrueHeight())
            } else if (keyCode == InputConstants.KEY_DOWN) {
                entry.setY(entry.getRawTrueY() + step + entry.offsetTrueHeight())
            } else if (keyCode == InputConstants.KEY_LEFT) {
                entry.setX(entry.getRawTrueX() - step + entry.offsetTrueWidth())
            } else if (keyCode == InputConstants.KEY_RIGHT) {
                entry.setX(entry.getRawTrueX() + step + entry.offsetTrueWidth())
            } else {
                consume = false
            }
        }
        if (consume) {
            return true
        }
        consume = true
        if (keyCode == InputConstants.KEY_SPACE) {
            moving = true
        } else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            mc.gui.setScreen(ConfigStyles.createScreen(mc.gui.screen(), entry.getCategory()))
        } else if (keyCode == InputConstants.KEY_DELETE) {
            entry.setEnabled(false)
        } else {
            consume = false
        }
        return consume
    }
}
