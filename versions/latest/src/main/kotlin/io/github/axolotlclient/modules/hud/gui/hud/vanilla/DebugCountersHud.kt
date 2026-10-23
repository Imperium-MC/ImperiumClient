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
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.DrawUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.max

class DebugCountersHud : TextHudEntry(115, 32, true) {
    private val showCCount = BooleanOption("debugcounters.ccount", true)
    private val showECount = BooleanOption("debugcounters.ecount", false)
    private val showPCount = BooleanOption("debugcounters.pcount", false)
    private val mc: Minecraft = super.client as Minecraft

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        val graphics = context as GuiGraphicsExtractor

        if (mc.level == null) {
            renderPlaceholderComponent(graphics, delta)
        }
        val pos: DrawPosition = contentPos
        var lineY = pos.y() + 2
        val lineX = pos.x() + 1

        var xEnd = lineX + 50
        if (showCCount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, mc.levelExtractor.sectionStatistics() ?: "", lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }
        if (showECount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, mc.levelExtractor.entityStatistics() ?: "", lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }
        if (showPCount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, "P: " + mc.particleEngine.countParticles(), lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }

        var boundsChanged = false
        if (lineY != getContentHeight() + pos.y()) {
            boundsChanged = true
            setContentHeight(lineY - pos.y())
        }
        if (xEnd != pos.x() + getContentWidth()) {
            boundsChanged = true
            setContentWidth(xEnd - pos.x())
        }
        if (boundsChanged) {
            onBoundsUpdate()
        }
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        val graphics = context as GuiGraphicsExtractor

        val pos: DrawPosition = contentPos
        var lineY = pos.y() + 2
        val lineX = pos.x() + 1

        var xEnd = lineX + 50
        if (showCCount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, "C: 186/15000 (s) D: 10, pC: 000, pU: 00, aB: 20", lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }
        if (showECount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, "E: 695/3001, SD: 12", lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }
        if (showPCount.get()) {
            xEnd = max(
                xEnd,
                DrawUtil.drawString(graphics, "P: 200", lineX, lineY, textColor.get().toInt(), shadow.get())
            )
            lineY += 10
        }

        var boundsChanged = false
        if (lineY != getContentHeight() + pos.y()) {
            boundsChanged = true
            setContentHeight(lineY - pos.y())
        }
        if (xEnd != pos.x() + getContentWidth()) {
            boundsChanged = true
            setContentWidth(xEnd - pos.x())
        }
        if (boundsChanged) {
            onBoundsUpdate()
        }
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(showCCount)
        options.add(showECount)
        options.add(showPCount)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "debugcountershud")
    }
}
