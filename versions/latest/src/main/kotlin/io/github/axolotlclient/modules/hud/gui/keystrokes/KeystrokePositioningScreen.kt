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

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud
import io.github.axolotlclient.modules.hud.snapping.SnappingHelper
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.DrawUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class KeystrokePositioningScreen(private val parent: Screen?, private val hud: KeystrokeHud, private val editing: KeystrokeHud.Keystroke?) :
    Screen(Component.translatable("keystrokes.stroke.move")) {

    private var focused: KeystrokeHud.Keystroke? = null
    private var offset: DrawPosition? = null
    private var mouseDown = false
    private var snap: SnappingHelper? = null

    constructor(parent: Screen?, hud: KeystrokeHud) : this(parent, hud, null)

    init {
        if (hud.keystrokes == null) {
            hud.setKeystrokes()
        }
        mouseDown = false
    }

    override fun extractBackground(guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphicsExtractor.pose().pushMatrix()
        super.extractBackground(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        HudManager.getInstance()!!.renderPlaceholder(guiGraphicsExtractor, partialTick)
        guiGraphicsExtractor.pose().popMatrix()
        extractTransparentBackground(guiGraphicsExtractor)
    }

    override fun init() {
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { onClose() }.pos(width / 2 - 75, height - 50 + 22).width(150).build()
        )
        this.addRenderableWidget(
            Button.builder(
                Component.translatable("hud.snapping").append(": ")
                    .append(Component.translatable(if (HudManager.getInstance()!!.isSnappingEnabled()) "options.on" else "options.off"))
            ) { buttonWidget ->
                HudManager.getInstance()!!.toggleSnapping()
                buttonWidget.setMessage(
                    Component.translatable("hud.snapping").append(": ")
                        .append(
                            Component.translatable(
                                if (HudManager.getInstance()!!.isSnappingEnabled()) "options.on" else "options.off"
                            )
                        )
                )
            }.bounds(width / 2 - 50, height - 50, 100, 20).build()
        )
    }

    override fun extractRenderState(guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
        if (editing != null) {
            drawStroke(guiGraphicsExtractor, mouseX, mouseY, editing)
        } else {
            hud.keystrokes!!.forEach { s -> drawStroke(guiGraphicsExtractor, mouseX, mouseY, s) }
        }
        if (mouseDown && snap != null) {
            snap!!.renderSnaps(guiGraphicsExtractor)
        }
    }

    private fun drawStroke(guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, s: KeystrokeHud.Keystroke) {
        val rect = getScaledRenderPos(s)
        if (rect.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            DrawUtil.fillRect(guiGraphicsExtractor, rect, ClientColors.SELECTOR_BLUE.withAlpha(100))
            guiGraphicsExtractor.requestCursor(CursorTypes.RESIZE_ALL)
        } else {
            DrawUtil.fillRect(guiGraphicsExtractor, rect, ClientColors.WHITE.withAlpha(50))
        }
        guiGraphicsExtractor.pose().pushMatrix()
        guiGraphicsExtractor.pose().scale(hud.getScale(), hud.getScale())
        s.render(guiGraphicsExtractor)
        guiGraphicsExtractor.pose().popMatrix()
        DrawUtil.outlineRect(guiGraphicsExtractor, rect, Colors.BLACK)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val value = super.mouseClicked(event, doubleClick)
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val mouseX = event.x()
            val mouseY = event.y()
            var entry: KeystrokeHud.Keystroke? = null
            var pos: Rectangle? = null
            if (editing == null) {
                for (k in hud.keystrokes!!) {
                    pos = getScaledRenderPos(k)
                    if (pos.isMouseOver(mouseX, mouseY)) {
                        entry = k
                        break
                    }
                }
            } else {
                pos = getScaledRenderPos(editing)
                if (pos.isMouseOver(mouseX, mouseY)) {
                    entry = editing
                }
            }
            if (entry != null) {
                focused = entry
                mouseDown = true
                val rect = pos!!
                offset = DrawPosition((mouseX - rect.x()).roundToInt(), (mouseY - rect.y()).roundToInt())
                updateSnapState()
                return true
            } else {
                focused = null
            }
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && editing == null) {
            val mouseX = event.x()
            val mouseY = event.y()
            var entry: KeystrokeHud.Keystroke? = null
            for (k in hud.keystrokes!!) {
                val pos = getScaledRenderPos(k)
                if (pos.isMouseOver(mouseX, mouseY)) {
                    entry = k
                    break
                }
            }
            entry?.let { stroke -> minecraft!!.gui.setScreen(ConfigureKeyBindScreen(this, hud, stroke, false)) }
        }
        return value
    }

    override fun onClose() {
        minecraft!!.gui.setScreen(parent)
        hud.saveKeystrokes()
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (focused != null) {
            hud.saveKeystrokes()
        }
        snap = null
        mouseDown = false
        focused = null
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (focused != null && mouseDown) {
            focused!!.setX(((event.x() - offset!!.x()) / hud.getScale()).roundToInt())
            focused!!.setY(((event.y() - offset!!.y()) / hud.getScale()).roundToInt())
            if (snap != null) {
                val rect = getScaledRenderPos(focused!!)
                snap!!.current = rect
                val snapX = snap!!.getCurrentXSnap()
                if (snapX.isPresent) {
                    focused!!.setX((snapX.get() / hud.getScale()).roundToInt())
                }
                val snapY = snap!!.getCurrentYSnap()
                if (snapY.isPresent) {
                    focused!!.setY((snapY.get() / hud.getScale()).roundToInt())
                }
            }
            return true
        }
        return false
    }

    private fun getScaledRenderPos(stroke: KeystrokeHud.Keystroke): Rectangle = stroke.getRenderPosition().scale(hud.getScale().toDouble())

    private fun getAllBounds(): List<Rectangle> =
        HudManager.getInstance()!!.getAllBounds() + hud.keystrokes!!.map { stroke: KeystrokeHud.Keystroke -> getScaledRenderPos(stroke) }

    private fun updateSnapState() {
        if (HudManager.getInstance()!!.isSnappingEnabled() && focused != null) {
            snap = SnappingHelper(ArrayList(getAllBounds()), getScaledRenderPos(focused!!))
        } else if (snap != null) {
            snap = null
        }
    }
}
