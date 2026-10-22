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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import net.fabricmc.loader.api.FabricLoader

class SubtitlesHudHud : BoxHudEntry(80, 13, true), DynamicallyPositionable {
    private val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(AnchorPoint.BOTTOM_RIGHT, this)
    @JvmField
    val vanillaEntryBackground = BooleanOption("subtitles.entry_background", true)

    /**
     * Subtitles appear and fade on their own, so this entry always re-renders live and is
     * never served from the `hud_frame_limit` recording.
     */
    override fun dynamicContent(): Boolean = true

    override fun getDefaultX(): Double = 1.0

    override fun getDefaultY(): Double = 0.9

    override fun render(ctx: AxoRenderContext, delta: Float) {
    }

    fun renderHud(ctx: AxoRenderContext, delta: Float) {
        super.render(ctx, delta)
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        // handled externally
    }

    override fun renderPlaceholder(ctx: AxoRenderContext, delta: Float) {
        if (BridgeVersion.V1_8.isCurrent() && !SOUNDFIX_INSTALLED) {
            hovered = false
            return
        }
        val h = 13
        val w = 80
        var updated = false
        if (h > getHeight()) {
            setHeight(h)
            updated = true
        }
        if (w > getWidth()) {
            setWidth(w)
            updated = true
        }
        if (updated) {
            onBoundsUpdate()
        }
        super.renderPlaceholder(ctx, delta)
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        ctx.`br$drawCenteredString`(
            getName(),
            getContentX() + getContentWidth() / 2,
            getContentY() + getContentHeight() / 2 - ctx.`br$getFont`().`br$getFontHeight`() / 2 + 1,
            -1
        )
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(vanillaEntryBackground)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    override fun getAnchor(): AnchorPoint = anchor.get()

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "subtitleshud")

        // only relevant to 1.8.9
        private val SOUNDFIX_INSTALLED = FabricLoader.getInstance().isModLoaded("soundfix")
    }
}
