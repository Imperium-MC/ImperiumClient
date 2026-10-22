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
import io.github.axolotlclient.api.chat.ChatWidget
import io.github.axolotlclient.api.handlers.ChatHandler
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.util.AlphabeticalComparator
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class ChatsSidebar(override val parent: Screen?) :
    Screen(Component.translatable("api.chats.sidebar")), ContextMenuScreen {

    private var sidebarAnimX = 0
    private var sidebarWidth = 0
    private var remove = false
    private var hasChat = false
    private var list: ListWidget? = null
    private var input: EditBox? = null
    private var channel: Channel? = null

    private var chatWidget: ChatWidget? = null

    private var contextMenu: ContextMenuContainer = ContextMenuContainer()

    override val menuContainer: ContextMenuContainer
        get() = contextMenu

    override val self: Screen
        get() = this

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (parent != null) {
            parent.extractRenderState(graphics, -1, -1, delta)
        }
        graphics.pose().pushMatrix()
        graphics.fill(sidebarAnimX, 0, sidebarWidth + sidebarAnimX, height, 0x99000000.toInt())

        graphics.text(font, Component.translatable("api.chats"), 10 + sidebarAnimX, 10, -1)

        if (hasChat) {
            graphics.fill(70 + sidebarAnimX, 0, 70 + sidebarAnimX + 1, height, 0xFF000000.toInt())
            graphics.text(font, channel!!.name, sidebarAnimX + 75, 20, -1)
            if (channel!!.isDM() && (channel as Channel.DM).receiver.status!!.isOnline()) {
                val receiver = (channel as Channel.DM).receiver
                graphics.text(
                    font,
                    ChatFormatting.ITALIC.toString() + receiver.status!!.title + ": " + receiver.status!!.description,
                    sidebarAnimX + 80, 30, 0x808080
                )
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta)
        contextMenu.extractRenderState(graphics, mouseX, mouseY, delta)

        animate()
        graphics.pose().popMatrix()
    }

    override fun extractBackground(
        guiGraphicsExtractor: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        if (parent != null) {
            super.extractBackground(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        }
    }

    override fun init() {
        removeChat()
        sidebarWidth = 70
        sidebarAnimX = -sidebarWidth

        if (parent != null) {
            parent.children().stream()
                .filter { element: GuiEventListener -> element is AbstractButton }
                .map { e -> e as AbstractButton }
                .filter { e -> e.message == Component.translatable("api.chats") }
                .forEach { e -> e.visible = false }
        }

        addRenderableWidget(
            ListWidget(ChannelRequest.getChannelList(), 10, 30, 50, height - 70).also { list = it }
        )

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { remove() }
                .bounds(10 - sidebarWidth, height - 30, 50, 20).build()
        )
        // fresh container per init, matching the original (rebuildWidgets re-runs init)
        contextMenu = ContextMenuContainer()
        addWidget(contextMenu)
    }

    fun remove() {
        remove = true
    }

    override fun removed() {
        if (chatWidget != null) {
            chatWidget!!.remove()
        }
    }

    override fun isPauseScreen(): Boolean {
        return parent != null && parent.isPauseScreen
    }

    private fun animate() {
        if (sidebarAnimX < 0 && !remove) {
            if (sidebarAnimX > -ANIM_STEP) {
                sidebarAnimX = -ANIM_STEP
            }
            sidebarAnimX += ANIM_STEP
            if (list != null) {
                list!!.visible = false
            }
            getButtons().forEach { button -> button.x = button.x + ANIM_STEP }
        } else if (remove) {
            if (sidebarAnimX < -sidebarWidth) {
                close()
            }
            sidebarAnimX -= ANIM_STEP
            if (list != null) {
                list!!.setX(list!!.x - ANIM_STEP)
            }
            getButtons().forEach { button -> button.x = button.x - ANIM_STEP }
            if (chatWidget != null) {
                chatWidget!!.setX(chatWidget!!.getX() - ANIM_STEP)
            }
        } else {
            if (list != null && !list!!.visible) {
                list!!.visible = true
            }
        }
    }

    fun getButtons(): List<AbstractWidget> {
        return children().stream()
            .filter { element: GuiEventListener -> element is AbstractWidget }
            .map { element -> element as AbstractWidget }
            .toList()
    }

    private fun close() {
        minecraft.gui.setScreen(parent)
        if (chatWidget != null) {
            chatWidget!!.remove()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (contextMenu.menu != null) {
            if (contextMenu.mouseClicked(event, doubleClick)) {
                return true
            }
            contextMenu.removeMenu()
        }
        if (event.x() > sidebarWidth) {
            remove()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    private fun removeChat() {
        hasChat = false
        chatWidget?.let { removeWidget(it) }
        input?.let { removeWidget(it) }
    }

    private fun addChat(channel: Channel) {
        if (hasChat) {
            removeChat()
        }
        hasChat = true
        list!!.elements.forEach { b -> b.active = true }
        this.channel = channel
        val w: Int
        if (channel.isDM()) {
            val chatUser = (channel as Channel.DM).receiver
            w = maxOf(
                font.width(chatUser.status!!.title + ": " + chatUser.status!!.description) + 5,
                font.width(channel.name!!)
            )
        } else {
            w = font.width(channel.name!!)
        }
        sidebarWidth = minOf(maxOf(width * 5 / 12, w + 5), width / 2)
        chatWidget = ChatWidget(channel, 75, 50, sidebarWidth - 80, height - 100, this)
        addRenderableWidget(chatWidget!!)
        val input: EditBox = object : EditBox(
            font, 75, height - 30, sidebarWidth - 80, 20, Component.translatable("api.friends.chat.input")
        ) {
            override fun keyPressed(event: KeyEvent): Boolean {
                if (event.key() == InputConstants.KEY_RETURN) {
                    ChatHandler.getInstance().sendMessage(channel, this.value)
                    this.setValue("")
                    return true
                }
                return super.keyPressed(event)
            }
        }
        this.input = input
        addRenderableWidget(input)
        input.setSuggestion(input.message.getString())
        input.setResponder { s ->
            if (s.isEmpty() && !input.isFocused) {
                input.setSuggestion(input.message.getString())
            } else {
                input.setSuggestion("")
            }
        }
        input.setMaxLength(1024)
    }

    private inner class ListWidget(
        future: CompletableFuture<List<Channel>>,
        x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int
    ) : AbstractContainerEventHandler(), Renderable, GuiEventListener, NarratableEntry {

        val elements: MutableList<AbstractButton> = ArrayList()
        private val entryHeight = 25
        protected var hovered: Boolean = false
        private var xField: Int = x
        private var scrollAmount = 0
        var visible = false

        val x: Int
            get() = xField

        init {
            val buttonY = AtomicInteger(y)
            future.thenAccept { list ->
                elements.addAll(
                    list.stream()
                        .sorted { u1, u2 -> AlphabeticalComparator().compare(u1.name!!, u2.name!!) }
                        .map { channel ->
                            Button.builder(
                                Component.literal(channel.name!!),
                                { buttonWidget ->
                                    addChat(channel)
                                    buttonWidget.active = false
                                }
                            )
                                .bounds(x, buttonY.getAndAdd(entryHeight), width, entryHeight - 5).build()
                        }.toList()
                )
            }
        }

        override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (visible) {
                graphics.pose().pushMatrix()
                graphics.enableScissor(x, y, x + width, y + height)

                val buttonY = AtomicInteger(y)
                elements.forEach { e ->
                    e.setY(buttonY.get() - scrollAmount)
                    e.extractRenderState(graphics, mouseX, mouseY, delta)
                    buttonY.getAndAdd(entryHeight)
                }

                graphics.disableScissor()
                graphics.pose().popMatrix()
            }
        }

        override fun children(): List<GuiEventListener> {
            return elements
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, amountX: Double, amountY: Double): Boolean {
            if (this.isMouseOver(mouseX, mouseY)) {
                if (elements.size * entryHeight > height) {
                    var a = scrollAmount
                    a -= (amountY * (entryHeight / 2f)).toInt()
                    scrollAmount = Mth.clamp(a, 0, elements.size * entryHeight - height)
                    return true
                }
            }
            return super<AbstractContainerEventHandler>.mouseScrolled(mouseX, mouseY, amountX, amountY)
        }

        override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
            hovered = visible && mouseX >= this.x && mouseY >= this.y &&
                mouseX < (this.x + this.width).toDouble() && mouseY < (this.y + this.height).toDouble()
            return hovered
        }

        override fun narrationPriority(): NarrationPriority {
            return if (this.hovered) NarrationPriority.HOVERED else NarrationPriority.NONE
        }

        override fun updateNarration(builder: NarrationElementOutput) {
        }

        fun setX(x: Int) {
            this.xField = x
            elements.forEach { e -> e.setX(x) }
        }
    }

    companion object {
        private const val ANIM_STEP = 8
    }
}
