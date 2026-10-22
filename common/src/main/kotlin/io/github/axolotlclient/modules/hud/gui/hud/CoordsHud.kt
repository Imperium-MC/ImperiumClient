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

import java.math.RoundingMode
import java.text.DecimalFormat

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.math.Vec3
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ClientColors

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class CoordsHud : TextHudEntry(79, 31, true) {
    private val secondColor = ColorOption("secondtextcolor", ClientColors.WHITE)
    private val firstColor = ColorOption("firsttextcolor", ClientColors.SELECTOR_BLUE)
    private val decimalPlaces: IntegerOption = IntegerOption(
        "decimalplaces", 0,
        { places: Int ->
            val formatBuilder = StringBuilder("0")
            if (places > 0) {
                formatBuilder.append(".")
                formatBuilder.append("0".repeat(places))
            }
            format = DecimalFormat(formatBuilder.toString())
            format.setRoundingMode(RoundingMode.FLOOR)
        }, 0, 15
    )
    private val minimal = BooleanOption("minimal", false)
    private val biome = BooleanOption("show_biome", false)

    // Alpine parity M2: toggle for the N/NE/E/... direction readout (shown in both modes).
    private val cardinal = BooleanOption("show_cardinal", true)
    private val delimiter = StringOption("coordshud.delimiter", " ")
    private val separator = StringOption("coordshud.separator", ", ")
    private val separatorColor = ColorOption("coordshud.separator.color", firstColor.getDefault())
    private val showNetherConversions = BooleanOption("coordshud.show_nether_conversions", false)

    // Alpine parity (coordinates module): the N/NE/... readout gets its own colour, the
    // X/Y/Z axis prefixes can be hidden, and the biome name can be tinted per biome.
    private val directionColor = ColorOption("coordshud.direction_color", firstColor.getDefault())
    private val displayAxis = BooleanOption("coordshud.display_axis", true)
    private val dynamicBiomeColor = BooleanOption("coordshud.dynamic_biome_color", false)

    /** Read by the pick-block mixin (latest) — announce the targeted block's coordinates.  */
    @JvmField
    val middleClickLookingAt = BooleanOption("coordshud.middle_click_looking_at", false)

    private var format = DecimalFormat("0")

    override fun postConfigLoad() {
        val formatBuilder = StringBuilder("0")
        if (decimalPlaces.get() > 0) {
            formatBuilder.append(".")
            formatBuilder.append("0".repeat(decimalPlaces.get()))
        }
        format = DecimalFormat(formatBuilder.toString())
        format.setRoundingMode(RoundingMode.FLOOR)
    }

    private fun doRender(
        context: AxoRenderContext, yaw: Double, playerPos: Vec3, biomeName: String,
        isOverworld: Boolean, isNether: Boolean
    ) {
        val pos = contentPos

        val dir = getDirection(yaw)
        val direction = getWordedDirection(dir)
        val del = delimiter.get()

        var width: Int
        var height: Int
        val xStart = pos.x() + 2

        val fx = format.format(playerPos.x())
        val fy = format.format(playerPos.y())
        val fz = format.format(playerPos.z())
        if (minimal.get()) {
            var currPos = xStart
            val separator = this.separator.get()
            if (displayAxis.get()) {
                currPos = context.`br$drawString`("XYZ" + del, currPos, pos.y() + 2, firstColor.get(), shadow.get())
            }
            currPos = context.`br$drawString`(fx, currPos, pos.y() + 2, secondColor.get(), shadow.get())
            currPos = context.`br$drawString`(separator, currPos, pos.y() + 2, separatorColor.get(), shadow.get())
            currPos = context.`br$drawString`(fy, currPos, pos.y() + 2, secondColor.get(), shadow.get())
            currPos = context.`br$drawString`(separator, currPos, pos.y() + 2, separatorColor.get(), shadow.get())
            currPos = context.`br$drawString`(fz, currPos, pos.y() + 2, secondColor.get(), shadow.get())
            if (cardinal.get()) {
                currPos = context.`br$drawString`(" " + direction, currPos, pos.y() + 2, directionColor.get(), shadow.get())
            }
            width = currPos - pos.x() + 2
            height = 11
            if (showNetherConversions.get() && (isNether || isOverworld)) {
                val name = AxoI18n.translate(if (isNether) "coordshud.dimension.overworld" else "coordshud.dimension.nether")
                val factor = if (isNether) 8f else 1 / 8f
                currPos = xStart
                currPos = context.`br$drawString`(name + del, currPos, pos.y() + 2, firstColor.get(), shadow.get())
                if (displayAxis.get()) {
                    currPos = context.`br$drawString`("XYZ" + del, currPos, pos.y() + 2, firstColor.get(), shadow.get())
                }
                currPos = context.`br$drawString`(
                    format.format(playerPos.x() * factor), currPos, pos.y() + 2, secondColor.get(), shadow.get()
                )
                currPos = context.`br$drawString`(
                    format.format(playerPos.y() * factor), currPos, pos.y() + 2, secondColor.get(), shadow.get()
                )
                currPos = context.`br$drawString`(
                    format.format(playerPos.z() * factor), currPos, pos.y() + 2, secondColor.get(), shadow.get()
                )
                width = maxOf(width, currPos - pos.x() + 2)
                height += 10
            }
        } else {
            var xEnd: Int
            var yEnd = pos.y() + 2
            var nextX = context.`br$drawString`(
                if (displayAxis.get()) "X" + del else "", xStart, yEnd, firstColor.get(), shadow.get()
            )
            xEnd = context.`br$drawString`(fx, nextX, yEnd, secondColor.get(), shadow.get())
            yEnd += 10

            nextX = context.`br$drawString`(if (displayAxis.get()) "Y" + del else "", xStart, yEnd, firstColor.get(), shadow.get())
            xEnd = maxOf(xEnd, context.`br$drawString`(fy, nextX, yEnd, secondColor.get(), shadow.get()))

            yEnd += 10
            nextX = context.`br$drawString`(if (displayAxis.get()) "Z" + del else "", xStart, yEnd, firstColor.get(), shadow.get())

            xEnd = maxOf(xEnd, context.`br$drawString`(fz, nextX, yEnd, secondColor.get(), shadow.get()))

            yEnd += 10

            xEnd = maxOf(pos.x() + 60, xEnd + 4)

            if (cardinal.get()) {
                context.`br$drawString`(direction, xEnd, pos.y() + 12, directionColor.get(), shadow.get())

                context.`br$drawString`(getXDir(dir), xEnd, pos.y() + 2, secondColor.get(), shadow.get())
                context.`br$drawString`(getZDir(dir), xEnd, pos.y() + 22, secondColor.get(), shadow.get())
                xEnd += 14
            }

            if (showNetherConversions.get() && (isNether || isOverworld)) {
                val name = AxoI18n.translate(if (isNether) "coordshud.dimension.overworld" else "coordshud.dimension.nether")
                val offset = context.`br$getFont`().`br$getWidth`(name + del)
                val factor = if (isNether) 8f else 1 / 8f
                nextX = context.`br$drawString`(name + del + "X" + del, xStart, yEnd, firstColor.get(), shadow.get())
                xEnd = maxOf(
                    xEnd,
                    context.`br$drawString`(format.format(playerPos.x() * factor), nextX, yEnd, secondColor.get(), shadow.get()) + 4
                )
                yEnd += 10

                nextX = context.`br$drawString`("Y" + del, xStart + offset, yEnd, firstColor.get(), shadow.get())
                xEnd = maxOf(
                    xEnd,
                    context.`br$drawString`(format.format(playerPos.y() * factor), nextX, yEnd, secondColor.get(), shadow.get()) + 4
                )
                yEnd += 10

                nextX = context.`br$drawString`("Z" + del, xStart + offset, yEnd, firstColor.get(), shadow.get())
                xEnd = maxOf(
                    xEnd,
                    context.`br$drawString`(format.format(playerPos.z() * factor), nextX, yEnd, secondColor.get(), shadow.get()) + 4
                )
                yEnd += 10
            }

            width = xEnd - pos.x()
            height = yEnd + 1 - pos.y()
        }

        if (biome.get()) {
            val bX = context.`br$drawString`(
                AxoI18n.translate("coordshud.biome") + del, xStart, height + pos.y(), firstColor.get(), shadow.get()
            )
            val biomeValueColor = if (dynamicBiomeColor.get()) biomeColor(biomeName) else secondColor.get()
            width = maxOf(
                width + pos.x() - 1,
                context.`br$drawString`(biomeName, bX, height + pos.y(), biomeValueColor, shadow.get())
            ) - pos.x() + 1
            height += 10
        }

        var changed = false

        if (getContentWidth() != width) {
            setContentWidth(width)
            changed = true
        }

        if (getContentHeight() != height) {
            setContentHeight(height)
            changed = true
        }

        if (changed) {
            onBoundsUpdate()
        }
    }

    override fun getId(): AxoIdentifier = ID

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        if (client.`br$getPlayer`() == null) {
            return
        }

        doRender(
            context, (client.`br$getPlayer`()!!.`br$getYaw`() + 180).toDouble(), client.`br$getPlayer`()!!.`br$getPos`(),
            client.`br$getWorld`()!!.`br$getBiomeName`(client.`br$getPlayer`()!!.`br$getPos`()),
            client.`br$getWorld`()!!.`br$isOverworld`(), client.`br$getWorld`()!!.`br$isNether`()
        )
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        doRender(context, 180.0, Vec3(109.2325, 180.8981, -5098.32698), "Plains", true, false)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.remove(textColor)
        options.add(firstColor)
        options.add(secondColor)
        options.add(directionColor)
        options.add(decimalPlaces)
        options.add(minimal)
        options.add(biome)
        options.add(dynamicBiomeColor)
        options.add(cardinal)
        options.add(displayAxis)
        options.add(middleClickLookingAt)
        options.add(showNetherConversions)
        options.add(delimiter)
        options.add(separator)
        options.add(separatorColor)
        return options
    }

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_MIDDLE

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "coordshud")

        /**
         * Get direction. 1 = North, 2 North East, 3 East, 4 South East...
         *
         * @param yaw the player's yaw
         * @return the direction, 0-360 degrees.
         */
        @JvmStatic
        fun getDirection(yawParam: Double): Int {
            var yaw = yawParam % 360

            if (yaw < 0) {
                yaw += 360
            }
            val directions = intArrayOf(0, 23, 68, 113, 158, 203, 248, 293, 338, 360)
            for (i in directions.indices) {
                val min = directions[i]
                val max: Int = if (i + 1 >= directions.size) {
                    directions[0]
                } else {
                    directions[i + 1]
                }
                if (yaw >= min && yaw < max) {
                    if (i >= 8) {
                        return 1
                    }
                    return i + 1
                }
            }
            return 0
        }

        @JvmStatic
        fun getXDir(dir: Int): String = when (dir) {
            3 -> "++"
            2, 4 -> "+"
            6, 8 -> "-"
            7 -> "--"
            else -> ""
        }

        @JvmStatic
        fun getZDir(dir: Int): String = when (dir) {
            5 -> "++"
            4, 6 -> "+"
            8, 2 -> "-"
            1 -> "--"
            else -> ""
        }

        @JvmStatic
        fun getWordedDirection(dir: Int): String = when (dir) {
            1 -> "N"
            2 -> "NE"
            3 -> "E"
            4 -> "SE"
            5 -> "S"
            6 -> "SW"
            7 -> "W"
            8 -> "NW"
            0 -> "?"
            else -> ""
        }

        /**
         * A stable colour per biome name (Alpine `dynamic_biome_color`): the name's hash
         * picks a hue on a fixed saturation/brightness, so each biome always reads in its own
         * colour without needing the biome's actual grass/foliage tint. Pure function so the
         * distribution can be exercised directly.
         */
        @JvmStatic
        fun biomeColor(biomeName: String): Color {
            val hue = ((biomeName.hashCode() and 0xFFFF) / 65535.0f + 0.6180339887f) % 1.0f
            return Color.fromHSB(hue, 0.55f, 0.9f)
        }
    }
}
