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
package io.github.axolotlclient.api.chat

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.ContextMenu
import io.github.axolotlclient.api.ContextMenuScreen
import io.github.axolotlclient.api.handlers.ChatHandler
import io.github.axolotlclient.api.handlers.ChatHandler.NotificationsEnabler
import io.github.axolotlclient.api.requests.ChannelRequest
import io.github.axolotlclient.api.types.Channel
import io.github.axolotlclient.api.types.ChatMessage
import io.github.axolotlclient.modules.auth.Auth
import io.github.axolotlclient.util.ClientColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Comparator
import java.util.Objects
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence

class ChatWidget(
    private val channel: Channel,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    internal val screen: ContextMenuScreen
) : ObjectSelectionList<ChatLine>(Minecraft.getInstance(), width, height, y, 13) {

    private val messages: MutableList<ChatMessage> = ArrayList()
    private val client: Minecraft = Minecraft.getInstance()

    // The original Java class declared its own x/y/width/height fields whose lombok
    // accessors shadow AbstractWidget's position accessors; explicit accessor
    // overrides reproduce that exact double-storage behavior. (The constructor's
    // original setX(x + 5) call was a dead store - the field was reassigned right
    // after - so it is not reproduced.)
    private var xField: Int = x
    private var yField: Int = y
    private var widthField: Int = width
    private var heightField: Int = height

    override fun getX(): Int = xField

    override fun setX(value: Int) {
        xField = value
    }

    override fun getY(): Int = yField

    override fun setY(value: Int) {
        yField = value
    }

    override fun getWidth(): Int = widthField

    override fun setWidth(value: Int) {
        widthField = value
    }

    override fun getHeight(): Int = heightField

    override fun setHeight(value: Int) {
        heightField = value
    }

    init {
        channel.messages.forEach { addMessage(it) }

        ChatHandler.getInstance().messagesConsumer =
            Consumer { chatMessages: List<ChatMessage> -> chatMessages.forEach { addMessage(it) } }
        ChatHandler.getInstance().messageConsumer = Consumer { addMessage(it) }
        ChatHandler.getInstance().enableNotifications =
            NotificationsEnabler { message -> message.channelId != channel.id }

        setScrollAmount(maxScrollAmount().toDouble())
    }

    // bridge for the (top-level) entry classes: getHovered() is protected on
    // AbstractSelectionList and not callable from a non-subclass
    internal fun hoveredEntry(): ChatLine? = hovered

    override fun scrollBarX(): Int {
        return xField + widthField - 6
    }

    override fun getRowWidth(): Int {
        return widthField - 60
    }

    private fun addMessage(message: ChatMessage) {
        val list: List<FormattedCharSequence> =
            client.font.split(Component.literal(message.content!!), getRowWidth())

        val scrollToBottom = scrollAmount() == maxScrollAmount().toDouble()

        if (messages.isNotEmpty()) {
            val prev = messages.last()
            if (!(prev.sender == message.sender &&
                    prev.senderDisplayName == message.senderDisplayName)
            ) {
                addEntry(NameChatLine(this, message))
            } else {
                if (message.timestamp.epochSecond - prev.timestamp.epochSecond > 150) {
                    addEntry(NameChatLine(this, message))
                }
            }
        } else {
            addEntry(NameChatLine(this, message))
        }

        list.forEach { t -> addEntry(ChatLine(this, t, message)) }
        messages.add(message)

        sort(Comparator.comparingLong { c: ChatLine -> c.origin.timestamp.epochSecond })

        if (scrollToBottom) {
            setScrollAmount(maxScrollAmount().toDouble())
        }
        messages.sortWith(Comparator.comparingLong { value: ChatMessage -> value.timestamp.epochSecond })
    }

    private fun loadMessages() {
        val before: Long = if (messages.isNotEmpty()) {
            messages.first().timestamp.epochSecond
        } else {
            Instant.now().epochSecond
        }
        ChatHandler.getInstance().getMessagesBefore(channel, before)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amountX: Double, amountY: Double): Boolean {
        val scrollAmount = this.scrollAmount() - amountY * scrollRate()
        if (scrollAmount < 0) {
            loadMessages()
        }
        setScrollAmount(scrollAmount)
        return true
    }

    fun remove() {
        ChatHandler.getInstance().messagesConsumer = ChatHandler.DEFAULT_MESSAGES_CONSUMER
        ChatHandler.getInstance().messageConsumer = ChatHandler.DEFAULT_MESSAGE_CONSUMER
        ChatHandler.getInstance().enableNotifications = ChatHandler.DEFAULT
    }

    override fun extractSelection(guiGraphicsExtractor: GuiGraphicsExtractor, entry: ChatLine, i: Int) {
    }

    override fun isValidClickButton(mouseButtonInfo: MouseButtonInfo): Boolean {
        return true
    }
}

// file-level entries: Kotlin cannot reference a nested class of the declaring class in
// its own supertype list (ObjectSelectionList<ChatWidget.ChatLine>), so the entries
// live at file level and hold the owning widget explicitly (they were inner in Java)

open class ChatLine(
    private val widget: ChatWidget,
    val content: FormattedCharSequence,
    val origin: ChatMessage
) : ObjectSelectionList.Entry<ChatLine>() {

    protected val client: Minecraft = Minecraft.getInstance()

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            widget.selected = this
            return true
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            val builder: ContextMenu.Builder =
                ContextMenu.builder().title(Component.literal(origin.sender.name!!)).spacer()
            if (origin.sender != API.getInstance().self) {
                builder.entry(Component.translatable("api.friends.chat")) {
                    ChannelRequest.getOrCreateDM(origin.sender).whenCompleteAsync { channel, _ ->
                        client.execute(
                            Runnable { client.gui.setScreen(ChatScreen(widget.screen.parent, channel)) }
                        )
                    }
                }.spacer()
            }
            builder.entry(Component.translatable("api.chat.report.message")) {
                val previous = client.gui.screen()
                client.gui.setScreen(
                    ConfirmScreen(
                        { b: Boolean ->
                            if (b) {
                                ChatHandler.getInstance().reportMessage(origin)
                            }
                            client.gui.setScreen(previous)
                        },
                        Component.translatable("api.channels.confirm_report"),
                        Component.translatable("api.channels.confirm_report.desc", origin.content!!)
                    )
                )
            }.spacer().entry(Component.translatable("action.copy")) {
                client.keyboardHandler.setClipboard(origin.content!!)
            }
            widget.screen.setContextMenu(builder.build())
            return true
        }
        return false
    }

    protected open fun renderExtras(graphics: GuiGraphicsExtractor, x: Int, y: Int, mouseX: Int, mouseY: Int) {
    }

    override fun extractContent(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        hovered: Boolean,
        tickDelta: Float
    ) {
        var hovered = hovered
        for (l in widget.children()) {
            if (l.origin == origin) {
                if (Objects.equals(widget.hoveredEntry(), l)) {
                    hovered = true
                    break
                }
            }
        }
        if (hovered && !widget.screen.hasContextMenu()) {
            val x = contentX
            val y = contentY
            val entryWidth = contentWidth
            val entryHeight = contentHeight
            val index = widget.children().indexOf(this)
            graphics.fill(x - 2 - 22, y - 2, x + entryWidth + 20, y + entryHeight - 1, 0x33FFFFFF.toInt())
            if (index < widget.children().size - 1 && widget.children()[index + 1].origin == origin) {
                graphics.fill(
                    x - 2 - 22, y + entryHeight - 1, x + entryWidth + 20, y + entryHeight + 2,
                    0x33FFFFFF.toInt()
                )
            }
            if ((index < widget.children().size - 1 && widget.children()[index + 1].origin != origin) ||
                index == widget.children().size - 1
            ) {
                graphics.fill(
                    x - 2 - 22, y + entryHeight - 1, x + entryWidth + 20, y + entryHeight,
                    0x33FFFFFF.toInt()
                )
            }
        }
        renderExtras(graphics, contentX, contentY, mouseX, mouseY)
        graphics.text(client.font, content, contentX, contentY, -1, false)
    }

    override fun getNarration(): Component {
        return Component.literal(origin.content!!)
    }
}

class NameChatLine(
    widget: ChatWidget,
    message: ChatMessage
) : ChatLine(
    widget,
    Component.literal(message.senderDisplayName!!).setStyle(Style.EMPTY.withBold(true))
        .getVisualOrderText(), message
) {

    private val formattedTime: String =
        DateTimeFormatter.ofPattern("d/M/yyyy H:mm").format(message.timestamp.atZone(ZoneId.systemDefault()))

    override fun renderExtras(graphics: GuiGraphicsExtractor, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        graphics.pose().pushMatrix()
        val texture: Identifier = Auth.getInstance().getSkinTexture(origin.sender.uuid)
        PlayerFaceExtractor.extractRenderState(graphics, texture, x - 22, y, 18, true, false, -1)
        graphics.text(
            client.font, formattedTime, client.font.width(content) + x + 5, y,
            ClientColors.GRAY.toInt(), false
        )
        graphics.pose().popMatrix()
    }
}
