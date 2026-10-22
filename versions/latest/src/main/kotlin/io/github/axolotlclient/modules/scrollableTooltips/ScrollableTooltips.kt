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

package io.github.axolotlclient.modules.scrollableTooltips

import java.util.Locale

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.mixin.AbstractContainerScreenAccessor
import io.github.axolotlclient.modules.AbstractModule
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Items
import kotlin.math.abs
import kotlin.math.max

class ScrollableTooltips : AbstractModule() {
    @JvmField
    val enabled = BooleanOption("enabled", false)
    @JvmField
    val enableShiftHorizontalScroll = BooleanOption("shiftHorizontalScroll", true)
    protected val scrollAmount = IntegerOption("scrollAmount", 5, 1, 20)
    protected val inverse = BooleanOption("inverse", false)

    // Alpine parity: smooth_scroll glides the offset toward the target instead of snapping
    // by scrollAmount per notch; activation_key requires a modifier to be held before any
    // scrolling applies (NONE keeps it always active).
    @JvmField
    val smoothScroll = BooleanOption("smooth_scroll", false)
    @JvmField
    val activationKey = EnumOption("activation_key", ActivationKey::class.java, ActivationKey.NONE)
    private val category = OptionCategory.create("scrollableTooltips")

    @JvmField
    var tooltipOffsetX = 0
    @JvmField
    var tooltipOffsetY = 0

    /** Smooth-scroll targets; the drawn offsets above ease toward these every tick. */
    private var targetOffsetX = 0
    private var targetOffsetY = 0

    override fun init() {
        category.add(enabled)
        category.add(enableShiftHorizontalScroll)
        category.add(scrollAmount)
        category.add(inverse)
        category.add(smoothScroll)
        category.add(activationKey)

        AxolotlClient.config().rendering.add(category)

        Events.TICK.register(Runnable {
            if (!smoothScroll.get()) {
                tooltipOffsetX = targetOffsetX
                tooltipOffsetY = targetOffsetY
                return@Runnable
            }
            // Ease a third of the remaining distance per tick, always covering at least
            // one pixel so small nudges cannot stall short of the target.
            tooltipOffsetX += Math.copySign(
                max(1, abs(targetOffsetX - tooltipOffsetX) / 3).toDouble(),
                (targetOffsetX - tooltipOffsetX).toDouble()
            ).toInt()
            tooltipOffsetY += Math.copySign(
                max(1, abs(targetOffsetY - tooltipOffsetY) / 3).toDouble(),
                (targetOffsetY - tooltipOffsetY).toDouble()
            ).toInt()
            if (abs(targetOffsetX - tooltipOffsetX) < 2) {
                tooltipOffsetX = targetOffsetX
            }
            if (abs(targetOffsetY - tooltipOffsetY) < 2) {
                tooltipOffsetY = targetOffsetY
            }
        })
    }

    fun onScroll(reverse: Boolean): Boolean {
        val screen = client.gui.screen()
        if (screen is AbstractContainerScreen<*>) {
            if (!activationKey.get().isDown(Minecraft.getInstance())) {
                return false
            }
            if (Minecraft.getInstance().gui.screen() is CreativeModeInventoryScreen &&
                (Minecraft.getInstance().gui.screen() as CreativeModeInventoryScreen).getSelectedTab() !==
                BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.INVENTORY)
            ) {
                return false
            }
            val hovered = (screen as AbstractContainerScreenAccessor).getHoveredSlot()
            if (hovered == null || hovered.hasItem() && hovered.item.`is`(Items.BUNDLE) && !Minecraft.getInstance().hasControlDown()) {
                return false
            }
            if (Minecraft.getInstance().hasShiftDown()) {
                if (applyInverse(reverse)) {
                    targetOffsetX -= scrollAmount.get()
                } else {
                    targetOffsetX += scrollAmount.get()
                }
            } else {
                if (applyInverse(reverse)) {
                    targetOffsetY -= scrollAmount.get()
                } else {
                    targetOffsetY += scrollAmount.get()
                }
            }
            return true
        }
        return false
    }

    protected fun applyInverse(value: Boolean): Boolean = inverse.get() != value

    fun resetScroll() {
        tooltipOffsetX = 0
        tooltipOffsetY = 0
        targetOffsetX = 0
        targetOffsetY = 0
    }

    /** Alpine activation_key: the modifier that must be held for scrolling to apply. */
    enum class ActivationKey {
        NONE,
        SHIFT,
        CONTROL,
        ALT;

        fun isDown(client: Minecraft): Boolean = when (this) {
            NONE -> true
            SHIFT -> client.hasShiftDown()
            CONTROL -> client.hasControlDown()
            ALT -> client.hasAltDown()
        }

        override fun toString(): String = "activation_key." + name.lowercase(Locale.ROOT)
    }

    companion object {
        private val Instance = ScrollableTooltips()

        @JvmStatic
        fun getInstance(): ScrollableTooltips = Instance
    }
}
