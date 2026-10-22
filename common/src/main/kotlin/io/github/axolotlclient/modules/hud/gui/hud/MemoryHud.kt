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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.Justification
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ClientColors

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class MemoryHud : TextHudEntry(150, 27, true) {
    protected val justification: EnumOption<Justification> =
        EnumOption("justification", Justification::class.java, Justification.CENTER)

    private data class MemoryInfo(val free: Long, val max: Long, val total: Long) {
        fun used(): Long = total - free

        fun usage(): Float = used() / max.toFloat()

        fun getMemoryLine(): String = toMiB(used()) + "/" + toMiB(max) + " (" + (usage() * 100).toInt() + "%)"

        fun getAllocationLine(): String = AxoI18n.translate("allocated") + ": " + toMiB(total)

        companion object {
            fun current(): MemoryInfo = MemoryInfo(
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().totalMemory()
            )

            val PLACEHOLDER = MemoryInfo(
                2 * 1024 * 1024,
                3 * 1024 * 1024,
                4 * 1024 * 1024
            )

            fun toMiB(bytes: Long): String = (bytes / 1024L / 1024L).toString() + "MiB"
        }
    }

    private val graphUsedColor = ColorOption("graphUsedColor", ClientColors.SELECTOR_RED.withAlpha(255))
    private val graphFreeColor = ColorOption("graphFreeColor", ClientColors.SELECTOR_GREEN.withAlpha(255))

    private val showGraph = BooleanOption("showGraph", true)
    private val showText = BooleanOption("showText", false)
    private val showAllocated = BooleanOption("showAllocated", false)

    private fun doRender(context: AxoRenderContext, info: MemoryInfo) {
        val pos = contentPos

        if (showGraph.get()) {
            val usagePx = ((getContentWidth() - 10) * info.usage()).toInt()
            context.`br$fillRect`(pos.x() + 5, pos.y() + 5, usagePx, getContentHeight() - 10, graphUsedColor.get().toInt())
            context.`br$fillRect`(
                pos.x() + 5 + usagePx, pos.y() + 5, getContentWidth() - 10 - usagePx, getContentHeight() - 10,
                graphFreeColor.get().toInt()
            )
            context.`br$outlineRect`(
                pos.x() + 5, pos.y() + 5, getContentWidth() - 10, getContentHeight() - 10, ClientColors.BLACK
            )
        }

        if (showText.get()) {
            val mem = info.getMemoryLine()

            context.`br$drawString`(
                mem,
                pos.x() + justification.get().getXOffset(
                    context.`br$getFont`().`br$getWidth`(mem), getContentWidth() - 4
                ) + 2,
                pos.y() + (Math.round(getContentHeight() / 2f) - 4) - (if (showAllocated.get()) 4 else 0),
                textColor.get().toInt(),
                shadow.get()
            )

            if (showAllocated.get()) {
                val alloc = info.getAllocationLine()
                context.`br$drawString`(
                    alloc,
                    pos.x() + justification.get().getXOffset(
                        context.`br$getFont`().`br$getWidth`(alloc),
                        getContentWidth() - 4
                    ) + 2,
                    pos.y() + (Math.round(getContentHeight() / 2f) - 4) + 4,
                    textColor.get().toInt(),
                    shadow.get()
                )
            }
        }
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        doRender(context, MemoryInfo.current())
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        doRender(context, MemoryInfo.PLACEHOLDER)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(justification)
        options.add(showGraph)
        options.add(graphUsedColor)
        options.add(graphFreeColor)
        options.add(showText)
        options.add(showAllocated)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "memoryhud")
    }
}
