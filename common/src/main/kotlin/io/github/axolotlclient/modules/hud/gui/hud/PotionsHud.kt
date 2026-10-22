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

import java.util.Locale

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffects
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.CardinalOrder
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.CommonUtil

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class PotionsHud : TextHudEntry(50, 200, true) {
    private val order: EnumOption<CardinalOrder> = DefaultOptions.getCardinalOrder(CardinalOrder.TOP_DOWN)

    private val mode: EnumOption<Mode> = EnumOption("mode", Mode::class.java, Mode.ICONS_AND_TEXT)
    private val showEffectName = BooleanOption("showEffectNames", true)

    // Alpine parity: show_icon forces the icons off even in icon modes (a text-only override
    // of the display mode); display_vanilla keeps the vanilla effect HUD drawn alongside this
    // HUD instead of cancelling it (read by the InGameHudMixin cancel guard).
    private val showIcon = BooleanOption("potionshud.show_icon", true)
    @JvmField
    val displayVanilla = BooleanOption("potionshud.display_vanilla", false)
    private val timerTextColor = ColorOption("potionshud.timer_text_color", Color.parse("#7F7F7F"))
    private val collapsed = BooleanOption("potionshud.collapsed", false)
    private val blinkLastSeconds = IntegerOption("potionshud.blink_last_seconds", 0, 0, 60)
    private val dynamicColor = BooleanOption("potionshud.dynamic_color", false)

    init {
        background = BooleanOption("background", false)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        assert(client.`br$getPlayer`() != null)
        renderEffects(graphics, client.`br$getPlayer`()!!.`br$getStatusEffects`())
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        val player = client.`br$getPlayer`() ?: return
        val effects = player.`br$getStatusEffects`()
        val noEffects = effects.isEmpty()
        val calcWidth = if (noEffects) 0 else calculateWidth(effects)
        val calcHeight = if (noEffects) 0 else calculateHeight(effects)
        var changed = false
        if (calcWidth != getContentWidth()) {
            setContentWidth(calcWidth)
            changed = true
        }
        if (calcHeight != getContentHeight()) {
            setContentHeight(calcHeight)
            changed = true
        }
        if (changed) {
            onBoundsUpdate()
        }
        if (noEffects) {
            return
        }
        super.render(ctx, delta)
    }

    private fun renderEffects(graphics: AxoRenderContext, effects: List<AxoStatusEffectInstance>) {
        val noEffects = effects.isEmpty()
        val calcWidth = if (noEffects) 0 else calculateWidth(effects)
        val calcHeight = if (noEffects) 0 else calculateHeight(effects)
        var changed = false
        if (calcWidth != getContentWidth()) {
            setContentWidth(calcWidth)
            changed = true
        }
        if (calcHeight != getContentHeight()) {
            setContentHeight(calcHeight)
            changed = true
        }
        if (changed) {
            onBoundsUpdate()
        }
        if (noEffects) {
            return
        }
        var lastPos = 0
        val direction = order.get()

        val bounds = contentBounds
        val x = bounds.x()
        val y = bounds.y()
        for (i in effects.indices) {
            val effect = effects[if (direction.direction == -1) i else effects.size - i - 1]
            if (direction.xAxis) {
                renderPotion(graphics, effect, x + lastPos + 2, y + 2)
                var nameWidth = 0
                if (showText()) {
                    nameWidth += graphics.`br$getFont`().`br$getWidth`(effect.`br$formatDuration`()) + 2
                    if (showEffectName.get()) {
                        nameWidth = maxOf(nameWidth, client.`br$getFont`().`br$getWidth`(formatNameAndAmplifier(effect)) + 2)
                    }
                }
                if (showIcons()) {
                    nameWidth += 20
                }
                lastPos += nameWidth
            } else {
                renderPotion(graphics, effect, x + 2, y + 2 + lastPos)
                lastPos += 20
            }
        }
    }

    private fun calculateWidth(effects: List<AxoStatusEffectInstance>): Int {
        val widthStreamFullName = effects.stream()
            .map { formatNameAndAmplifier(it) }
            .mapToInt { client.`br$getFont`().`br$getWidth`(it) }
        val widthStreamDuration = effects.stream()
            .map { it.`br$formatDuration`() }
            .mapToInt { client.`br$getFont`().`br$getWidth`(it) }

        var width = 2
        if (order.get().xAxis) {
            if (showIcons()) {
                width += 20 * effects.size
            }

            if (showText()) {
                width += if (!showEffectName.get()) {
                    widthStreamDuration.map { i -> i + 2 }.sum()
                } else {
                    widthStreamFullName.map { i -> i + 2 }.sum()
                }
            }
        } else {
            if (showIcons()) {
                width += 20
            }

            if (showText()) {
                width += if (!showEffectName.get()) {
                    widthStreamDuration.max().orElse(0) + 2
                } else {
                    widthStreamFullName.max().orElse(0) + 2
                }
            }
        }
        return width
    }

    private fun calculateHeight(effects: List<AxoStatusEffectInstance>): Int {
        return if ((order.get()).xAxis) {
            24
        } else {
            20 * effects.size + 2
        }
    }

    private fun renderPotion(graphics: AxoRenderContext, effect: AxoStatusEffectInstance, xParam: Int, y: Int) {
        val type = effect.`br$getType`()
        var x = xParam

        if (showIcons()) {
            if (!isIconBlinkingOff(effect)) {
                val tint = dynamicTint(effect)
                graphics.`br$drawTexture`(type.`br$getSprite`(), x, y, 18, 18, tint?.toInt() ?: -1)
            }
            x += 19
        }

        if (showText()) {
            val tint = dynamicTint(effect)
            val durationColor = tint ?: timerTextColor.get()
            if (showEffectName.get()) {
                graphics.`br$drawString`(formatNameAndAmplifier(effect), x, y + 1, textColor.get().toInt(), shadow.get())
                graphics.`br$drawString`(effect.`br$formatDuration`(), x, y + 1 + 10, durationColor.toInt(), shadow.get())
            } else {
                graphics.`br$drawString`(effect.`br$formatDuration`(), x, y + 5, durationColor.toInt(), shadow.get())
            }
        }
    }

    /**
     * Icons show in icon modes and while collapsed, unless `show_icon` is switched off
     * (a text-only override that wins over every icon mode).
     */
    private fun showIcons(): Boolean = showIcon.get() && (collapsed.get() || mode.get().hasIcons)

    private fun showText(): Boolean = !collapsed.get() && mode.get().hasText

    /**
     * Whether the icon is currently in its "off" blink phase: effects with less than
     * `blink_last_seconds` remaining toggle their icon on a ~250 ms cycle so expiring
     * effects are hard to miss.
     */
    private fun isIconBlinkingOff(effect: AxoStatusEffectInstance): Boolean {
        val threshold = blinkLastSeconds.get()
        if (threshold <= 0) {
            return false
        }
        val duration = effect.`br$getDuration`()
        if (duration < 0 || duration >= threshold * 20) {
            return false
        }
        return (System.currentTimeMillis() / 250L) % 2L == 0L
    }

    /**
     * Duration-based tint for icon and timer text: yellow in the last 30 seconds, orange in
     * the last 10, red in the last 3. Infinite effects are not tinted.
     */
    private fun dynamicTint(effect: AxoStatusEffectInstance): Color? {
        if (!dynamicColor.get()) {
            return null
        }
        val duration = effect.`br$getDuration`()
        if (duration < 0) {
            return null
        }
        val seconds = duration / 20f
        if (seconds <= 3) {
            return Colors.RED
        }
        if (seconds <= 10) {
            return ClientColors.GOLD
        }
        if (seconds <= 30) {
            return Colors.YELLOW
        }
        return null
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val effect = AxoStatusEffectInstance.create(AxoStatusEffects.SPEED, 9999)
        val jump = AxoStatusEffectInstance.create(AxoStatusEffects.JUMP_BOOST, 99999)
        val haste = AxoStatusEffectInstance.create(AxoStatusEffects.HASTE, -1)
        renderEffects(graphics, listOf(effect, jump, haste))
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(order)
        options.add(mode)
        options.add(collapsed)
        options.add(showIcon)
        options.add(displayVanilla)
        options.add(blinkLastSeconds)
        options.add(dynamicColor)
        options.add(showEffectName)
        options.add(timerTextColor)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    private enum class Mode(val hasIcons: Boolean, val hasText: Boolean) {
        ICONS_ONLY(true, false),
        TEXT_ONLY(false, true),
        ICONS_AND_TEXT(true, true);

        override fun toString(): String = "potionshud.mode." + super.toString().lowercase(Locale.ROOT)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "potionshud")

        private fun formatNameAndAmplifier(effect: AxoStatusEffectInstance): AxoText =
            effect.`br$getType`().`br$getDisplayName`().`br$copy`()
                .`br$append`(" ")
                .`br$append`(CommonUtil.toRoman(effect.`br$getAmplifier`() + 1))
    }
}
