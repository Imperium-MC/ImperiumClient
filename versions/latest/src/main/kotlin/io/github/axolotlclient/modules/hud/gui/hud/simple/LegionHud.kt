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

import java.util.Locale

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.imperium.LegionStateRegistry
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

/**
 * Legion status HUD (Alpine "factions status HUD" parity, reimplemented for ImperiumMC
 * Legions): one line with your legion name, online/total members and power, fed from
 * [LegionStateRegistry]. Until the future `imperiumclient:legion` channel
 * populates the registry the entry auto-sizes to nothing and draws no pixels
 * (CooldownsHud/BoostsHud shell pattern), so the module is invisible in the default setup.
 */
class LegionHud : TextHudEntry(70, 13, true) {

    /** Show "online/total" after the legion name. */
    @JvmField
    val showMembers = BooleanOption("legionhud.show_members", true)

    /** Show the war-score power after the name. */
    @JvmField
    val showPower = BooleanOption("legionhud.show_power", true)

    private val mc: Minecraft = super.client as Minecraft

    init {
        background = BooleanOption("background", false)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val legion = LegionStateRegistry.get() ?: run {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
            return
        }

        val label = buildLabel(legion)
        val x = contentPos.x().toInt()
        val y = contentPos.y().toInt()
        val width = mc.font.width(label)
        graphics.`br$drawString`(label, x, y, textColor.get().toInt(), shadow.get())
        if (width != getContentWidth() || mc.font.lineHeight != getContentHeight()) {
            setContentWidth(width)
            setContentHeight(mc.font.lineHeight)
            onBoundsUpdate()
        }
    }

    /** "[Spartans] 3/12 · Power 4.2k" — each part toggleable, separators dropped when empty. */
    private fun buildLabel(legion: LegionStateRegistry.LegionState): String {
        val label = StringBuilder("[").append(legion.name()).append("]")
        if (showMembers.get()) {
            label.append(" ").append(legion.onlineMembers()).append("/").append(legion.totalMembers())
        }
        if (showPower.get()) {
            label.append(if (label.indexOf(" ") > 0) " · " else " ")
                .append("Power ").append(formatPower(legion.power()))
        }
        return label.toString()
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (LegionStateRegistry.get() == null) {
            return // hidden while legion-less, even in a fixed-size layout
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
        options.add(showMembers)
        options.add(showPower)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "legionhud")
        private const val PLACEHOLDER = "Legion"

        /** Compact power formatting: 950 below 1k, 4.2k above, 1.2m above a million. */
        private fun formatPower(power: Long): String {
            if (power < 1000) {
                return power.toString()
            }
            if (power < 1_000_000) {
                return String.format(Locale.ROOT, "%.1fk", power / 1000.0).replace(',', '.')
            }
            return String.format(Locale.ROOT, "%.1fm", power / 1_000_000.0).replace(',', '.')
        }
    }
}
