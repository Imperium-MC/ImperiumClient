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

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.legions.Breadcrumb
import io.github.axolotlclient.modules.legions.BreadcrumbManager
import io.github.axolotlclient.util.Util
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Breadcrumbs HUD (Alpine "factions breadcrumbs" parity, reimplemented for Legions): every
 * crumb of the current dimension renders as one stacked distance label — name, distance in
 * blocks, and age — in the WaypointsHud style (same arrow/distance formatting).
 *
 * <p>This entry is the module's runtime brain, because HudManagerCommon ticks and inits
 * every registered entry: [init] binds the drop and clear-all keybinds, and
 * [postConfigLoad] re-parses the persisted crumb list after the config (or
 * profile) loads. Expiry is enforced lazily by [BreadcrumbManager.getForDimension]
 * so it keeps working with the HUD hidden.</p>
 */
class BreadcrumbsHud : TextHudEntry(60, 13, true) {

    @JvmField
    val showDistance = BooleanOption("breadcrumbs.show_distance", true)

    @JvmField
    val showAge = BooleanOption("breadcrumbs.show_age", true)

    @JvmField
    val showArrow = BooleanOption("breadcrumbs.show_arrow", true)

    @JvmField
    val expiry = EnumOption("breadcrumbs.expire", BreadcrumbManager.Expiry::class.java, BreadcrumbManager.Expiry.MINUTES_15)

    // Default N: deliberately not J — EnvironmentTogglesGameTest rebinds sneak to J and the
    // double-tap detector fires once per KeyMapping, so a second J-bound mapping would
    // double-fire its tap detection on a single press.
    private val dropKey: AxoKeybinding = AxoKeybinding.create(
        InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_N), "key.legions.breadcrumbs_drop"
    )
    private val clearKey: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.legions.breadcrumbs_clear")

    private val mc: Minecraft = super.client as Minecraft

    init {
        background = BooleanOption("background", false)
    }

    override fun init() {
        dropKey.`br$registerOnConsumeClick` { mc.execute(Runnable { dropAtCurrentPosition() }) }
        clearKey.`br$registerOnConsumeClick` {
            mc.execute(Runnable {
                BreadcrumbManager.getInstance().clearAll()
                Util.addMessageToChatHud(Component.translatable("breadcrumbs.cleared"))
            })
        }
    }

    override fun postConfigLoad() {
        BreadcrumbManager.getInstance().loadFromOption()
    }

    /** Drops a crumb at the player's feet in the current dimension. */
    private fun dropAtCurrentPosition() {
        val player = mc.player ?: return
        val crumb = BreadcrumbManager.getInstance().drop(
            player.x, player.y, player.z, WaypointsHud.currentDimension(player)
        )
        Util.addMessageToChatHud(Component.translatable("breadcrumbs.dropped", crumb.name))
    }

    private class RenderRow(val crumb: Breadcrumb, val distance: Double, val relativeAngleRad: Float)

    private fun collectRows(): List<RenderRow> {
        val player = mc.player ?: return emptyList()
        val rows = ArrayList<RenderRow>()
        val dimension = WaypointsHud.currentDimension(player)
        val px = player.x
        val py = player.y
        val pz = player.z
        val yaw = player.yRot
        for (crumb in BreadcrumbManager.getInstance().getForDimension(dimension, expiry.get().maxAgeMs)) {
            val dx = crumb.x - px
            val dy = crumb.y - py
            val dz = crumb.z - pz
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            // MC yaw convention: 0 = +Z; facing vector is (-sin(yaw), cos(yaw)).
            val targetYaw = Math.toDegrees(Math.atan2(-dx, dz))
            var relative = ((targetYaw - yaw) % 360).toFloat()
            if (relative > 180) {
                relative -= 360
            } else if (relative < -180) {
                relative += 360
            }
            rows.add(RenderRow(crumb, distance, Math.toRadians(relative.toDouble()).toFloat()))
        }
        return rows
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        val rows = collectRows()
        if (rows.isEmpty()) {
            if (getContentWidth() != 0 || getContentHeight() != 0) {
                setContentWidth(0)
                setContentHeight(0)
                onBoundsUpdate()
            }
            return
        }

        var x = contentPos.x().toInt()
        var y = contentPos.y().toInt()
        val lineHeight = mc.font.lineHeight
        var width = 0
        var height = 0
        val now = System.currentTimeMillis()

        for (row in rows) {
            val crumb = row.crumb
            val label = crumb.name
            val detail = StringBuilder()
            if (showDistance.get()) {
                detail.append(formatDistance(row.distance))
            }
            if (showAge.get()) {
                if (detail.isNotEmpty()) {
                    detail.append(" ")
                }
                detail.append(formatAge(now - crumb.droppedAtEpochMs))
            }
            val detailText = if (detail.isEmpty()) "" else " $detail"
            val textWidth = mc.font.width(label) + mc.font.width(detailText)
            val rowWidth = (if (showArrow.get()) ARROW_BOX else 0) + textWidth
            width = max(width, rowWidth)

            var cursor = x
            if (showArrow.get()) {
                drawDirectionArrow(context, x + ARROW_BOX / 2, y + lineHeight / 2, row.relativeAngleRad, CRUMB_COLOR)
                cursor += ARROW_BOX
            }
            cursor = context.`br$drawString`(label, cursor, y, CRUMB_COLOR, shadow.get())
            if (detailText.isNotEmpty()) {
                context.`br$drawString`(detailText, cursor, y, textColor.get().toInt(), shadow.get())
            }

            y += lineHeight + LINE_SPACING
            height += lineHeight + LINE_SPACING
        }

        if (width != getContentWidth() || height != getContentHeight()) {
            setContentWidth(width)
            setContentHeight(height)
            onBoundsUpdate()
        }
    }

    override fun render(context: AxoRenderContext, delta: Float) {
        if (mc.player == null) {
            return
        }
        super.render(context, delta)
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        val x = contentPos.x().toInt()
        val y = contentPos.y().toInt()
        context.`br$drawString`(PLACEHOLDER, x, y, -1, false)
    }

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(showDistance)
        options.add(showAge)
        options.add(showArrow)
        options.add(expiry)
        options.add(
            GenericOption("breadcrumbs.clear_all", "breadcrumbs.clear_all") {
                mc.execute(Runnable {
                    BreadcrumbManager.getInstance().clearAll()
                    Util.addMessageToChatHud(Component.translatable("breadcrumbs.cleared"))
                })
            }
        )
        return options
    }

    override fun getSaveOptions(): MutableList<Option<*>> {
        // The persisted crumb list rides along in the module config (see BreadcrumbManager).
        val options = super.getSaveOptions()
        options.add(BreadcrumbManager.getInstance().getDataOption())
        return options
    }

    /** The same rotating on-screen arrow WaypointsHud uses, in the crumb color. */
    private fun drawDirectionArrow(context: AxoRenderContext, cx: Int, cy: Int, angleRad: Float, color: Int) {
        context.`br$pushMatrix`()
        context.`br$rotateMatrixAround`(angleRad, cx.toFloat(), cy.toFloat())
        context.`br$fillRect`(cx, cy - 4, 1, 1, color) // tip
        context.`br$fillRect`(cx - 1, cy - 3, 3, 1, color)
        context.`br$fillRect`(cx - 2, cy - 2, 5, 1, color) // widest row
        context.`br$fillRect`(cx, cy - 1, 1, 5, color) // shaft
        context.`br$popMatrix`()
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "breadcrumbshud")
        private const val PLACEHOLDER = "Breadcrumbs"
        private const val ARROW_BOX = 11
        private const val LINE_SPACING = 1
        private const val CRUMB_COLOR = 0xFFFFC78A.toInt()

        /** Compact block-distance formatting: 42m below 1k, 1.2km above (WaypointsHud style). */
        private fun formatDistance(distance: Double): String {
            if (distance < 1000) {
                return distance.roundToInt().toString() + "m"
            }
            return String.format(Locale.ROOT, "%.1fk", distance / 1000).replace(',', '.')
        }

        /** "5m ago" style age: seconds below a minute, minutes below an hour, hours above. */
        private fun formatAge(ageMs: Long): String {
            val seconds = max(0, ageMs / 1000)
            if (seconds < 60) {
                return "${seconds}s ago"
            }
            val minutes = seconds / 60
            if (minutes < 60) {
                return "${minutes}m ago"
            }
            return "${minutes / 60}h ago"
        }
    }
}
