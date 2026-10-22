/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfigCommon.FarmingOverlayStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.roundToInt

/**
 * farming (Alpine parity): when the crosshair targets a crop, its growth stage is drawn
 * near the crosshair — either as text ("3/8", age over total stages) or a small progress
 * bar. `farming.hoe_only` restricts the overlay to while holding a hoe.
 *
 * Covers [CropBlock] crops (wheat, carrots, potatoes, beetroot); other growers
 * like cocoa/sweet berry/nether wart have their own age properties and are left to a
 * later pass.
 */
object FarmingOverlay {

    private const val BAR_WIDTH = 40
    private const val BAR_HEIGHT = 5

    /**
     * The stage text for a crop state: "`age`/`stages`", e.g. "3/8" for wheat at
     * age 3 (stages 0-7). Pure function so the exact rendering input can be asserted.
     * Returns null when the state is not a crop (as the Java version did).
     */
    @JvmStatic
    fun stageText(state: BlockState): String? {
        val crop = state.block as? CropBlock ?: return null
        val age = crop.getAge(state)
        val stages = crop.maxAge + 1
        return "$age/$stages"
    }

    /**
     * Growth progress 0..1 of a crop state, or a negative value if the state is not a crop.
     */
    @JvmStatic
    fun progress(state: BlockState): Float {
        val crop = state.block as? CropBlock ?: return -1.0f
        return crop.getAge(state).toFloat() / crop.maxAge
    }

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        if (!AxolotlClient.config().farmingOverlay.get() || minecraft.level == null || minecraft.player == null) {
            return
        }
        val level = minecraft.level ?: return
        val hit = minecraft.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) {
            return
        }
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        val crop = state.block as? CropBlock ?: return
        val player = minecraft.player!!
        if (AxolotlClient.config().farmingHoeOnly.get() && !isHoldingHoe(player.mainHandItem)) {
            return
        }

        val progress = crop.getAge(state).toFloat() / crop.maxAge
        val color = lerpColor(0xFFFF5555.toInt(), 0xFF55FF55.toInt(), progress)
        val font = minecraft.font

        if (AxolotlClient.config().farmingStyle.get() == FarmingOverlayStyle.BAR) {
            val x = graphics.guiWidth() / 2 - BAR_WIDTH / 2
            val y = graphics.guiHeight() / 2 + 12
            graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x90000000.toInt())
            graphics.fill(x + 1, y + 1, x + 1 + ((BAR_WIDTH - 2) * progress).roundToInt(), y + BAR_HEIGHT - 1, color)
        } else {
            val text = stageText(state)!!
            val x = graphics.guiWidth() / 2 + 10
            val y = graphics.guiHeight() / 2 + 8
            graphics.text(font, text, x, y, color, true)
        }
    }

    /**
     * Hoes are fully data-driven in 26.3 (there is no HoeItem class), so the check matches
     * the vanilla item ids (`*_hoe`).
     */
    @JvmStatic
    fun isHoldingHoe(stack: ItemStack): Boolean {
        return !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path.endsWith("_hoe")
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        return ARGB.color(
            ARGB.alpha(from),
            Mth.lerpInt(t, ARGB.red(from), ARGB.red(to)),
            Mth.lerpInt(t, ARGB.green(from), ARGB.green(to)),
            Mth.lerpInt(t, ARGB.blue(from), ARGB.blue(to))
        )
    }
}
