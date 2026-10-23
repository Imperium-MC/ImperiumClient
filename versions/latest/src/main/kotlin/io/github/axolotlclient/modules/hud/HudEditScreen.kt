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
import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.AxolotlClientConfig
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.imperium.menu.CarvedTheme
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.component.Positionable
import io.github.axolotlclient.modules.hud.snapping.SnappingHelper
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ExtraCursorTypes
import io.github.axolotlclient.util.MathUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
open class HudEditScreen protected constructor(protected val parent: Screen?) : Screen(Component.empty()) {
    // Editor state is protected so the v2 editor layer (ImperiumHudEditScreen) can build on the
    // same drag/snap machinery instead of forking it.
    protected var current: HudEntry? = null
    protected var offset: DrawPosition? = null
    protected var mouseDown = false
    protected var snap: SnappingHelper? = null
    protected var pendingMode = ModificationMode.NONE
    protected var mode = ModificationMode.NONE

    constructor() : this(null)

    /**
     * True while a drag or corner-resize modification is in progress; the v2 layer suppresses
     * keyboard nudging and extra hover behaviour while the mouse owns the element.
     */
    protected open fun isModificationActive(): Boolean = mode != ModificationMode.NONE

    private fun updateSnapState() {
        if (HudManager.getInstance()!!.isSnappingEnabled() && current != null) {
            val bounds = SnappingHelper.getNonDependentEntries(current!!, HudManager.getInstance()!!.getMoveableEntries())
                .stream()
                .map { obj: Positionable -> obj.getTrueBounds() }
                .collect(java.util.stream.Collectors.toCollection { ArrayList() })
            bounds.remove(current!!.getTrueBounds())
            snap = SnappingHelper(bounds, current!!.getTrueBounds())
        } else if (snap != null) {
            snap = null
        }
    }

    init {
        updateSnapState()
        mouseDown = false
    }

    // Carved chrome (menu.theme = CARVED): a chiseled stone strip framing the editor's tools.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(graphics, mouseX, mouseY, delta)
        if (AxolotlClient.config().menuTheme.get() != AxolotlClientConfigCommon.MenuTheme.CARVED) {
            return
        }
        graphics.fill(0, 0, width, CHROME_H, 0xFF17130F.toInt())
        CarvedTheme.drawStoneFill(graphics, 0, 0, width, CHROME_H)
        CarvedTheme.outline(graphics, 0, 0, width, CHROME_H, 0xFF0B0908.toInt())
        CarvedTheme.carvedRule(graphics, 0, CHROME_H - 2, width)
        CarvedTheme.engravedCaps(
            graphics, font, Component.translatable("imperium.hud_editor.title").string,
            8, 8, CarvedTheme.BRONZE_LIGHT
        )
        val hint = Component.translatable("imperium.hud_editor.hint").string
        CarvedTheme.engravedText(graphics, font, hint, width - 8 - font.width(hint), 9, CarvedTheme.MUTED)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val entry: HudEntry?
        if (current != null && mode != ModificationMode.NONE) {
            current!!.setHovered(true)
            graphics.requestCursor(mode.type)
            entry = current
        } else {
            entry = HudManager.getInstance()!!.getEntryXY(mouseX, mouseY).orElse(null)
            entry?.setHovered(true)
        }
        if (mouseDown && snap != null && HudManagerCommon.getInstance()!!.hudLinkCreationEnabled.get()) {
            snap!!.renderHighlights(graphics, current!!)
        }
        HudManager.getInstance()!!.renderPlaceholder(graphics, delta)
        if (renderWidgetFocusOutline() && focused is HudEntryWidget) {
            (focused as HudEntryWidget).extractRenderState(graphics, mouseX, mouseY, delta)
        }
        if (entry != null) {
            val bounds = entry.getTrueBounds()
            if (mode == ModificationMode.NONE && bounds.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                val supportsScaling = entry.supportsScaling()
                val tolerance = minOf(
                    HudManagerCommon.HUD_RESCALE_GRAB_TOLERANCE, minOf(bounds.width(), bounds.height()) / 2
                )
                val toleranceSquared = tolerance * tolerance
                var pending = ModificationMode.MOVE
                if (supportsScaling) {
                    if (MathUtil.distSq(mouseX.toFloat(), mouseY.toFloat(), bounds.x().toFloat(), bounds.y().toFloat()) < toleranceSquared) {
                        // top-left
                        pending = ModificationMode.TOP_LEFT
                    } else if (MathUtil.distSq(
                            mouseX.toFloat(), mouseY.toFloat(), bounds.xEnd().toFloat(), bounds.yEnd().toFloat()
                        ) < toleranceSquared
                    ) {
                        // bottom-right
                        pending = ModificationMode.BOTTOM_RIGHT
                    } else if (MathUtil.distSq(
                            mouseX.toFloat(), mouseY.toFloat(), bounds.x().toFloat(), bounds.yEnd().toFloat()
                        ) < toleranceSquared
                    ) {
                        // bottom-left
                        pending = ModificationMode.BOTTOM_LEFT
                    } else if (MathUtil.distSq(
                            mouseX.toFloat(), mouseY.toFloat(), bounds.xEnd().toFloat(), bounds.y().toFloat()
                        ) < toleranceSquared
                    ) {
                        // top-right
                        pending = ModificationMode.TOP_RIGHT
                    }
                }
                graphics.requestCursor(pending.type)
                this.pendingMode = pending
            }
        } else if (current == null) {
            pendingMode = ModificationMode.NONE
            mode = ModificationMode.NONE
        }
        if (mouseDown && snap != null) {
            renderSnapGuides(graphics)
        }
    }

    /**
     * Draws the snap guide lines while an element is being dragged. The base editor uses
     * SnappingHelper's blue lines; the v2 layer overrides this for its accent-hot guides.
     */
    protected open fun renderSnapGuides(graphics: GuiGraphicsExtractor) {
        snap!!.renderSnaps(graphics)
    }

    /**
     * Whether the focused entry widget's own 1px outline is drawn; the v2 layer replaces that
     * outline with its selection styling.
     */
    protected open fun renderWidgetFocusOutline(): Boolean = true

    override fun removed() {
        mode = ModificationMode.NONE
        super.removed()
    }

    override fun init() {
        mode = ModificationMode.NONE

        HudManager.getInstance()!!.getMoveableEntries().forEach { e -> addRenderableWidget(HudEntryWidget(e)) }

        addControlButtons()
    }

    /**
     * The editor's vanilla control buttons (snapping toggle, client options, back/close). The v2
     * layer replaces these with its themed toolbar, so the buttons live behind this hook.
     */
    protected open fun addControlButtons() {
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
            }.bounds(width / 2 - 50, height / 2 + 12, 100, 20).build()
        )

        this.addRenderableWidget(
            Button.builder(Component.translatable("hud.clientOptions")) {
                val screen = ConfigStyles.createScreen(this, io.github.axolotlclient.AxolotlClientCommon.getInstance()!!.configManager.getRoot())
                minecraft.gui.setScreen(screen)
            }.bounds(width / 2 - 75, height / 2 - 10, 150, 20).build()
        )

        if (parent != null) {
            addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK) { minecraft.gui.setScreen(parent) }
                    .bounds(width / 2 - 75, height - 50 + 22, 150, 20).build()
            )
        } else {
            addRenderableWidget(
                Button.builder(Component.translatable("close")) { minecraft.gui.setScreen(null) }
                    .bounds(width / 2 - 75, height - 50 + 22, 150, 20).build()
            )
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val value = super.mouseClicked(event, doubleClick)
        val mouseX = event.x()
        val mouseY = event.y()
        val entry = HudManager.getInstance()!!.getEntryXY(Math.round(mouseX).toInt(), Math.round(mouseY).toInt())
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            mouseDown = true
            if (entry.isPresent) {
                current = entry.get()
                offset = DrawPosition(
                    Math.round(mouseX - current!!.getTruePos().x()).toInt(),
                    Math.round(mouseY - current!!.getTruePos().y()).toInt()
                )
                if (pendingMode == ModificationMode.MOVE) {
                    updateSnapState()
                }
                mode = pendingMode
                return true
            } else {
                mode = ModificationMode.NONE
                current = null
            }
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            entry.ifPresent { abstractHudEntry: HudEntry ->
                val screen = ConfigStyles.createScreen(this, abstractHudEntry.getCategory())
                minecraft.gui.setScreen(screen)
            }
        }
        return value
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (current != null) {
            AxolotlClientConfig.getInstance().getConfigManager(current!!.getCategory()).save()
        }
        current = null
        snap = null
        mouseDown = false
        mode = ModificationMode.NONE
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        if (current != null) {
            current!!.clearBoundsDependencies()
            if (mode == ModificationMode.MOVE) {
                current!!.setPos(
                    (mouseX - offset!!.x() + current!!.offsetTrueWidth()).toInt(),
                    (mouseY - offset!!.y() + current!!.offsetTrueHeight()).toInt()
                )
                if (snap != null) {
                    var entries: MutableList<HudEntry>? = null
                    val snapX = snap!!.getCurrentXSnap()
                    val snapY = snap!!.getCurrentYSnap()
                    if (snapX.isPresent || snapY.isPresent) {
                        entries = HudManagerCommon.getInstance()!!.getMoveableEntries()
                        entries.remove(current)
                        entries.removeIf { e -> e.dependsOnX(current!!).isPresent || e.dependsOnY(current!!).isPresent }
                    }
                    snap!!.current = current!!.getTrueBounds()
                    if (snapX.isPresent) {
                        current!!.setX(snapX.get() + current!!.offsetTrueWidth())
                        if (HudManagerCommon.getInstance()!!.hudLinkCreationEnabled.get()) {
                            snap!!.getXTouching(entries!!, current!!).forEach { c ->
                                c.left!!.removeBoundsDependencyX(current!!)
                                current!!.addBoundsDependency(c.left!!, c.right!!)
                            }
                        }
                    }
                    if (snapY.isPresent) {
                        current!!.setY(snapY.get() + current!!.offsetTrueHeight())
                        if (HudManagerCommon.getInstance()!!.hudLinkCreationEnabled.get()) {
                            snap!!.getYTouching(entries!!, current!!).forEach { c ->
                                c.left!!.removeBoundsDependencyY(current!!)
                                current!!.addBoundsDependency(c.left!!, c.right!!)
                            }
                        }
                    }
                    HudManagerCommon.getInstance()!!.saveHudDependencyLinks()
                }
            } else {
                val bounds = current!!.getTrueBounds()
                val newWidth: Double
                val newHeight: Double
                when (mode) {
                    ModificationMode.TOP_LEFT -> {
                        // top-left corner
                        newWidth = mouseX - bounds.xEnd()
                        newHeight = mouseY - bounds.yEnd()
                    }

                    ModificationMode.BOTTOM_LEFT -> {
                        // bottom-left corner
                        newWidth = mouseX - bounds.xEnd()
                        newHeight = mouseY - bounds.y()
                    }

                    ModificationMode.TOP_RIGHT -> {
                        // top-right corner
                        newWidth = mouseX - bounds.x()
                        newHeight = mouseY - bounds.yEnd()
                    }

                    ModificationMode.BOTTOM_RIGHT -> {
                        // bottom-right corner
                        newWidth = mouseX - bounds.x()
                        newHeight = mouseY - bounds.y()
                    }

                    else -> {
                        newWidth = bounds.width().toDouble()
                        newHeight = bounds.height().toDouble()
                    }
                }
                val newScale = current!!.getScale() *
                    max(abs(newWidth).toFloat() / bounds.width(), abs(newHeight).toFloat() / bounds.height())
                current!!.setScale(max(0.1f, newScale))
                if (mode == ModificationMode.TOP_LEFT) {
                    // top-left corner
                    current!!.setPos(
                        bounds.xEnd() - current!!.getTrueWidth() + current!!.offsetTrueWidth(),
                        bounds.yEnd() - current!!.getTrueHeight() + current!!.offsetTrueHeight()
                    )
                } else if (mode == ModificationMode.BOTTOM_LEFT) {
                    // bottom-left corner
                    current!!.setX(bounds.xEnd() - current!!.getTrueWidth() + current!!.offsetTrueWidth())
                } else if (mode == ModificationMode.TOP_RIGHT) {
                    // top-right corner
                    current!!.setY(bounds.yEnd() - current!!.getTrueHeight() + current!!.offsetTrueHeight())
                }
            }
            if (current!!.tickable()) {
                current!!.tick()
            }
            return true
        }
        return false
    }

    protected enum class ModificationMode(val type: CursorType) {
        NONE(CursorType.DEFAULT),
        MOVE(CursorTypes.RESIZE_ALL),
        TOP_LEFT(ExtraCursorTypes.RESIZE_NWSE),
        TOP_RIGHT(ExtraCursorTypes.RESIZE_NESW),
        BOTTOM_LEFT(ExtraCursorTypes.RESIZE_NESW),
        BOTTOM_RIGHT(ExtraCursorTypes.RESIZE_NWSE)
    }

    companion object {
        // Carved chrome (menu.theme = CARVED): a chiseled stone strip framing the editor's tools.
        private const val CHROME_H = 24
    }
}
