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

import java.util.function.Supplier

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
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
class CompassHud : TextHudEntry(240, BASE_HEIGHT, false) {
    private val widthOption: IntegerOption = IntegerOption(
        "width", width,
        { newWidth: Int -> updateWidth(newWidth) }, 100, 800
    )

    private val lookingBox = ColorOption("lookingbox", Color(0x80000000.toInt()))
    private val degreesColor = ColorOption("degreescolor", Color(-1))
    private val majorIndicatorColor = ColorOption("majorindicator", Color(-1))
    private val minorIndicatorColor = ColorOption("minorindicator", Color(0xCCFFFFFF.toInt()))
    private val cardinalColor = ColorOption("cardinalcolor", ClientColors.WHITE)
    private val semiCardinalColor = ColorOption("semicardinalcolor", Color(0xFFAAAAAA.toInt()))
    private val invert = BooleanOption("invert_direction", false)
    private val showDegrees = BooleanOption("showdegrees", true)

    // Alpine parity M2: direction arrow + waypoint markers on the compass strip.
    private val showArrow = BooleanOption("compasshud.show_arrow", true) { updateHeight() }
    private val arrowColor = ColorOption("compasshud.arrow_color", Color(-1))
    private val showWaypoints = BooleanOption("compasshud.show_waypoints", true)
    private val waypointLabels = BooleanOption("compasshud.waypoint_labels", true)

    private fun updateWidth(newWidth: Int) {
        setWidth(newWidth)
        onBoundsUpdate()
    }

    private fun updateHeight() {
        val newHeight = BASE_HEIGHT + (if (showArrow.get()) ARROW_ROW_HEIGHT else 0)
        if (getHeight() != newHeight) {
            setHeight(newHeight)
            onBoundsUpdate()
        }
    }

    override fun postConfigLoad() {
        super.postConfigLoad()
        // The saved arrow setting may differ from the constructor default; re-apply the height.
        updateHeight()
    }

    override fun getId(): AxoIdentifier = ID

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        renderCompass(context)
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        renderCompass(context)
    }

    fun renderCompass(context: AxoRenderContext) {
        // N = 0
        // E = 90
        // S = 180
        // W = 270
        if (client.`br$getPlayer`() == null) {
            return
        }
        val halfWidth = width / 2f
        var degrees = (client.`br$getPlayer`()!!.`br$getYaw`() + 180) % 360
        if (degrees < -180) {
            degrees += 360
        }
        val start = degrees - 150 + 360
        val startIndicator = ((start + 8).toInt() / 15) * 15
        val amount = 21
        val dist = width / (amount)
        val pos = getPos()
        val x = pos.x()
        val y = pos.y() + 1
        val arrowOffset = if (showArrow.get()) ARROW_ROW_HEIGHT else 0
        context.`br$fillRect`(pos.x() + halfWidth.toInt() - 1, pos.y(), 3, 11, lookingBox.get())
        if (showDegrees.get()) {
            context.`br$drawCenteredString`(
                degrees.toInt().toString(), x + halfWidth.toInt(), y + 20 + arrowOffset, degreesColor.get(), shadow.get()
            )
        }
        var shift = (startIndicator - start) / 15f * dist
        if (invert.get()) {
            shift = dist - shift
        }
        context.`br$pushMatrix`()
        context.`br$translateMatrix`(shift, 0f)
        for (i in 0 until amount) {
            val d: Int = if (invert.get()) {
                (startIndicator + ((amount - i - 2) * 15)) % 360
            } else {
                (startIndicator + i * 15) % 360
            }
            val innerX = x + dist * (i + 1)
            val indicator = getIndicator(d)

            val trueDist: Float = if (invert.get()) {
                ((amount - i) * dist) - shift
            } else {
                ((i + 1) * dist) - shift
            }

            val targetOpacity = 1 - Math.abs(halfWidth - trueDist) / halfWidth
            if (indicator == Indicator.CARDINAL) {
                context.`br$fillRect`(
                    innerX, y, 1, 9,
                    majorIndicatorColor.get().withAlpha((majorIndicatorColor.get().alpha * targetOpacity).toInt())
                )
                var color = cardinalColor.get()
                color = color.withAlpha((color.alpha * targetOpacity).toInt())
                if (color.alpha > 0) {
                    context.`br$drawCenteredString`(getCardString(indicator, d), innerX + 1, y + 10, color, shadow.get())
                }
            } else if (indicator == Indicator.SEMI_CARDINAL) {
                var color = semiCardinalColor.get()
                color = color.withAlpha((color.alpha * targetOpacity).toInt())
                if (color.alpha > 0) {
                    context.`br$drawCenteredString`(getCardString(indicator, d), innerX + 1, y + 1, color, shadow.get())
                }
            } else {
                // We have to call .color() here so that transparency stays
                context.`br$fillRect`(
                    innerX, y, 1, 5,
                    minorIndicatorColor.get()
                        .withAlpha((minorIndicatorColor.get().alpha * targetOpacity).toInt()).toInt()
                )
            }
        }

        context.`br$popMatrix`()

        renderWaypointMarkers(context, x, y, halfWidth, dist, degrees)

        if (showArrow.get()) {
            // Small pixel arrow under the strip, tip pointing up at the current bearing.
            val cx = x + halfWidth.toInt()
            val arrowY = y + 20
            context.`br$fillRect`(cx, arrowY, 1, 1, arrowColor.get())
            context.`br$fillRect`(cx - 1, arrowY + 1, 3, 1, arrowColor.get())
            context.`br$fillRect`(cx - 2, arrowY + 2, 5, 1, arrowColor.get())
        }
    }

    /**
     * Draws one colored bar (plus an optional short name) per waypoint marker, positioned by
     * its absolute bearing relative to the player's heading. Markers fade out towards the
     * strip edges exactly like the tick indicators, and are skipped outside the ±150° window.
     */
    private fun renderWaypointMarkers(context: AxoRenderContext, x: Int, y: Int, halfWidth: Float, dist: Int, degrees: Float) {
        if (!showWaypoints.get()) {
            return
        }
        val markers = waypointProvider.get()
        if (markers == null || markers.isEmpty()) {
            return
        }
        for (marker in markers) {
            var delta = marker.bearing - degrees
            while (delta > 180) {
                delta -= 360
            }
            while (delta < -180) {
                delta += 360
            }
            if (Math.abs(delta) > 150) {
                continue
            }
            var offset = delta / 15f * dist
            if (invert.get()) {
                offset = -offset
            }
            val trueDist = halfWidth + offset
            val targetOpacity = 1 - Math.abs(halfWidth - trueDist) / halfWidth
            if (targetOpacity <= 0) {
                continue
            }
            val base = Color(marker.color)
            val color = base.withAlpha((base.alpha * targetOpacity).toInt())
            if (color.alpha <= 0) {
                continue
            }
            val mx = x + halfWidth.toInt() + Math.round(offset)
            context.`br$fillRect`(mx - MARKER_WIDTH / 2, y, MARKER_WIDTH, 11, color)
            if (waypointLabels.get() && Math.abs(offset) > 12) {
                // Skip the label near the center: there it would sit on top of the
                // looking box and the cardinal letter at the player's heading.
                val label = if (marker.label.length > 3) marker.label.substring(0, 3) else marker.label
                context.`br$drawCenteredString`(label, mx + 1, y + 10, color, shadow.get())
            }
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(widthOption)
        options.add(showDegrees)
        options.add(invert)
        options.add(showArrow)
        options.add(arrowColor)
        options.add(showWaypoints)
        options.add(waypointLabels)
        options.add(lookingBox)
        options.add(degreesColor)
        options.add(cardinalColor)
        options.add(semiCardinalColor)
        options.add(majorIndicatorColor)
        options.add(minorIndicatorColor)
        return options
    }

    override fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_MIDDLE

    private enum class Indicator {
        CARDINAL, SEMI_CARDINAL, SMALL
    }

    /**
     * A single marker on the compass strip.
     *
     * @property label waypoint name (only its first few characters are drawn)
     * @property color ARGB marker color
     * @property bearing absolute bearing of the waypoint from the player, N = 0, E = 90
     */
    @JvmRecord
    data class CompassMarker(val label: String, val color: Int, val bearing: Float)

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "compasshud")

        /** Height without the arrow row (ticks + cardinal letters + degrees readout). */
        private const val BASE_HEIGHT = 33

        /** Extra height reserved below the cardinal letters for the direction arrow. */
        private const val ARROW_ROW_HEIGHT = 7

        /** Width (in px) of the colored waypoint marker bars, distinct from the 1px tick marks. */
        private const val MARKER_WIDTH = 3

        /**
         * Feed for waypoint markers (Alpine parity M2). Common cannot see the latest-side
         * `WaypointManager`, so the version side registers a supplier here at init.
         */
        private var waypointProvider: Supplier<List<CompassMarker>> = Supplier { listOf() }

        @JvmStatic
        fun setWaypointProvider(provider: Supplier<List<CompassMarker>>?) {
            waypointProvider = provider ?: Supplier { listOf() }
        }

        private fun getIndicator(degrees: Int): Indicator {
            if (degrees % 90 == 0) {
                return Indicator.CARDINAL
            }
            if (degrees % 45 == 0) {
                return Indicator.SEMI_CARDINAL
            }
            return Indicator.SMALL
        }

        private fun getCardString(indicator: Indicator, degrees: Int): String {
            if (indicator == Indicator.CARDINAL) {
                return when (degrees) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> "NaD"
                }
            }
            return when (degrees) {
                45 -> "NE"
                135 -> "SE"
                225 -> "SW"
                315 -> "NW"
                else -> "NaD"
            }
        }
    }
}
