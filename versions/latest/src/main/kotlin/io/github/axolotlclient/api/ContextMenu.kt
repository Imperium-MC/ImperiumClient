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
import io.github.axolotlclient.util.DrawUtil
import java.util.Optional
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.Style

class ContextMenu protected constructor(private val children: List<AbstractButton>) :
    ContainerEventHandler, Renderable, NarratableEntry {

    private var dragging = false
    private var focused: GuiEventListener? = null
    private var x = 0
    private var y = 0
    private val width: Int
    private val height: Int
    private var rendering = false

    init {
        var width = 0
        var height = 0
        for (d in children) {
            d.setY(height)
            height += d.height
            width = maxOf(width, d.width)
        }
        this.width = width
        this.height = height
    }

    override fun children(): List<GuiEventListener> {
        return children
    }

    fun entries(): List<AbstractButton> {
        return children
    }

    override fun isDragging(): Boolean {
        return dragging
    }

    override fun setDragging(dragging: Boolean) {
        this.dragging = dragging
    }

    override fun getFocused(): GuiEventListener? {
        return focused
    }

    override fun setFocused(child: GuiEventListener?) {
        focused?.setFocused(false)
        focused = child
        focused?.setFocused(true)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (!rendering) {
            y = mouseY
            x = mouseX
            rendering = true
        }
        val yStart = minOf(y + 2, graphics.guiHeight() - height - 2)
        val xStart = minOf(x + 2, graphics.guiWidth() - width - 2)
        var y = yStart + 1
        for (d in children) {
            d.setX(xStart + 1)
            d.setY(y)
            y += d.height
        }
        graphics.pose().pushMatrix()
        graphics.fill(xStart, yStart, xStart + width + 1, y, 0xDD1E1F22.toInt())
        DrawUtil.outlineRect(graphics, xStart, yStart, width + 1, y - yStart + 1, -1)
        for (c in children) {
            c.setWidth(width)
            c.extractRenderState(graphics, mouseX, mouseY, delta)
        }
        graphics.pose().popMatrix()
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return getChildAt(mouseX, mouseY).isPresent
    }

    override fun narrationPriority(): NarrationPriority {
        return if (isFocused) NarrationPriority.FOCUSED else NarrationPriority.HOVERED
    }

    override fun updateNarration(builder: NarrationElementOutput) {
        builder.add(NarratedElementType.TITLE, Component.translatable("api.context_menu"))
        entries().forEach { b -> builder.add(NarratedElementType.USAGE, b.message) }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val optional: Optional<GuiEventListener> = this.getChildAt(event.x(), event.y())
        if (optional.isPresent) {
            val guiEventListener = optional.get()
            if (guiEventListener.mouseClicked(event, doubleClick)) {
                this.setFocused(guiEventListener)
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    this.setDragging(true)
                }
                return true
            }
        }
        return false
    }

    class Builder {

        private val elements: MutableList<AbstractButton> = ArrayList()

        fun entry(name: Component, action: Button.OnPress): Builder {
            elements.add(ContextMenuEntryWithAction(name, action))
            return this
        }

        fun entry(widget: AbstractButton): Builder {
            elements.add(widget)
            return this
        }

        fun spacer(): Builder {
            elements.add(
                object : ContextMenuEntry(Component.literal("-----")) {
                    override fun updateWidgetNarration(builder: NarrationElementOutput) {
                    }
                }
            )
            return this
        }

        fun title(title: Component): Builder {
            elements.add(ContextMenuEntry(title))
            return this
        }

        fun build(): ContextMenu {
            return ContextMenu(elements)
        }
    }

    open class ContextMenuEntry(content: Component) :
        AbstractButton(0, 0, Minecraft.getInstance().font.width(content), 11, content.copy().withColor(0xFFDDDDDD.toInt())) {

        override fun onPress(input: InputWithModifiers) {
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE))
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            return false
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {
            builder.add(NarratedElementType.TITLE, message)
        }
    }

    class ContextMenuEntryWithAction(message: Component, onPress: OnPress) :
        Button(
            0, 0, Minecraft.getInstance().font.width(message) + 4, 11, message, onPress,
            DEFAULT_NARRATION
        ) {

        private val inactiveMessage: Component =
            ComponentUtils.mergeStyles(getMessage(), Style.EMPTY.withColor(0xFFA0A0A0.toInt()))

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (isHoveredOrFocused) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x55ffffff.toInt())
            }
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE))
        }

        override fun getMessage(): Component {
            return if (active) super.getMessage() else inactiveMessage
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
