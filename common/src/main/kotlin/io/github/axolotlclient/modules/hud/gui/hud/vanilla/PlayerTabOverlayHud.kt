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

import java.util.ArrayList

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint

class PlayerTabOverlayHud : TextHudEntry(150, 40, true) {
    @JvmField
    val showPlayerHeads = BooleanOption("showPlayerHeads", true)
    @JvmField
    val showHeader = BooleanOption("showHeader", true)
    @JvmField
    val showFooter = BooleanOption("showFooter", true)
    @JvmField
    val alwaysShowHeadLayer = BooleanOption("alwaysShowHeadLayer", false)
    @JvmField
    val numericalPing = BooleanOption("numericalPing", true)
    @JvmField
    val highlightSelf = BooleanOption("highlightSelf", true)
    private val selfHighlightColor = ColorOption("selfHighlightColor", Color.parse("#40408CFF"))

    // Alpine profile settings (M6): rows of the player's group (their ImperiumMC legion)
    // get their own row highlight. The member-name set is owned by the version side
    // (LegionStateRegistry via the API fallback / legion channel); when it is empty —
    // no legion or names not delivered — no row matches and nothing changes.
    @JvmField
    val highlightGroupMembers = BooleanOption("highlight_group_members", false)
    private val groupHighlightColor = ColorOption("highlight_color_group", Color.parse("#4040AA55"))
    private val smallPingText = BooleanOption("tablist.small_ping_text", false)
    private val pingColor0 = ColorOption("pingColor0", Color.parse("#FF00FFFF"))
    private val pingColor1 = ColorOption("pingColor1", Color.parse("#FF00FF00"))
    private val pingColor2 = ColorOption("pingColor2", Color.parse("#FF008800"))
    private val pingColor3 = ColorOption("pingColor3", Color.parse("#FFFFFF00"))
    private val pingColor4 = ColorOption("pingColor4", Color.parse("#FFFF8800"))
    private val pingColor5 = ColorOption("pingColor5", Color.parse("#FFFF0000"))

    // shadows TextHudEntry's shared text-shadow option on purpose: the tab list
    // historically has its own "shadow" toggle (shadowed name kept in the ctor default)
    private val shadowOption = BooleanOption("shadow", true)
    @JvmField
    val customBackgroundColor = BooleanOption("custom_background_color", false)

    init {
        supportsScaling = false
    }

    fun renderNumericPing(graphics: AxoRenderContext, width: Int, x: Int, y: Int, entry: AxoPlayerListEntry): Boolean {
        if (numericalPing.get()) {
            val current: Color = when {
                entry.`br$getPing`() < 0 -> pingColor0.get()
                entry.`br$getPing`() < 150 -> pingColor1.get()
                entry.`br$getPing`() < 300 -> pingColor2.get()
                entry.`br$getPing`() < 600 -> pingColor3.get()
                entry.`br$getPing`() < 1000 -> pingColor4.get()
                else -> pingColor5.get()
            }

            val text = applySmallText(numericPingText(entry.`br$getPing`()))
            graphics.`br$pushMatrix`()
            graphics.`br$translateMatrix`((x + width - 1).toFloat(), y.toFloat())
            graphics.`br$translateMatrix`(-graphics.`br$getFont`().`br$getWidth`(text).toFloat(), 0f)

            if (smallPingText.get()) {
                graphics.`br$translateMatrix`(0f, -2f)
            }

            graphics.`br$drawString`(text, 0, 0, current, shadowOption.get())
            graphics.`br$popMatrix`()
            return true
        }
        return false
    }

    private fun applySmallText(text: String): String {
        if (smallPingText.get()) {
            val builder = StringBuilder(text.length)
            text.chars().map { i -> if (i >= '0'.code && i <= '9'.code) i + 0x2050 else i }
                .forEach { builder.appendCodePoint(it) }
            return builder.toString()
        }
        return text
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        // done in mixins with transformations
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        val pos = contentPos
        ctx.`br$drawCenteredString`(getName(), pos.x() + getContentWidth() / 2, pos.y() + getContentHeight() / 2, -1)
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = ArrayList<Option<*>>()
        options.add(enabled)
        options.addAll(
            listOf(
                background!!, customBackgroundColor, backgroundColor!!, roundBackground!!,
                backgroundRounding!!, backgroundPadding!!, outline!!, outlineColor!!
            )
        )
        options.add(anchor)
        options.addAll(
            listOf(
                numericalPing, smallPingText, showPlayerHeads, shadowOption, showHeader, showFooter,
                alwaysShowHeadLayer, highlightSelf, highlightGroupMembers
            )
        )
        options.addAll(
            listOf(
                pingColor0, pingColor1, pingColor2, pingColor3, pingColor4, pingColor5,
                selfHighlightColor, groupHighlightColor
            )
        )
        return options
    }

    override fun getId(): AxoIdentifier = ID

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_MIDDLE

    override fun getDefaultX(): Double = 0.5

    override fun getDefaultY(): Double = 0.01

    fun getBackgroundColor(): Color = backgroundColor!!.get()

    fun backgroundDisabled(): Boolean = !background!!.get()

    fun hasOutline(): Boolean = outline!!.get()

    fun getOutlineColor(): Color = outlineColor!!.get()

    fun hasRoundBackground(): Boolean = roundBackground!!.get()

    fun getBackgroundRounding(): Int = backgroundRounding!!.get()

    fun getBackgroundPadding(): Int = backgroundPadding!!.get()

    fun getSelfHighlightColor(): Int = selfHighlightColor.get().toInt()

    fun getGroupHighlightColor(): Int = groupHighlightColor.get().toInt()

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "tab_overlay_hud")

        /**
         * Text rendered in place of the vanilla ping bars, shared so that width
         * compensation in version mixins matches the drawn string.
         */
        @JvmStatic
        fun numericPingText(ping: Int): String = ping.toString() + "ms"
    }
}
