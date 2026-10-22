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

package io.github.axolotlclient.modules.hud.gui.hud.item

import java.util.Collections
import java.util.Locale
import java.util.function.Supplier
import java.util.stream.Stream

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.item.AxoEnchants
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.gui.layout.CardinalOrder
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.CommonUtil
import io.github.axolotlclient.util.ItemUtil

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ArmorHud : TextHudEntry(20, 100, true) {
    protected val showProtLvl = BooleanOption("showProtectionLevel", false)
    private val showDurabilityNumber = BooleanOption("show_durability_num", false)
    private val showMaxDurabilityNumber = BooleanOption("show_max_durability_num", false)
    private val durabilityFormat: EnumOption<DurabilityFormat> =
        EnumOption("armorhud.durability_format", DurabilityFormat::class.java, DurabilityFormat.NONE)
    private val durabilityColorTiers = BooleanOption("armorhud.durability_color", false)
    private val customDurabilityNumColor = BooleanOption("armorhud.custom_durability_num_color", false)
    private val durabilityNumColor = ColorOption("armorhud.durability_num_color", Colors.WHITE)

    // armor_status batch (Alpine parity): the durability readout can interpolate between two
    // configured colours (start = fresh, end = about to break) instead of the tiers/vanilla
    // bar colour; durability_color_default is the empty-track colour of the BARS format.
    private val durabilityGradient = BooleanOption("armorhud.durability_gradient", false)
    private val durabilityColorStart = ColorOption("armorhud.durability_color_start", Colors.GREEN)
    private val durabilityColorEnd = ColorOption("armorhud.durability_color_end", Colors.RED)
    private val durabilityColorDefault = ColorOption("armorhud.durability_color_default", Color(0xFF404040.toInt()))

    // display_name: the item's name next to (or under) its icon, in name_color.
    private val displayName = BooleanOption("armorhud.display_name", false)
    private val nameColor = ColorOption("armorhud.name_color", Colors.WHITE)

    // low_effect: a tint over items whose remaining durability percent has dropped to the
    // threshold or below.
    private val lowEffect = BooleanOption("armorhud.low_effect", false)
    private val lowEffectThreshold = IntegerOption("armorhud.low_effect_threshold", 20, 1, 100)
    private val lowEffectColor = ColorOption("armorhud.low_effect_color", Color(0x80FF3030.toInt()))
    private val mainHandItemPosition: EnumOption<MainHandItemPosition> =
        EnumOption("armorhud.main_hand_item_position", MainHandItemPosition::class.java, MainHandItemPosition.BOTTOM)
    private val order: EnumOption<CardinalOrder> = DefaultOptions.getCardinalOrder(CardinalOrder.LEFT_RIGHT)
    private val reverseArmorOrder = BooleanOption("armorhud.reverse_armor_order", false)
    private val hideIfEmpty: BooleanOption = DefaultOptions.getHideIfEmpty()

    /**
     * Armor pieces and their durability bars change through discrete events, not on their
     * own, so this entry can be served from the `hud_frame_limit` recording.
     */
    override fun dynamicContent(): Boolean = false

    override fun render(ctx: AxoRenderContext, delta: Float) {
        val player = client.`br$getPlayer`() ?: return
        if (hideIfEmpty.get() && Stream.concat(
                if (mainHandItemPosition.get() == MainHandItemPosition.DISABLED) {
                    Stream.empty()
                } else {
                    Stream.of(player.`br$getInventory`().`br$getMainHand`())
                },
                player.`br$getInventory`().`br$getArmor`().stream()
            ).allMatch { it.`br$isEmpty`() }
        ) {
            return
        }
        super.render(ctx, delta)
    }

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        val player = client.`br$getPlayer`() ?: return

        val mainHand = player.`br$getInventory`().`br$getMainHand`()

        renderInternal(
            graphics,
            mainHand,
            player.`br$getInventory`().`br$getArmor`(),
            ItemUtil.getTotal(client, mainHand.`br$getItem`())
        )
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        if (BridgeVersion.is26OrLater()) {
            if (client.`br$getWorld`() == null) {
                var w = if (order.get().xAxis) 20 else 100
                var h = if (order.get().xAxis) 100 else 20
                var updated = false
                if (mainHandItemPosition.get() == MainHandItemPosition.DISABLED) {
                    if (order.get().xAxis) h -= 20 else w -= 20
                }
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
                graphics.`br$pushMatrix`()
                if (order.get().xAxis) {
                    graphics.`br$rotateMatrixAround`(
                        Math.PI.toFloat() / 2f, pos.x() + getContentWidth() / 2f, pos.y() + getContentHeight() / 2f
                    )
                }
                graphics.`br$drawCenteredString`(
                    getName(),
                    pos.x() + getContentWidth() / 2,
                    pos.y() + getContentHeight() / 2 - graphics.`br$getFont`().`br$getFontHeight`() / 2,
                    textColor.get()
                )
                graphics.`br$popMatrix`()
                return
            }
        }
        renderInternal(graphics, PLACEHOLDER_MAIN_HAND.get(), PLACEHOLDER_GEAR.get(), 1)
    }

    private fun renderInternal(
        context: AxoRenderContext,
        mainHand: AxoItemStack, armorParam: List<AxoItemStack>,
        mainHandCount: Int
    ) {
        var armor = armorParam
        var width = 20
        var height = 100
        var boundsChanged = false
        val format = effectiveDurabilityFormat()

        if (reverseArmorOrder.get()) {
            armor = ArrayList(armor)
            Collections.reverse(armor)
        }

        var mhPos = mainHandItemPosition.get()
        if (hideIfEmpty.get() && mhPos != MainHandItemPosition.DISABLED && mainHand.`br$isEmpty`()) {
            mhPos = MainHandItemPosition.DISABLED
        }
        val order = this.order.get()
        if (order.xAxis) {
            var labelWidth = if (format != DurabilityFormat.NONE || displayName.get()) {
                (if (mhPos == MainHandItemPosition.DISABLED) armor.stream() else Stream.concat(Stream.of(mainHand), armor.stream()))
                    .mapToInt { stack -> xAxisLabelWidth(context, stack, format) }.max().orElse(0)
            } else {
                0
            }
            width += labelWidth
            if (width != getContentWidth()) {
                setContentWidth(width)
                boundsChanged = true
            }

            if (mhPos == MainHandItemPosition.DISABLED) {
                height -= 20
            }

            if (height != getContentHeight()) {
                setContentHeight(height)
                boundsChanged = true
            }
            if (boundsChanged) {
                onBoundsUpdate()
            }
            val pos = contentPos

            var lastY = 2 + (height - 20)

            if (mhPos == MainHandItemPosition.BOTTOM) {
                val x = pos.x() + 2
                val y = pos.y() + lastY
                if (order.direction == 1) {
                    renderXAxisLabel(context, mainHand, x, y)
                    renderMainItem(context, mainHand, x + labelWidth, y, mainHandCount)
                } else {
                    renderMainItem(context, mainHand, x, y, mainHandCount)
                    renderXAxisLabel(context, mainHand, x + 18, y)
                }
                lastY -= 20
            }

            for (stack in armor) {
                var label: String? = null

                if (showProtLvl.get() && stack.`br$hasEnchantment`(AxoEnchants.PROTECTION)) {
                    label = stack.`br$getEnchantment`(AxoEnchants.PROTECTION).toString()
                }

                val x = pos.x() + 2
                val y = pos.y() + lastY
                if (order.direction == 1) {
                    renderXAxisLabel(context, stack, x, y)
                    renderItem(context, stack, x + labelWidth, y, label)
                } else {
                    renderItem(context, stack, x, y, label)
                    renderXAxisLabel(context, stack, x + 18, y)
                }
                lastY -= 20
            }

            if (mhPos == MainHandItemPosition.TOP) {
                val x = pos.x() + 2
                val y = pos.y() + lastY
                if (order.direction == 1) {
                    renderXAxisLabel(context, mainHand, x, y)
                    renderMainItem(context, mainHand, x + labelWidth, y, mainHandCount)
                } else {
                    renderMainItem(context, mainHand, x, y, mainHandCount)
                    renderXAxisLabel(context, mainHand, x + 18, y)
                }
            }
        } else {
            var labelWidth = if (format != DurabilityFormat.NONE) {
                (if (mhPos == MainHandItemPosition.DISABLED) armor.stream() else Stream.concat(Stream.of(mainHand), armor.stream()))
                    .mapToInt { stack ->
                        if (format == DurabilityFormat.NUMBER_AND_MAX) {
                            val text1 = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()).toString()
                            val text2 = "/" + stack.`br$getMaxDamage`()
                            val t1W = context.`br$getFont`().`br$getWidth`(text1)
                            val t2W = context.`br$getFont`().`br$getWidth`(text2)
                            maxOf(t1W, t2W)
                        } else if (format == DurabilityFormat.BARS) {
                            BAR_WIDTH
                        } else {
                            context.`br$getFont`().`br$getWidth`(durabilityText(stack, format))
                        }
                    }.map { i -> i + 2 }.max().orElse(0)
            } else {
                0
            }
            if (displayName.get()) {
                val nameWidth = (if (mhPos == MainHandItemPosition.DISABLED) armor.stream() else Stream.concat(Stream.of(mainHand), armor.stream()))
                    .mapToInt { stack -> context.`br$getFont`().`br$getWidth`(stack.`br$getHoverName`()) }.max().orElse(0)
                labelWidth = maxOf(labelWidth, nameWidth + 2)
            }
            run {
                val n = width
                width = height - 6
                height = n
            }
            if (format != DurabilityFormat.NONE) {
                height += if (format == DurabilityFormat.NUMBER_AND_MAX) 20 else 10
            }
            if (displayName.get()) {
                height += 10
            }
            if (labelWidth > 0) {
                width = (if (mhPos == MainHandItemPosition.DISABLED) 4 else 5) * labelWidth + 2
            }
            if (width != getContentWidth()) {
                setContentWidth(width)
                boundsChanged = true
            }
            if (height != getContentHeight()) {
                setContentHeight(height)
                boundsChanged = true
            }
            if (boundsChanged) {
                onBoundsUpdate()
            }
            val pos = contentPos
            var x = pos.x() + 2
            val y = pos.y() + 2
            val stackWidth = 18
            labelWidth = maxOf(stackWidth, labelWidth)
            if (mhPos == MainHandItemPosition.TOP) {
                if (order == CardinalOrder.TOP_DOWN) {
                    val numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y)
                    renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y + numHeight, mainHandCount)
                    renderNameYAxis(context, mainHand, x + labelWidth / 2, y + numHeight + 18)
                } else {
                    renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y, mainHandCount)
                    val numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y + 18)
                    renderNameYAxis(context, mainHand, x + labelWidth / 2, y + 18 + numHeight)
                }
                x += labelWidth
            }
            for (stack in armor) {
                var label: String? = null

                if (showProtLvl.get() && stack.`br$hasEnchantment`(AxoEnchants.PROTECTION)) {
                    label = stack.`br$getEnchantment`(AxoEnchants.PROTECTION).toString()
                }

                if (order == CardinalOrder.TOP_DOWN) {
                    val numHeight = renderDurabilityNumberYAxis(context, stack, x + labelWidth / 2, y)
                    renderItem(context, stack, x - stackWidth / 2 + labelWidth / 2, y + numHeight, label)
                    renderNameYAxis(context, stack, x + labelWidth / 2, y + numHeight + 18)
                } else {
                    renderItem(context, stack, x - stackWidth / 2 + labelWidth / 2, y, label)
                    val numHeight = renderDurabilityNumberYAxis(context, stack, x + labelWidth / 2, y + 18)
                    renderNameYAxis(context, stack, x + labelWidth / 2, y + 18 + numHeight)
                }
                x += labelWidth
            }
            if (mhPos == MainHandItemPosition.BOTTOM) {
                if (order == CardinalOrder.TOP_DOWN) {
                    val numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y)
                    renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y + numHeight, mainHandCount)
                    renderNameYAxis(context, mainHand, x + labelWidth / 2, y + numHeight + 18)
                } else {
                    renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y, mainHandCount)
                    val numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y + 18)
                    renderNameYAxis(context, mainHand, x + labelWidth / 2, y + 18 + numHeight)
                }
            }
        }
    }

    fun renderMainItem(graphics: AxoRenderContext, stack: AxoItemStack, x: Int, y: Int, mainHandCount: Int) {
        renderItem(graphics, stack, x, y, if (mainHandCount == 1) null else mainHandCount.toString())
    }

    fun renderItem(graphics: AxoRenderContext, stack: AxoItemStack, x: Int, y: Int, labelOverride: String?) {
        graphics.`br$renderGuiItemModel`(stack, x, y)
        graphics.`br$renderGuiItemOverlay`(stack, x, y, labelOverride, textColor.get().toInt(), shadow.get())
        if (lowEffect.get() && isLowDurability(stack)) {
            graphics.`br$fillRect`(x, y, 18, 18, lowEffectColor.get().toInt())
        }
    }

    /** armor_status/low_effect: remaining durability percent at or below the threshold.  */
    private fun isLowDurability(stack: AxoItemStack): Boolean {
        if (stack.`br$isEmpty`() || stack.`br$getMaxDamage`() <= 0) {
            return false
        }
        val remainingPercent = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()) * 100f / stack.`br$getMaxDamage`()
        return remainingPercent <= lowEffectThreshold.get()
    }

    /** Width of the shared name/durability label column of one item in the X-axis layout.  */
    private fun xAxisLabelWidth(context: AxoRenderContext, stack: AxoItemStack, format: DurabilityFormat): Int {
        if (stack.`br$isEmpty`() || (format == DurabilityFormat.NONE && !displayName.get())) {
            return 0
        }
        var width = 0
        if (format != DurabilityFormat.NONE) {
            width = if (format == DurabilityFormat.BARS) BAR_WIDTH else context.`br$getFont`().`br$getWidth`(durabilityText(stack, format))
        }
        if (displayName.get()) {
            width = maxOf(width, context.`br$getFont`().`br$getWidth`(stack.`br$getHoverName`()))
        }
        return width + 2
    }

    /**
     * X-axis label column: the item name (display_name, top line) plus the durability
     * readout as text or a bar (bottom line, or centred when there is no name).
     */
    private fun renderXAxisLabel(graphics: AxoRenderContext, stack: AxoItemStack, x: Int, y: Int) {
        val format = effectiveDurabilityFormat()
        if (stack.`br$isEmpty`()) {
            return
        }
        val showName = displayName.get()
        val showDurability = format != DurabilityFormat.NONE && stack.`br$getMaxDamage`() > 0
        if (!showName && !showDurability) {
            return
        }
        var durabilityY = y + 10 - graphics.`br$getFont`().`br$getFontHeight`() / 2
        if (showName) {
            graphics.`br$drawString`(stack.`br$getHoverName`(), x, y + 1, nameColor.get().toInt(), shadow.get())
            durabilityY = y + 11
        }
        if (!showDurability) {
            return
        }
        if (format == DurabilityFormat.BARS) {
            renderDurabilityBar(graphics, x, if (showName) y + 15 else y + 9, BAR_WIDTH, stack)
        } else {
            graphics.`br$drawString`(durabilityText(stack, format), x, durabilityY, durabilityNumberColor(stack), shadow.get())
        }
    }

    /**
     * The BARS durability readout: a `durability_color_default` track with a fill in
     * the active durability colour (gradient / tiers / custom / vanilla bar colour).
     */
    private fun renderDurabilityBar(graphics: AxoRenderContext, x: Int, y: Int, width: Int, stack: AxoItemStack) {
        graphics.`br$fillRect`(x, y, width, BAR_HEIGHT, durabilityColorDefault.get().toInt())
        val fraction = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()) / stack.`br$getMaxDamage`().toFloat()
        val fill = Math.round(width * fraction)
        if (fill > 0) {
            graphics.`br$fillRect`(x, y, minOf(fill, width), BAR_HEIGHT, durabilityNumberColor(stack))
        }
    }

    /** display_name in the Y-axis layout: the item name centred under its column.  */
    private fun renderNameYAxis(graphics: AxoRenderContext, stack: AxoItemStack, centerX: Int, y: Int) {
        if (!displayName.get() || stack.`br$isEmpty`()) {
            return
        }
        val textWidth = graphics.`br$getFont`().`br$getWidth`(stack.`br$getHoverName`())
        graphics.`br$drawString`(stack.`br$getHoverName`(), centerX - textWidth / 2, y, nameColor.get().toInt(), shadow.get())
    }

    private fun renderDurabilityNumberYAxis(graphics: AxoRenderContext, stack: AxoItemStack, x: Int, y: Int): Int {
        val format = effectiveDurabilityFormat()
        if (format == DurabilityFormat.NONE) {
            return 0
        }
        if (stack.`br$isEmpty`() || stack.`br$getMaxDamage`() == 0) {
            return if (format == DurabilityFormat.NUMBER_AND_MAX) 20 else 10
        }
        if (format == DurabilityFormat.BARS) {
            renderDurabilityBar(graphics, x - BAR_WIDTH / 2, y + 4, BAR_WIDTH, stack)
            return 10
        }
        val textY = y + 10 / 2 - graphics.`br$getFont`().`br$getFontHeight`() / 2
        if (format == DurabilityFormat.NUMBER_AND_MAX) {
            val text1 = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()).toString()
            val text2 = "/" + stack.`br$getMaxDamage`()
            val t1W = graphics.`br$getFont`().`br$getWidth`(text1)
            val t2W = graphics.`br$getFont`().`br$getWidth`(text2)
            graphics.`br$drawString`(text1, x - t1W / 2, textY, durabilityNumberColor(stack), true)
            graphics.`br$drawString`(text2, x - t2W / 2, textY + 10, durabilityNumberColor(stack), true)
            return 20
        } else {
            val text = durabilityText(stack, format)
            graphics.`br$drawString`(text, x - graphics.`br$getFont`().`br$getWidth`(text) / 2, textY, durabilityNumberColor(stack), true)
            return 10
        }
    }

    private fun effectiveDurabilityFormat(): DurabilityFormat {
        val format = durabilityFormat.get()
        if (format != DurabilityFormat.NONE) {
            return format
        }
        // Legacy toggles keep working when no explicit format is selected.
        if (showDurabilityNumber.get() && showMaxDurabilityNumber.get()) {
            return DurabilityFormat.NUMBER_AND_MAX
        }
        if (showDurabilityNumber.get()) {
            return DurabilityFormat.NUMBER
        }
        if (showMaxDurabilityNumber.get()) {
            return DurabilityFormat.MAX
        }
        return DurabilityFormat.NONE
    }

    private fun durabilityNumberColor(stack: AxoItemStack): Int {
        if (stack.`br$getMaxDamage`() > 0) {
            // armor_status/durability gradient: interpolate start (fresh) -> end (about to
            // break); takes precedence over the tiers and the custom flat colour.
            if (durabilityGradient.get()) {
                val fraction = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()) / stack.`br$getMaxDamage`().toFloat()
                return ClientColors.blend(durabilityColorEnd.get(), durabilityColorStart.get(), fraction).toInt()
            }
            if (durabilityColorTiers.get()) {
                val percent = (stack.`br$getMaxDamage`() - stack.`br$getDamage`()) / stack.`br$getMaxDamage`().toFloat()
                if (percent > 0.5f) {
                    return Colors.GREEN.toInt()
                }
                if (percent > 0.2f) {
                    return Colors.YELLOW.toInt()
                }
                return Colors.RED.toInt()
            }
        }
        return if (customDurabilityNumColor.get()) durabilityNumColor.get().toInt() else ClientColors.ARGB.opaque(stack.`br$getBarColor`())
    }

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(showProtLvl)
        options.add(showDurabilityNumber)
        options.add(showMaxDurabilityNumber)
        options.add(durabilityFormat)
        options.add(durabilityGradient)
        options.add(durabilityColorStart)
        options.add(durabilityColorEnd)
        options.add(durabilityColorDefault)
        options.add(durabilityColorTiers)
        options.add(customDurabilityNumColor)
        options.add(durabilityNumColor)
        options.add(displayName)
        options.add(nameColor)
        options.add(lowEffect)
        options.add(lowEffectThreshold)
        options.add(lowEffectColor)
        options.add(mainHandItemPosition)
        options.add(order)
        options.add(reverseArmorOrder)
        options.add(hideIfEmpty)
        return options
    }

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_RIGHT

    private enum class MainHandItemPosition {
        BOTTOM,
        TOP,
        DISABLED;

        override fun toString(): String = "armorhud.main_hand_item_position." + super.toString().lowercase(Locale.ROOT)
    }

    private enum class DurabilityFormat {
        NONE,
        NUMBER,
        MAX,
        NUMBER_AND_MAX,
        PERCENT,
        BARS;

        override fun toString(): String = "armorhud.durability_format." + super.toString().lowercase(Locale.ROOT)
    }

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "armorhud")
        private val PLACEHOLDER_MAIN_HAND: Supplier<AxoItemStack> =
            CommonUtil.memoize { AxoItemStack.of(AxoItems.IRON_SWORD) }
        private val PLACEHOLDER_GEAR: Supplier<List<AxoItemStack>> = CommonUtil.memoize {
            listOf(
                AxoItemStack.of(AxoItems.IRON_BOOTS),
                AxoItemStack.of(AxoItems.IRON_LEGGINGS),
                AxoItemStack.of(AxoItems.IRON_CHESTPLATE),
                AxoItemStack.of(AxoItems.IRON_HELMET)
            )
        }

        private const val BAR_WIDTH = 18
        private const val BAR_HEIGHT = 2

        private fun durabilityText(stack: AxoItemStack, format: DurabilityFormat): String {
            val remaining = stack.`br$getMaxDamage`() - stack.`br$getDamage`()
            return when (format) {
                DurabilityFormat.NONE, DurabilityFormat.BARS -> ""
                DurabilityFormat.NUMBER -> remaining.toString()
                DurabilityFormat.MAX -> stack.`br$getMaxDamage`().toString()
                DurabilityFormat.NUMBER_AND_MAX -> "$remaining/" + stack.`br$getMaxDamage`()
                DurabilityFormat.PERCENT ->
                    if (stack.`br$getMaxDamage`() == 0) "0%" else Math.round(remaining * 100f / stack.`br$getMaxDamage`()).toString() + "%"
            }
        }
    }
}
