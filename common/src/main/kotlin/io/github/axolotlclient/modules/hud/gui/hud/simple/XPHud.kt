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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.util.MathUtil

class XPHud : TextHudEntry(53, 13, true) {
    private val mode: EnumOption<Mode> = EnumOption("mode", Mode::class.java, Mode.LEVEL)
    private val minimal = BooleanOption("minimal", false)
    private val lines: MutableList<String> = ArrayList(3)

    override fun getId(): AxoIdentifier = ID

    private fun renderInternal(ctx: AxoRenderContext, level: Int, percent: Float) {
        val mode = this.mode.get()
        lines.clear()
        if (minimal.get()) {
            val b = StringBuilder()
            if (mode.level) {
                b.append(AxoI18n.translate("xphud.level", level))
            }
            if (mode.percent) {
                if (b.isEmpty()) {
                    b.append(AxoI18n.translate("xphud.percent", "%.2f".format(percent * 100f) + "%"))
                } else {
                    b.append(" / ").append("%.2f".format(percent * 100f)).append("%")
                }
            }
            if (mode.total) {
                if (!b.isEmpty()) {
                    b.append(" ")
                }
                b.append(AxoI18n.translate("xphud.total", getTotalXp(level, percent)))
            }
            lines.add(b.toString())
        } else {
            if (mode.level) {
                lines.add(AxoI18n.translate("xphud.level", level))
            }
            if (mode.percent) {
                lines.add(AxoI18n.translate("xphud.percent", "%.2f".format(percent * 100f) + "%"))
            }
            if (mode.total) {
                lines.add(AxoI18n.translate("xphud.total", getTotalXp(level, percent)))
            }
        }

        val h = (ctx.`br$getFont`().`br$getFontHeight`() + 1) * lines.size + 3
        val w = lines.stream().mapToInt { s -> ctx.`br$getFont`().`br$getWidth`(s) + 2 }.max().orElse(0) + 2

        var updated = false
        if (w != getContentWidth()) {
            setContentWidth(w)
            updated = true
        }
        if (h != getContentHeight()) {
            setContentHeight(h)
            updated = true
        }
        if (updated) {
            onBoundsUpdate()
        }

        val pos = contentPos
        var x = pos.x() + 2
        var y = pos.y() + 2
        for (l in lines) {
            ctx.`br$drawString`(l, x, y, textColor.get())
            y += ctx.`br$getFont`().`br$getFontHeight`() + 1
        }
    }

    private fun getTotalXp(currentLevel: Int, percentToNext: Float): Int {
        var totalXp = 0
        for (i in 0 until currentLevel) {
            totalXp += getXpNeededForNextLevel(i)
        }
        totalXp += MathUtil.floor(getXpNeededForNextLevel(currentLevel) * percentToNext)
        return totalXp
    }

    private fun getXpNeededForNextLevel(experienceLevel: Int): Int {
        return if (experienceLevel >= 30) {
            112 + (experienceLevel - 30) * 9
        } else {
            if (experienceLevel >= 15) 37 + (experienceLevel - 15) * 5 else 7 + experienceLevel * 2
        }
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        val player = AxoMinecraftClient.getInstance().`br$getPlayer`()!!
        renderInternal(ctx, player.`br$getExperienceLevel`(), player.`br$getExperienceProgress`())
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        renderInternal(ctx, 45, .362f)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(mode)
        options.add(minimal)
        return options
    }

    private enum class Mode(val level: Boolean, val percent: Boolean, val total: Boolean) {
        LEVEL(true, false, false),
        PERCENT(false, true, false),
        TOTAL(false, false, true),
        LEVEL_PERCENT(true, true, false),
        LEVEL_TOTAL(true, false, true),
        PERCENT_TOTAL(false, true, true),
        LEVEL_PERCENT_TOTAL(true, true, true);

        override fun toString(): String = "xphud.mode." + super.toString().lowercase(Locale.ROOT)
    }

    companion object {
        private val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "xphud")
    }
}
