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

import java.util.Locale

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.modules.hud.gui.layout.Justification
import io.github.axolotlclient.modules.hud.util.DrawPosition

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
abstract class SimpleTextHudEntry protected constructor(
    width: Int, height: Int, backgroundAllowed: Boolean, private val hasOrder: Boolean
) : TextHudEntry(width, height, backgroundAllowed) {

    @JvmField
    protected val justification: EnumOption<Justification> =
        EnumOption("justification", Justification::class.java, Justification.CENTER)
    @JvmField
    protected val showBrackets: BooleanOption = BooleanOption("show_brackets", false)
    private val order: EnumOption<ValueDescriptionRelation> =
        EnumOption("simple_text_hud.order", ValueDescriptionRelation::class.java, ValueDescriptionRelation.VALUE_DESCRIPTION)
    @JvmField
    protected val separator: StringOption = StringOption("simple_text_hud.separator", " ")
    private val minWidth: IntegerOption = IntegerOption("minwidth", width, 1, 300)
    private val minHeight: IntegerOption = IntegerOption("hud.height", height, 1, 150)

    constructor() : this(53)

    protected constructor(hasOrder: Boolean) : this(53, 13, true, hasOrder)

    protected constructor(width: Int) : this(width, 13, true, false)

    override fun renderComponent(render: AxoRenderContext, delta: Float) {
        val pos = contentPos
        val value = applyOptions(getValue(), getLabel())

        val valueWidth = render.`br$getFont`().`br$getWidth`(value)
        updateBounds(valueWidth)
        render.`br$drawString`(
            value,
            pos.x() + justification.get().getXOffset(valueWidth, getContentWidth() - 4) + 2,
            pos.y() + (Math.round(getContentHeight() / 2f)) - 4, getTextColor().toInt(), shadow.get()
        )
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        val pos = contentPos
        val value = applyOptions(getPlaceholderValue(), getPlaceholderLabel())
        val valueWidth = ctx.`br$getFont`().`br$getWidth`(value)
        updateBounds(valueWidth)
        ctx.`br$drawString`(
            value, pos.x() + justification.get().getXOffset(valueWidth, getContentWidth() - 4) + 2,
            pos.y() + (Math.round(getContentHeight() / 2f)) - 4, getTextColor().toInt(), shadow.get()
        )
    }

    private fun updateBounds(valueWidth: Int) {
        val elementWidth = valueWidth + 4
        val elementHeight = client.`br$getFont`().`br$getFontHeight`() + 4
        var boundsChanged = false
        val minW = minWidth.get()
        if (elementWidth < minW) {
            if (getContentWidth() != minW) {
                setContentWidth(minW)
                boundsChanged = true
            }
        } else if (elementWidth != getContentWidth()) {
            setContentWidth(elementWidth)
            boundsChanged = true
        }
        val minH = minHeight.get()
        if (elementHeight < minH) {
            if (getContentHeight() != minH) {
                setContentHeight(minH)
                boundsChanged = true
            }
        } else if (elementHeight != getContentHeight()) {
            setContentHeight(elementHeight)
            boundsChanged = true
        }

        if (boundsChanged) {
            onBoundsUpdate()
        }
    }

    protected open fun applyOptions(value: String, desc: String): String {
        val s = if (hasOrder) order.get().func.apply(value, desc, separator.get()) else value
        return if (showBrackets.get()) AxoI18n.translate("bracket_format", s) else s
    }

    abstract fun getPlaceholderValue(): String

    open fun getPlaceholderLabel(): String = getLabel()

    abstract fun getValue(): String

    open fun getLabel(): String = ""

    open fun getTextColor(): Color = textColor.get()

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(justification)
        options.add(minWidth)
        options.add(minHeight)
        options.add(showBrackets)
        if (hasOrder) {
            options.add(order)
            options.add(separator)
        }
        return options
    }

    private enum class ValueDescriptionRelation(val func: Relation) {
        VALUE_DESCRIPTION(Relation { s, s2, sep -> s + sep + s2 }),
        DESCRIPTION_VALUE(Relation { s, s2, sep -> s2 + sep + s }),
        VALUE_ONLY(Relation { s, s2, sep -> s });

        override fun toString(): String = "simple_text_hud.order." + super.toString().lowercase(Locale.ROOT)

        fun interface Relation {
            fun apply(value: String, description: String, separator: String): String
        }
    }
}
