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
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry
import io.github.axolotlclient.modules.hud.util.DrawPosition
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Block coordinates (Alpine "block_coordinates" module, parity spec M4/M6): the block the
 * player is looking at — its X/Y/Z and translated name. Reads the same vanilla hit
 * result the crosshair uses (`Minecraft.hitResult`). The block name acts as the
 * row label: with `dynamic_block_type_color` it is drawn in the block's own map
 * colour (the same palette the vanilla map item uses).
 */
class BlockCoordinatesHud : SimpleTextHudEntry(75, 13, true, false) {

    @JvmField
    val dynamicBlockTypeColor = BooleanOption("blockcoords.dynamic_block_type_color", false)

    @JvmField
    val labelColor = ColorOption("blockcoords.label_color", Color(255, 255, 255).immutable())

    @JvmField
    val valueColor = ColorOption("blockcoords.value_color", Color(160, 160, 160).immutable())

    private val mc: Minecraft = super.client as Minecraft

    override fun getValue(): String {
        val level = mc.level ?: return getPlaceholderValue()

        val hit = mc.hitResult
        if (hit == null || hit.type != HitResult.Type.BLOCK || hit !is BlockHitResult) {
            return "-"
        }

        val pos = hit.blockPos
        val name = level.getBlockState(pos).block.name.string
        return "$name ${pos.x} ${pos.y} ${pos.z}"
    }

    override fun renderComponent(render: AxoRenderContext, delta: Float) {
        drawParts(render, getValue())
    }

    override fun renderPlaceholderComponent(render: AxoRenderContext, delta: Float) {
        drawParts(render, getPlaceholderValue())
    }

    /** Two-colour draw: block name as label, coordinates as value. */
    private fun drawParts(render: AxoRenderContext, value: String) {
        val pos: DrawPosition = contentPos
        val x = pos.x() + 2
        val y = pos.y() + (getContentHeight() / 2f).roundToInt() - 4

        val parts = value.split(" ", limit = 2)
        val width: Int
        if (parts.size == 2) {
            val cursor = render.`br$drawString`(parts[0] + " ", x, y, nameColor(parts[0]).toInt(), shadow.get())
            val end = render.`br$drawString`(parts[1], cursor, y, valueColor.get().toInt(), shadow.get())
            width = end - x
        } else {
            val end = render.`br$drawString`(value, x, y, labelColor.get().toInt(), shadow.get())
            width = end - x
        }
        updateOwnBounds(width)
    }

    /** Row height: mirror of `SimpleTextHudEntry#updateBounds` (both private there):
     * widen to the drawn text, never below the constructor minimums, re-snap on change. */
    private fun updateOwnBounds(valueWidth: Int) {
        val elementWidth = valueWidth + 2
        val elementHeight = mc.`br$getFont`().`br$getFontHeight`() + 4
        var changed = false
        val newWidth = max(elementWidth, MIN_WIDTH)
        if (newWidth != getContentWidth()) {
            setContentWidth(newWidth)
            changed = true
        }
        val newHeight = max(elementHeight, MIN_HEIGHT)
        if (newHeight != getContentHeight()) {
            setContentHeight(newHeight)
            changed = true
        }
        if (changed) {
            onBoundsUpdate()
        }
    }

    private fun nameColor(name: String): Color {
        val level = mc.level
        val hit = mc.hitResult
        if (dynamicBlockTypeColor.get() && level != null && hit is BlockHitResult) {
            if (level.getBlockState(hit.blockPos).block.name.string == name) {
                val mapColor = level.getBlockState(hit.blockPos).getMapColor(level, hit.blockPos)
                if (mapColor != null && mapColor != MapColor.NONE) {
                    return Color(0xFF000000.toInt() or mapColor.col).immutable()
                }
            }
        }
        return labelColor.get()
    }

    override fun getPlaceholderValue(): String = "Stone 128 64 -256"

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(dynamicBlockTypeColor)
        options.add(labelColor)
        options.add(valueColor)
        return options
    }

    override fun getId(): Identifier = ID

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "blockcoordinateshud")

        /** Constructor minimums of `super(75, 13, ...)`; the parent's option fields are private. */
        private const val MIN_WIDTH = 75
        private const val MIN_HEIGHT = 13
    }
}
