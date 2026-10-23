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

import java.util.ArrayList

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.imperium.ServerCooldownsRegistry
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.util.ClientColors
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Item cooldown bars (Alpine "cooldowns" module, parity spec M2): every item on the
 * hotbar with an active cooldown (ender pearls, chorus fruit, shields, modded items)
 * renders as a name line plus a horizontal progress bar draining to zero. Only the
 * hotbar is scanned — that's what the player can actually act on — using the public
 * ItemCooldowns API (isOnCooldown / getCooldownPercent), no accessor widening.
 *
 * The entry auto-sizes its box to the active list (PotionsHud pattern): with no
 * cooldowns running the module draws nothing, so it costs a hotbar scan per frame
 * and no render when idle.
 */
class CooldownsHud : TextHudEntry(80, 13, true) {

    @JvmField
    val showItemName = BooleanOption("showitemname", true)

    @JvmField
    val showSeconds = BooleanOption("showseconds", true)

    @JvmField
    val barColor = ColorOption("barcolor", Color(206, 164, 100).immutable())

    /** Bar thickness in pixels; the line height is this + 2. */
    @JvmField
    val barThickness = IntegerOption("barthickness", 2, 1, 6)

    // Alpine profile settings (M6): dedicated colour for the remaining-seconds text
    // (override_color + duration_color; off = the entry's regular text colour) and a
    // blink flash for rows about to expire (PotionsHud blink idiom, ~250 ms cycle).
    @JvmField
    val overrideColor = BooleanOption("cooldowns.override_color", false)

    @JvmField
    val durationColor = ColorOption("cooldowns.duration_color", Color(232, 197, 99).immutable())

    @JvmField
    val blink = BooleanOption("cooldowns.blink", false)

    private val mc: Minecraft = super.client as Minecraft

    init {
        background = BooleanOption("background", false)
    }

    private class ActiveCooldown(val name: String, val percent: Float, val remainingSeconds: Float)

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val active = scanCooldowns(delta)
        if (active.isEmpty()) {
            return
        }

        var x = contentPos.x().toInt()
        var y = contentPos.y().toInt()
        val width = getContentWidth()
        val thickness = barThickness.get()

        for (cd in active) {
            var lineY = y
            if (showItemName.get()) {
                val cursor = graphics.`br$drawString`(cd.name, x, lineY, textColor.get().toInt(), shadow.get())
                if (showSeconds.get() && cd.remainingSeconds >= 0.05f) {
                    val suffix = " (" + String.format("%.1f", cd.remainingSeconds) + "s)"
                    val secondsColor = if (overrideColor.get()) durationColor.get().toInt() else textColor.get().toInt()
                    if (!isBlinkingOff(cd)) {
                        graphics.`br$drawString`(suffix, cursor, lineY, secondsColor, shadow.get())
                    }
                }
                lineY += mc.font.lineHeight + 1
            }
            // Background track (subtle) then the filled portion draining with the cooldown.
            val trackColor = Color(0, 0, 0, 90).toInt()
            graphics.`br$fillRect`(x, lineY, width, thickness, trackColor)
            val filled = (width * cd.percent).roundToInt()
            if (filled > 0) {
                graphics.`br$fillRect`(x, lineY, min(filled, width), thickness, barColor.get().toInt())
            }
            y = lineY + thickness + LINE_SPACING
        }
    }

    /**
     * Whether a row's seconds text is currently in its "off" blink phase: rows with
     * under three seconds left toggle the suffix on a ~250 ms cycle (PotionsHud blink
     * idiom). Only server-pushed cooldowns know their exact remaining time; hotbar
     * items only expose a percent, so they blink for the final 15% of the cooldown.
     */
    private fun isBlinkingOff(cd: ActiveCooldown): Boolean {
        if (!blink.get()) {
            return false
        }
        val expiring = if (cd.remainingSeconds >= 0) cd.remainingSeconds < 3.0f else cd.percent < 0.15f
        return expiring && (System.currentTimeMillis() / 250L) % 2L == 0L
    }

    /** Hotbar slots 0-8; items on cooldown become render rows. Cheap: 9 checks/frame. */
    private fun scanCooldowns(delta: Float): List<ActiveCooldown> {
        val player = mc.player ?: return emptyList()
        val cooldowns = player.cooldowns
        val out = ArrayList<ActiveCooldown>()
        val lineH = mc.font.lineHeight + 1 + barThickness.get() + LINE_SPACING
        var height = 0
        var width = 0
        for (slot in 0..8) {
            val stack: ItemStack = player.inventory.getItem(slot)
            if (stack.isEmpty || !cooldowns.isOnCooldown(stack)) {
                continue
            }
            val percent = cooldowns.getCooldownPercent(stack, delta)
            // getCooldownPercent returns 1->0 as the cooldown runs OUT; remaining seconds
            // derive from the percent only as a display estimate (duration is not public).
            val name = stack.hoverName.string
            val rowWidth = if (showItemName.get()) {
                mc.font.width(name) + (if (showSeconds.get()) 34 else 0)
            } else {
                getContentWidth()
            }
            width = max(width, rowWidth)
            height += lineH
            out.add(ActiveCooldown(name, percent, -1f))
        }
        // Server-pushed feature cooldowns (imperiumclient:cooldowns) render beneath the
        // hotbar rows: the registry anchors deadlines at packet time and expires lazily.
        val now = System.currentTimeMillis()
        for (server in ServerCooldownsRegistry.active(now)) {
            val remainingSeconds = server.remainingMs(now) / 1000f
            val rowWidth = if (showItemName.get()) {
                mc.font.width(server.name()) + (if (showSeconds.get()) 34 else 0)
            } else {
                getContentWidth()
            }
            width = max(width, rowWidth)
            height += lineH
            out.add(ActiveCooldown(server.name(), server.remainingFraction(now), remainingSeconds))
        }
        if (out.isEmpty()) {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
        } else if (width != getContentWidth() || height != getContentHeight()) {
            setContentWidth(width)
            setContentHeight(height)
            onBoundsUpdate()
        }
        return out
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (mc.player == null) {
            return
        }
        super.render(context, delta)
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val x = contentPos.x().toInt()
        val y = contentPos.y().toInt()
        graphics.`br$drawString`(PLACEHOLDER, x, y, -1, false)
        val lineY = y + mc.font.lineHeight + 1
        graphics.`br$fillRect`(x, lineY, getContentWidth(), barThickness.get(), ClientColors.SELECTOR_BLUE.toInt())
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(showItemName)
        options.add(showSeconds)
        options.add(overrideColor)
        options.add(durationColor)
        options.add(blink)
        options.add(barColor)
        options.add(barThickness)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "cooldownshud")
        private const val PLACEHOLDER = "Cooldowns"
        private const val LINE_SPACING = 1
    }
}
