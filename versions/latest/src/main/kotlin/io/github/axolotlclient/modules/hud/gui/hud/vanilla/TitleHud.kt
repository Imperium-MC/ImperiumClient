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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Movable title + subtitle (Alpine "titles" module, parity spec M2): the vanilla
 * title screen-text (the big centered announcement and its smaller second line)
 * rendered at a freely positionable HUD anchor instead of the fixed center.
 *
 * Vanilla still owns the STATE — InGameHudMixin forwards setTitle/setSubtitle/
 * setTimes/clearTitles calls here and cancels the vanilla extractTitle draw when
 * this entry is enabled — so fade timing, packet behaviour, and /title all work
 * unchanged. Alpha is derived from wall-clock since the last set (ticks*50ms),
 * matching the vanilla fadeIn/stay/fadeOut shape closely enough that a title
 * fades in, holds, and fades out exactly as without this module.
 *
 * The actual Component is drawn (as AxoText, never getString()), so per-section
 * colours from the title itself survive — the flat color only tints unstyled
 * sections, exactly like vanilla's own ARGB.white(alpha) title draw. Text wider
 * than the entry wraps to further centered lines (Font.split), and the entry
 * height grows to fit the wrapped lines instead of clipping them.
 */
class TitleHud : TextHudEntry(200, 31, false) {

    /** Scale of the title text (vanilla draws it ~2x). Subtitle renders at half this. */
    @JvmField
    val titleScale = IntegerOption("titlescale", 2, { updateHeight() }, 1, 4)

    @JvmField
    val customTextColor = BooleanOption("customtextcolor", false)

    @JvmField
    val renderBackgroundOption = BooleanOption("background", false)

    private val mc: Minecraft = super.client as Minecraft

    // Live title state, owned by vanilla via InGameHudMixin forwards.
    @Volatile
    private var title: Component = Component.empty()

    @Volatile
    private var subtitle: Component = Component.empty()

    @Volatile
    private var fadeInTicks = 10

    @Volatile
    private var stayTicks = 70

    @Volatile
    private var fadeOutTicks = 20

    @Volatile
    private var setAtMs: Long = 0

    @Volatile
    private var cleared = true

    /** Widest line actually drawn by the last render, entry units (scale included). Test seam. */
    @Volatile
    private var lastRenderedMaxLineWidth = -1

    // ── state (called from InGameHudMixin on the netty/render thread vanilla uses) ─────

    fun setTitle(title: Component?) {
        this.title = title ?: Component.empty()
        this.setAtMs = System.currentTimeMillis()
        this.cleared = false
        updateHeight()
    }

    fun setSubtitle(subtitle: Component?) {
        this.subtitle = subtitle ?: Component.empty()
        updateHeight()
    }

    fun setTimes(fadeIn: Int, stay: Int, fadeOut: Int) {
        this.fadeInTicks = max(0, fadeIn)
        this.stayTicks = max(0, stay)
        this.fadeOutTicks = max(0, fadeOut)
    }

    fun clearTitles() {
        this.cleared = true
        updateHeight()
    }

    // ── rendering ───────────────────────────────────────────────────────────────────────

    override fun renderComponent(graphics: AxoRenderContext, delta: Float) {
        if (cleared) {
            return
        }
        val alpha = currentAlpha()
        if (alpha <= 0.0f) {
            return
        }

        val baseX = (contentPos.x() + (getContentWidth() / 2f).roundToInt()).toInt()
        var y = contentPos.y().toInt()

        val titleText = this.title
        val subtitleText = this.subtitle
        val hasTitle = titleText.string.isNotEmpty()
        val hasSubtitle = subtitleText.string.isNotEmpty()
        val color = colorWithAlpha(alpha)

        // Scaled draws go through the context matrix stack: scale, draw centered at the
        // anchor, restore. Each line wraps at contentWidth/scale so the scaled result stays
        // inside the entry; the subtitle renders at half the title's scale below it.
        var maxLineWidth = -1
        if (hasTitle) {
            val scale = titleScale.get()
            val lines = wrappedLines(titleText, scale)
            maxLineWidth = drawWrapped(graphics, titleText, baseX, y, scale, lines, color)
            y += mc.font.lineHeight * scale * lines.size + 2
        }
        if (hasSubtitle) {
            val scale = max(1, titleScale.get() / 2)
            val lines = wrappedLines(subtitleText, scale)
            maxLineWidth = max(
                maxLineWidth,
                drawWrapped(graphics, subtitleText, baseX, y, scale, lines, color)
            )
        }
        lastRenderedMaxLineWidth = maxLineWidth
    }

    /**
     * Draws the component word-wrapped to the entry width at the given scale (inside its own
     * scaled, centered matrix push) and returns the widest line in entry units (scale included).
     */
    private fun drawWrapped(
        graphics: AxoRenderContext, text: Component, baseX: Int, y: Int, scale: Int,
        lines: List<FormattedCharSequence>, color: Int
    ): Int {
        // Measurement uses the same Font.split the renderer's word-wrap path uses, so the
        // widest line reported here is the widest line actually drawn.
        var widest = 0
        for (line in lines) {
            widest = max(widest, mc.font.width(line))
        }
        graphics.`br$pushMatrix`()
        graphics.`br$translateMatrix`(baseX.toFloat(), y.toFloat())
        graphics.`br$scaleMatrix`(scale.toFloat(), scale.toFloat())
        graphics.`br$drawCenteredWordWrap`(text as AxoText, 0, 0, wrapWidth(scale), shadow.get(), color)
        graphics.`br$popMatrix`()
        return widest * scale
    }

    /** The component split to the entry width at the given scale (same split the draw uses). */
    private fun wrappedLines(text: Component, scale: Int): List<FormattedCharSequence> {
        val wrapWidth = wrapWidth(scale)
        if (wrapWidth <= 0 || mc.font == null) {
            return emptyList()
        }
        return mc.font.split(text, wrapWidth)
    }

    /** Width available for one unscaled line: the entry content shrunk by the text scale. */
    private fun wrapWidth(scale: Int): Int {
        val width = if (getContentWidth() > 0) getContentWidth() else getWidth()
        return max(1, width / max(1, scale))
    }

    /** Grows/shrinks the entry height to fit the wrapped title + subtitle lines. */
    private fun updateHeight() {
        if (mc.font == null) {
            return
        }
        val titleText = this.title
        val subtitleText = this.subtitle
        val hasTitle = !cleared && titleText.string.isNotEmpty()
        val hasSubtitle = !cleared && subtitleText.string.isNotEmpty()
        var height = 0
        if (hasTitle) {
            val scale = titleScale.get()
            height += mc.font.lineHeight * scale * wrappedLines(titleText, scale).size
        }
        if (hasTitle && hasSubtitle) {
            height += 2
        }
        if (hasSubtitle) {
            val scale = max(1, titleScale.get() / 2)
            height += mc.font.lineHeight * scale * wrappedLines(subtitleText, scale).size
        }
        if (height == 0) {
            height = initialContentHeight
        }
        if (height != getHeight()) {
            setHeight(height)
            onBoundsUpdate()
        }
    }

    /** Widest line drawn by the last render, entry units (scale included); -1 when nothing was drawn. */
    fun lastRenderedMaxLineWidth(): Int = lastRenderedMaxLineWidth

    private fun currentAlpha(): Float {
        val elapsed = System.currentTimeMillis() - setAtMs
        val fadeIn = fadeInTicks * 50L
        val stay = stayTicks * 50L
        val fadeOut = fadeOutTicks * 50L
        if (elapsed < fadeIn && fadeIn > 0) {
            return elapsed / fadeIn.toFloat()
        }
        if (elapsed < fadeIn + stay) {
            return 1.0f
        }
        if (elapsed < fadeIn + stay + fadeOut && fadeOut > 0) {
            return 1.0f - (elapsed - fadeIn - stay) / fadeOut.toFloat()
        }
        return 0.0f
    }

    private fun colorWithAlpha(alpha: Float): Int {
        if (customTextColor.get()) {
            val c = textColor.get()
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt()).toInt()
        }
        return ARGB.color((alpha * 255).toInt(), 255, 255, 255)
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, delta: Float) {
        val baseX = (contentPos.x() + (getContentWidth() / 2f).roundToInt()).toInt()
        val y = contentPos.y().toInt()
        graphics.`br$drawString`(PLACEHOLDER_TITLE, baseX - mc.font.width(PLACEHOLDER_TITLE) / 2, y, -1, false)
        graphics.`br$drawString`(
            PLACEHOLDER_SUBTITLE, baseX - mc.font.width(PLACEHOLDER_SUBTITLE) / 2,
            y + mc.font.lineHeight + 2, Color(160, 160, 160, 160).toInt(), false
        )
    }

    override fun postConfigLoad() {
        super.postConfigLoad()
        // The saved scale (or a leftover tall height from a previous long title) may differ
        // from the constructor default; re-derive the height for the editor placeholder box.
        updateHeight()
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(titleScale)
        options.add(customTextColor)
        options.add(renderBackgroundOption)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "titlehud")
        private const val PLACEHOLDER_TITLE = "Title"
        private const val PLACEHOLDER_SUBTITLE = "Subtitle"
    }
}
