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

package io.github.axolotlclient.modules.hud.gui.entry

import java.util.ArrayList
import java.util.HashMap
import java.util.Optional

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.DoubleOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.SnapAnchorType
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.MathUtil
import io.github.axolotlclient.util.options.ForceableBooleanOption

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
abstract class AbstractHudEntry(width: Int, height: Int) : HudEntry {
    @JvmField
    protected val enabled: ForceableBooleanOption = DefaultOptions.getEnabled(this)
    @JvmField
    protected val scale: DoubleOption = DefaultOptions.getScale(this)
    @JvmField
    protected val client: AxoMinecraftClient = AxoMinecraftClient.getInstance()
    @JvmField
    protected val hide: BooleanOption = BooleanOption("hud.hide", false)
    @JvmField
    protected val hideInDebug: BooleanOption = BooleanOption("hud.hide_in_debug", false)
    @JvmField
    protected val zLevel: IntegerOption = IntegerOption("hud.z_level", 0, -10, 10)
    private val x: DoubleOption = DefaultOptions.getX(getDefaultX())
    private val y: DoubleOption = DefaultOptions.getY(getDefaultY())
    private val xDependencies: MutableMap<HudEntry, SnapAnchorType> = HashMap()
    private val yDependencies: MutableMap<HudEntry, SnapAnchorType> = HashMap()

    @JvmField
    protected var width: Int = width
    @JvmField
    protected var height: Int = height
    @JvmField
    protected var hovered: Boolean = false
    @JvmField
    protected var supportsScaling: Boolean = true

    override fun getWidth(): Int = width

    override fun setWidth(width: Int) {
        this.width = width
    }

    override fun getHeight(): Int = height

    override fun setHeight(height: Int) {
        this.height = height
    }

    override fun isHovered(): Boolean = hovered

    override fun setHovered(hovered: Boolean) {
        this.hovered = hovered
    }

    private var trueBounds: Rectangle = Rectangle(0, 0, 1, 1)
    private var renderBounds: Rectangle = Rectangle(0, 0, 1, 1)
    private var truePosition: DrawPosition = DrawPosition(0, 0)
    private var renderPosition: DrawPosition = DrawPosition(0, 0)
    private var category: OptionCategory? = null

    fun renderPlaceholderBackground(context: AxoRenderContext) {
        val bounds = trueBounds
        if (hovered) {
            context.`br$fillRect`(bounds, ClientColors.ARGB.color(100, ClientColors.SELECTOR_BLUE.toInt()))
        } else {
            context.`br$fillRect`(bounds, ClientColors.ARGB.color(50, ClientColors.WHITE.toInt()))
        }
        context.`br$outlineRect`(bounds, Colors.BLACK)
    }

    fun renderPlaceholderGrabCorners(context: AxoRenderContext) {
        if (!supportsScaling()) return
        val c = HudManagerCommon.getInstance()!!.grabCornerColor.get()
        if (c.alpha == 0) return
        val bounds = trueBounds
        val grabTolerance = minOf(
            HudManagerCommon.HUD_RESCALE_GRAB_TOLERANCE,
            minOf(bounds.width(), bounds.height()) / 2
        )
        val color = c.toInt()
        val rounding = grabTolerance - .5f
        context.`br$fillRectRoundVarying`(
            bounds.x(), bounds.y(), grabTolerance, grabTolerance, color, 0f, 0f, rounding, 0f
        )
        context.`br$fillRectRoundVarying`(
            bounds.x(), bounds.yEnd() - grabTolerance, grabTolerance, grabTolerance, color, 0f, 0f, 0f, rounding
        )
        context.`br$fillRectRoundVarying`(
            bounds.xEnd() - grabTolerance, bounds.yEnd() - grabTolerance, grabTolerance, grabTolerance, color, rounding, 0f, 0f, 0f
        )
        context.`br$fillRectRoundVarying`(
            bounds.xEnd() - grabTolerance, bounds.y(), grabTolerance, grabTolerance, color, 0f, rounding, 0f, 0f
        )
    }

    fun scale(context: AxoRenderContext) {
        val scale = getScale()
        context.`br$scaleMatrix`(scale, scale)
    }

    override fun getRawTrueX(): Int = truePosition.x()

    override fun setX(x: Int) {
        this.x.set(intToFloat(x, AxoWindow.getWindow()!!.`br$getScaledWidth`().toInt(), 0).toDouble())
        onBoundsUpdate()
    }

    override fun setPos(x: Int, y: Int) {
        this.x.set(intToFloat(x, AxoWindow.getWindow()!!.`br$getScaledWidth`().toInt(), 0).toDouble())
        this.y.set(intToFloat(y, AxoWindow.getWindow()!!.`br$getScaledHeight`().toInt(), 0).toDouble())
        onBoundsUpdate()
    }

    override fun getScale(): Float = scale.get().toFloat()

    override fun setScale(scale: Float) {
        this.scale.set(scale.toDouble())
    }

    override fun getRawX(): Int = getPos().x

    override fun getRawTrueY(): Int = truePosition.y()

    override fun getRawY(): Int = getPos().y

    override fun setY(y: Int) {
        this.y.set(intToFloat(y, AxoWindow.getWindow()!!.`br$getScaledHeight`().toInt(), 0).toDouble())
        onBoundsUpdate()
    }

    /**
     * Gets the hud's bounds when the matrix has already been scaled.
     *
     * @return The bounds.
     */
    override fun getBounds(): Rectangle = renderBounds

    open fun setBounds() {
        val window = AxoWindow.getWindow()

        if (window == null) {
            truePosition = DrawPosition(0, 0)
            renderPosition = DrawPosition(0, 0)
            renderBounds = Rectangle(0, 0, 1, 1)
            trueBounds = Rectangle(0, 0, 1, 1)
            return
        }
        var scaledX = floatToInt(x.get().toFloat(), window.`br$getScaledWidth`().toInt(), 0) - offsetTrueWidth()
        var scaledY = floatToInt(
            y.get().toFloat(), window.`br$getScaledHeight`().toInt(), 0
        ) - offsetTrueHeight()
        if (scaledX < 0) {
            scaledX = 0
        }
        if (scaledY < 0) {
            scaledY = 0
        }
        val trueWidth = (getWidth() * getScale()).toInt()
        if (trueWidth < window.`br$getScaledWidth`() && scaledX + trueWidth > window.`br$getScaledWidth`()) {
            scaledX = (window.`br$getScaledWidth`() - trueWidth).toInt()
        }
        val trueHeight = (getHeight() * getScale()).toInt()
        if (trueHeight < window.`br$getScaledHeight`() &&
            scaledY + trueHeight > window.`br$getScaledHeight`()
        ) {
            scaledY = (window.`br$getScaledHeight`() - trueHeight).toInt()
        }
        truePosition.x = scaledX
        truePosition.y = scaledY
        renderPosition = truePosition.divide(getScale())
        renderBounds = Rectangle(renderPosition.x(), renderPosition.y(), getWidth(), getHeight())
        trueBounds = Rectangle(scaledX, scaledY, trueWidth, trueHeight)
    }

    override fun getPos(): DrawPosition = renderPosition

    override fun getTruePos(): DrawPosition = truePosition

    override fun getTrueBounds(): Rectangle = trueBounds

    override fun getTrueWidth(): Int = trueBounds.width()

    override fun getTrueHeight(): Int = trueBounds.height()

    override fun onBoundsUpdate() {
        setBounds()
        HudManagerCommon.getInstance()!!.updateBoundsDependencies(this)
    }

    fun getAllOptions(): OptionCategory {
        if (category == null) {
            val options = getSaveOptions()
            category = OptionCategory.create(getNameKey())
            options.forEach { category!!.add(it) }
        }
        return category!!
    }

    /**
     * Returns a list of options that should be saved. By default, this includes [getConfigurationOptions]
     *
     * @return a list of options.
     */
    override fun getSaveOptions(): MutableList<Option<*>> {
        val options = getConfigurationOptions()
        options.add(x)
        options.add(y)
        return options
    }

    /**
     * Returns a list of options that should be shown in configuration screens
     *
     * @return List of options
     */
    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = ArrayList<Option<*>>()
        options.add(enabled)
        options.add(scale)
        options.add(hideInDebug)
        options.add(zLevel)
        return options
    }

    override fun getCategory(): OptionCategory? = category

    override fun isEnabled(): Boolean = enabled.get()

    override fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    override fun getZLevel(): Int = zLevel.get()

    override fun isHidden(): Boolean =
        hide.get() || (hideInDebug.get() && HudManagerCommon.isDebugScreenShown())

    override fun supportsScaling(): Boolean = supportsScaling

    override fun dependsOnX(entry: HudEntry): Optional<SnapAnchorType> =
        Optional.ofNullable(xDependencies[entry])

    override fun dependsOnY(entry: HudEntry): Optional<SnapAnchorType> =
        Optional.ofNullable(yDependencies[entry])

    override fun addBoundsDependency(dependency: HudEntry, type: SnapAnchorType) {
        when (type) {
            SnapAnchorType.X_X, SnapAnchorType.X_XEND, SnapAnchorType.XEND_X, SnapAnchorType.XEND_XEND ->
                xDependencies[dependency] = type
            SnapAnchorType.Y_Y, SnapAnchorType.Y_YEND, SnapAnchorType.YEND_Y, SnapAnchorType.YEND_YEND ->
                yDependencies[dependency] = type
        }
    }

    override fun clearBoundsDependencies() {
        xDependencies.clear()
        yDependencies.clear()
    }

    override fun removeBoundsDependencyX(entry: HudEntry) {
        xDependencies.remove(entry)
    }

    override fun removeBoundsDependencyY(entry: HudEntry) {
        yDependencies.remove(entry)
    }

    override fun getDependenciesX(): Map<HudEntry, SnapAnchorType> = xDependencies

    override fun getDependenciesY(): Map<HudEntry, SnapAnchorType> = yDependencies

    companion object {
        @JvmStatic
        fun intToFloat(current: Int, max: Int, offset: Int): Float =
            MathUtil.clamp(current.toFloat() / (max - offset), 0f, 1f)

        @JvmStatic
        fun floatToInt(percent: Float, max: Int, offset: Int): Int =
            MathUtil.clamp(Math.round((max - offset) * percent), 0, max)
    }
}
