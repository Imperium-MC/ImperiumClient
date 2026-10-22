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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.MathUtil

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
abstract class BoxHudEntry(width: Int, height: Int, private val backgroundAllowed: Boolean) :
    AbstractHudEntry(width, height) {

    @JvmField
    protected var background: BooleanOption? = BooleanOption("background", true)
    @JvmField
    protected var backgroundColor: ColorOption? = ColorOption("bgcolor", Color(0x64000000))
    @JvmField
    protected var backgroundBlur: BooleanOption? = BooleanOption("background_blur", "hud.background_blur.tooltip", false)
    @JvmField
    protected var backgroundBlurRadius: IntegerOption? =
        IntegerOption("background_blur_radius", "hud.background_blur_radius.tooltip", 8, 1, 24)

    @JvmField
    protected var outline: BooleanOption? = BooleanOption("outline", false)
    @JvmField
    protected var outlineColor: ColorOption? = ColorOption("outlinecolor", ClientColors.WHITE)
    @JvmField
    protected var outlineWidth: IntegerOption? = IntegerOption("hud.outline_width", 1, 1, 5)

    @JvmField
    protected var backgroundPadding: IntegerOption? = IntegerOption(
        "hud.background_padding", 0,
        { paddingValue: Int ->
            setWidth(getContentWidth() + paddingValue * 2)
            setHeight(getContentHeight() + paddingValue * 2)
            onBoundsUpdate()
        },
        0, 15
    )
    @JvmField
    protected var roundBackground: BooleanOption? = BooleanOption("hud.round_background", false)
    @JvmField
    protected var backgroundRounding: IntegerOption? = IntegerOption("hud.background_rounding", 10, 1, 20)

    var contentBounds: Rectangle = Rectangle(0, 0, 0, 0)
        private set
    var contentPos: DrawPosition = DrawPosition(0, 0)
        private set
    val initialContentWidth: Int = width
    val initialContentHeight: Int = height

    init {
        if (!backgroundAllowed) {
            background = null
            backgroundColor = null
            backgroundBlur = null
            backgroundBlurRadius = null
            outline = null
            outlineColor = null
            outlineWidth = null
            roundBackground = null
            backgroundRounding = null
        }
    }

    override fun postConfigLoad() {
        setContentWidth(initialContentWidth)
        setContentHeight(initialContentHeight)
    }

    /**
     * Whether this entry wants the world behind its background blurred this frame. Null-safe for
     * entries constructed without a background (`backgroundAllowed == false`, where the
     * options are nulled) and for the HUD edit screen's placeholders, which never blur (they draw
     * their own translucent placeholder, not this background).
     */
    fun wantsBlur(): Boolean =
        backgroundAllowed && background!!.get() && backgroundBlur!!.get()

    /**
     * This entry's preferred blur radius, clamped to 1-24 (default 8). The blur chain runs once
     * per frame at the strongest radius requested by any entry (and the master option), so a
     * radius below that maximum has no visible effect for this entry that frame.
     */
    fun blurRadius(): Int {
        if (backgroundBlurRadius == null) {
            return 8
        }
        return MathUtil.clamp(backgroundBlurRadius!!.get(), 1, 24)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        if (backgroundAllowed) {
            options.add(background!!)
            options.add(backgroundColor!!)
            options.add(outline!!)
            options.add(outlineColor!!)
            options.add(outlineWidth!!)
            options.add(backgroundPadding!!)
            options.add(roundBackground!!)
            options.add(backgroundRounding!!)
            options.add(backgroundBlur!!)
            options.add(backgroundBlurRadius!!)
        }
        return options
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        ctx.`br$pushMatrix`()
        scale(ctx)
        if (backgroundAllowed) {
            val bounds = getBounds()
            if (wantsBlur()) {
                // Blurred world behind the background; the (semi-transparent) background fill
                // tints on top of it below. Draws nothing when the blur pass did not run.
                ctx.`br$hudBlurBackground`(
                    bounds.x().toFloat(), bounds.y().toFloat(), bounds.width().toFloat(), bounds.height().toFloat()
                )
            }
            if (background!!.get() && backgroundColor!!.get().alpha > 0) {
                if (roundBackground!!.get()) {
                    ctx.`br$fillRectRound`(
                        bounds, backgroundColor!!.get(),
                        minOf(minOf(getHeight(), getWidth()) / 2f, backgroundRounding!!.get().toFloat())
                    )
                } else {
                    ctx.`br$fillRect`(bounds, backgroundColor!!.get())
                }
            }
            if (outline!!.get() && outlineColor!!.get().alpha > 0) {
                if (outlineWidth!!.get() > 1) {
                    val r = if (roundBackground!!.get()) {
                        minOf(minOf(getHeight(), getWidth()) / 2f, backgroundRounding!!.get().toFloat())
                    } else {
                        0f
                    }
                    ctx.`br$outlineRectRoundVarying`(
                        bounds.x(), bounds.y(), bounds.width(), bounds.height(), outlineColor!!.get().toInt(),
                        r, r, r, r, outlineWidth!!.get().toFloat()
                    )
                } else if (roundBackground!!.get()) {
                    ctx.`br$outlineRectRound`(
                        bounds, outlineColor!!.get(),
                        minOf(minOf(getHeight(), getWidth()) / 2f, backgroundRounding!!.get().toFloat())
                    )
                } else {
                    ctx.`br$outlineRect`(bounds, outlineColor!!.get())
                }
            }
        }
        renderComponent(ctx, delta)
        ctx.`br$popMatrix`()
    }

    override fun setBounds() {
        super.setBounds()
        val bounds = getBounds()
        val padding = backgroundPadding!!.get()
        if (padding == 0) {
            contentBounds = bounds.copy()
            contentPos = DrawPosition(bounds.x(), bounds.y())
        } else {
            contentBounds = Rectangle(
                bounds.x() + padding, bounds.y() + padding,
                bounds.width() - padding * 2, bounds.height() - padding * 2
            )
            contentPos = DrawPosition(bounds.x() + padding, bounds.y() + padding)
        }
    }

    fun getContentX(): Int = contentPos.x()

    fun getContentY(): Int = contentPos.y()

    fun setContentX(x: Int) {
        val padding = backgroundPadding!!.get()
        if (padding == 0) setX(x) else setX(x - padding)
    }

    fun setContentY(y: Int) {
        val padding = backgroundPadding!!.get()
        if (padding == 0) setY(y) else setY(y - padding)
    }

    fun getContentWidth(): Int = contentBounds.width()

    fun getContentHeight(): Int = contentBounds.height()

    fun setContentWidth(width: Int) {
        val padding = backgroundPadding!!.get()
        if (padding == 0) setWidth(width) else setWidth(width + padding * 2)
    }

    fun setContentHeight(height: Int) {
        val padding = backgroundPadding!!.get()
        if (padding == 0) setHeight(height) else setHeight(height + padding * 2)
    }

    fun getTrueContentX(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getTrueX()
        return getTrueX() + (padding * getScale()).toInt()
    }

    fun getTrueContentY(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getTrueY()
        return getTrueY() + (padding * getScale()).toInt()
    }

    fun getRawTrueContentX(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getRawTrueX()
        return getRawTrueX() + (padding * getScale()).toInt()
    }

    fun getRawTrueContentY(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getRawTrueY()
        return getRawTrueY() + (padding * getScale()).toInt()
    }

    fun getTrueContentWidth(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getTrueWidth()
        return getTrueWidth() + (padding * getScale() * 2).toInt()
    }

    fun getTrueContentHeight(): Int {
        val padding = backgroundPadding!!.get()
        if (padding == 0) return getTrueHeight()
        return getTrueHeight() + (padding * getScale() * 2).toInt()
    }

    abstract fun renderComponent(ctx: AxoRenderContext, delta: Float)

    override fun renderPlaceholder(ctx: AxoRenderContext, delta: Float) {
        ctx.`br$pushMatrix`()
        renderPlaceholderBackground(ctx)
        scale(ctx)
        renderPlaceholderComponent(ctx, delta)
        ctx.`br$popMatrix`()
        renderPlaceholderGrabCorners(ctx)
        hovered = false
    }

    abstract fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float)
}
