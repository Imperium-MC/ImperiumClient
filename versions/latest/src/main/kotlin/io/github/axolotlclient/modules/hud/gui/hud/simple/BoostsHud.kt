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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.imperium.BoostRegistry
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import kotlin.math.max

/**
 * Boosts HUD (Alpine "enchantedmc / boosts" parity) — server-specific. Lists the active
 * entries of [BoostRegistry] (name + remaining time), one line each; with no boosts
 * running the entry auto-sizes to nothing and draws no pixels (CooldownsHud pattern), so
 * the shell is invisible until the future ImperiumMC boost channel populates it.
 */
class BoostsHud : TextHudEntry(80, 11, true) {

    /** Show the remaining time after each boost name. */
    @JvmField
    val showRemaining = BooleanOption("show_remaining", true)

    // Alpine profile settings (M6): the remaining-time text gets its own colour when
    // override_color is on (off = the entry's regular text colour).
    @JvmField
    val overrideColor = BooleanOption("boosts.override_color", false)

    @JvmField
    val durationColor = ColorOption("boosts.duration_color", Color(232, 197, 99).immutable())

    private val mc: Minecraft = super.client as Minecraft

    init {
        background = BooleanOption("background", false)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val boosts = BoostRegistry.active()
        if (boosts.isEmpty()) {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
            return
        }

        var x = contentPos.x().toInt()
        var y = contentPos.y().toInt()
        val lineHeight = mc.font.lineHeight + 1
        var width = 0
        for (boost in boosts) {
            val cursor = graphics.`br$drawString`(boost.name(), x, y, textColor.get().toInt(), shadow.get())
            var end = cursor
            if (showRemaining.get()) {
                val remaining = boost.endsAtEpochMs() - System.currentTimeMillis()
                val secondsColor = if (overrideColor.get()) durationColor.get().toInt() else textColor.get().toInt()
                end = graphics.`br$drawString`(" " + formatRemaining(remaining), cursor, y, secondsColor, shadow.get())
            }
            width = max(width, end - x)
            y += lineHeight
        }
        if (width != getContentWidth() || boosts.size * lineHeight != getContentHeight()) {
            setContentWidth(width)
            setContentHeight(boosts.size * lineHeight)
            onBoundsUpdate()
        }
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (BoostRegistry.active().isEmpty()) {
            return // hidden when idle, even in a fixed-size layout
        }
        super.render(context, delta)
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val x = contentPos.x().toInt()
        val y = contentPos.y().toInt()
        graphics.`br$drawString`(PLACEHOLDER, x, y, -1, false)
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(showRemaining)
        options.add(overrideColor)
        options.add(durationColor)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "boostshud")
        private const val PLACEHOLDER = "Boosts"

        private fun formatRemaining(ms: Long): String {
            val seconds = max(0, (ms + 999) / 1000)
            if (seconds < 60) {
                return "${seconds}s"
            }
            val minutes = seconds / 60
            if (minutes < 60) {
                return "${minutes}m${seconds % 60}s"
            }
            return "${minutes / 60}h${minutes % 60}m"
        }
    }
}
