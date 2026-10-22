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

package io.github.axolotlclient.modules.hud.gui.hud

import java.util.Locale

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoSprites
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions

class IconHud : BoxHudEntry(16, 16, true), DynamicallyPositionable {
    private val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(this)
    private val mode: EnumOption<Mode> = EnumOption("iconhud.mode", Mode::class.java, Mode.BOTH)

    init {
        background = BooleanOption("background", false)
    }

    override fun getId(): AxoIdentifier = ID

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (client.`br$getScreen`() == null && mode.get().showsInGame()) {
            super.render(ctx, delta)
        }
    }

    fun renderInGui(context: AxoRenderContext, delta: Float) {
        if (mode.get().showsInGui()) {
            super.render(context, delta)
        }
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        ctx.`br$pushMatrix`()
        val scale = getScale()
        ctx.`br$scaleMatrix`(1 / scale, 1 / scale)
        ctx.`br$translateMatrix`(getRawTrueContentX().toFloat(), getRawTrueContentY().toFloat())
        ctx.`br$scaleMatrix`(scale, scale)
        ctx.`br$drawTexture`(AxoSprites.BADGE, 0, 0, 16, 16)
        ctx.`br$popMatrix`()
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        renderComponent(ctx, delta)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(mode)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    private enum class Mode {
        IN_GAME {
            override fun showsInGui(): Boolean = false
        },
        GUI {
            override fun showsInGame(): Boolean = false
        },
        BOTH;

        open fun showsInGame(): Boolean = true

        open fun showsInGui(): Boolean = true

        override fun toString(): String = "iconhud.mode." + super.toString().lowercase(Locale.ROOT)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "iconhud")
    }
}
