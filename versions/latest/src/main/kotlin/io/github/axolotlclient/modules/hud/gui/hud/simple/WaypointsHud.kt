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
import java.util.Locale

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.api.SimpleTextInputScreen
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.waypoints.Waypoint
import io.github.axolotlclient.modules.waypoints.WaypointManager
import io.github.axolotlclient.modules.waypoints.WaypointsScreen
import io.github.axolotlclient.util.Util
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Waypoint HUD (Alpine "waypoints" module, parity spec M2): every enabled waypoint of the
 * current dimension renders as one stacked label line — a direction arrow that rotates with
 * the player's view, the waypoint name in its color, and the distance in blocks.
 *
 * <p>The codebase has no world-to-screen projection math for HUD entries, so Alpine's 3D
 * tracers are approximated with compass-style on-screen arrows (CompassHud fallback noted
 * in the parity spec).</p>
 *
 * <p>This entry is also the module's runtime brain, because HudManagerCommon ticks and
 * inits every registered entry: [init] binds the add-waypoint key,
 * [tick] edge-triggers the death waypoint, and [postConfigLoad] re-parses
 * the persisted waypoint list after the config (or profile) loads.</p>
 */
class WaypointsHud : TextHudEntry(60, 13, true) {

    @JvmField
    val showDistance = BooleanOption("waypoints.show_distance", true)

    @JvmField
    val showArrow = BooleanOption("waypoints.show_arrow", true)

    @JvmField
    val deathWaypoints = BooleanOption("waypoints.death_points", true)

    // Alpine profile settings (M6): cap the death-point history per dimension and pick
    // the colour new death points get. Unbounded history keeps numbered names so the
    // list stays readable.
    @JvmField
    val limitDeathWaypoints = BooleanOption("waypoints.limit_death_waypoints", true)

    @JvmField
    val maxDeathWaypoints = IntegerOption("waypoints.max_death_waypoints", 1, 1, 20)

    @JvmField
    val deathWaypointColor = ColorOption("waypoints.death_waypoint_color", Color(Waypoint.DEATH_COLOR).immutable())

    /**
     * 3D tracers (world-space markers projected onto the screen, see io.github.axolotlclient.modules.waypoints.WaypointTracers);
     * rendered while the entry is enabled, independent of the stacked HUD list.
     */
    @JvmField
    val tracers = BooleanOption("waypoints.tracers", true)

    private val addWaypointKey: AxoKeybinding = AxoKeybinding.create(
        InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_B), "key.waypoints.add"
    )

    private val mc: Minecraft = super.client as Minecraft
    private var wasDeadOrDying = false

    init {
        background = BooleanOption("background", false)
    }

    override fun init() {
        addWaypointKey.`br$registerOnConsumeClick` { openAddScreen() }
    }

    private fun openAddScreen() {
        mc.execute(Runnable {
            if (mc.player == null) {
                return@Runnable
            }
            mc.gui.setScreen(
                SimpleTextInputScreen(
                    mc.gui.screen(),
                    Component.translatable("waypoints.add.title"),
                    Component.translatable("waypoints.add.name"),
                    java.util.function.Consumer { name: String -> addWaypointAtCurrentPosition(name) }
                )
            )
        })
    }

    override fun postConfigLoad() {
        WaypointManager.getInstance().loadFromOption()
    }

    override fun tickable(): Boolean = true

    /**
     * Death detection: no client-side death event exists in the bridge, so this
     * edge-triggers on the alive -> dead transition. Runs while the entry is enabled,
     * matching Alpine's module toggle semantics.
     */
    override fun tick() {
        val player = mc.player
        if (player == null) {
            wasDeadOrDying = false
            return
        }
        val dead = player.isDeadOrDying
        if (dead && !wasDeadOrDying && deathWaypoints.get()) {
            WaypointManager.getInstance().setDeathPoint(
                player.x, player.y, player.z, currentDimension(player), deathWaypointColor.get().toInt(),
                limitDeathWaypoints.get(), maxDeathWaypoints.get()
            )
            Util.addMessageToChatHud(Component.translatable("waypoints.death.set"))
        }
        wasDeadOrDying = dead
    }

    private class RenderRow(val waypoint: Waypoint, val distance: Double, val relativeAngleRad: Float)

    private fun collectRows(): List<RenderRow> {
        val player = mc.player ?: return emptyList()
        val rows = ArrayList<RenderRow>()
        val dimension = currentDimension(player)
        val px = player.x
        val py = player.y
        val pz = player.z
        val yaw = player.yRot
        for (wp in WaypointManager.getInstance().getForDimension(dimension)) {
            if (!wp.enabled) {
                continue
            }
            val dx = wp.x - px
            val dy = wp.y - py
            val dz = wp.z - pz
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            // MC yaw convention: 0 = +Z; facing vector is (-sin(yaw), cos(yaw)).
            val targetYaw = Math.toDegrees(Math.atan2(-dx, dz))
            var relative = ((targetYaw - yaw) % 360).toFloat()
            if (relative > 180) {
                relative -= 360
            } else if (relative < -180) {
                relative += 360
            }
            rows.add(RenderRow(wp, distance, Math.toRadians(relative.toDouble()).toFloat()))
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

        for (row in rows) {
            val wp = row.waypoint
            val label = wp.name
            val distanceText = if (showDistance.get()) " " + formatDistance(row.distance) else ""
            val textWidth = mc.font.width(label) + mc.font.width(distanceText)
            val rowWidth = (if (showArrow.get()) ARROW_BOX else 0) + textWidth
            width = max(width, rowWidth)

            var cursor = x
            if (showArrow.get()) {
                drawDirectionArrow(context, x + ARROW_BOX / 2, y + lineHeight / 2, row.relativeAngleRad, wp.color)
                cursor += ARROW_BOX
            }
            cursor = context.`br$drawString`(label, cursor, y, wp.color, shadow.get())
            if (showDistance.get()) {
                context.`br$drawString`(distanceText, cursor, y, textColor.get().toInt(), shadow.get())
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

    /**
     * A small pixel arrow pointing "up" (towards the top of the screen = straight ahead),
     * rotated around its center by the waypoint's relative bearing. Drawn as stacked
     * 1px rows so it rotates as one shape; positive angles rotate clockwise on screen.
     */
    private fun drawDirectionArrow(context: AxoRenderContext, cx: Int, cy: Int, angleRad: Float, color: Int) {
        context.`br$pushMatrix`()
        context.`br$rotateMatrixAround`(angleRad, cx.toFloat(), cy.toFloat())
        context.`br$fillRect`(cx, cy - 4, 1, 1, color) // tip
        context.`br$fillRect`(cx - 1, cy - 3, 3, 1, color)
        context.`br$fillRect`(cx - 2, cy - 2, 5, 1, color) // widest row
        context.`br$fillRect`(cx, cy - 1, 1, 5, color) // shaft
        context.`br$popMatrix`()
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
        options.add(showDistance)
        options.add(showArrow)
        options.add(deathWaypoints)
        options.add(limitDeathWaypoints)
        options.add(maxDeathWaypoints)
        options.add(deathWaypointColor)
        options.add(tracers)
        options.add(
            GenericOption("waypoints.open_list", "waypoints.open_list") {
                mc.execute(Runnable { mc.gui.setScreen(WaypointsScreen(null)) })
            }
        )
        return options
    }

    override fun getSaveOptions(): MutableList<Option<*>> {
        // The persisted waypoint list rides along in the module config (see WaypointManager).
        val options = super.getSaveOptions()
        options.add(WaypointManager.getInstance().getDataOption())
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "waypointshud")
        private const val PLACEHOLDER = "Waypoints"
        private const val ARROW_BOX = 11
        private const val LINE_SPACING = 1

        @JvmStatic
        fun currentDimension(player: LocalPlayer): String = player.level().dimension().identifier().toString()

        /**
         * Adds a waypoint named `name` at the player's current position and dimension.
         * Shared by the keybind input screen and the list screen's add button.
         */
        @JvmStatic
        fun addWaypointAtCurrentPosition(name: String?) {
            val mc = Minecraft.getInstance()
            val player = mc.player
            if (player == null || name == null || name.isBlank()) {
                return
            }
            val waypoint = Waypoint(
                name.trim(), player.x, player.y, player.z, currentDimension(player),
                WaypointManager.getInstance().nextColor(), true, false
            )
            WaypointManager.getInstance().add(waypoint)
            Util.addMessageToChatHud(Component.translatable("waypoints.added", waypoint.name))
        }

        /** Compact block-distance formatting: 42m below 1k, 1.2km above. Shared with the tracers. */
        @JvmStatic
        fun formatDistance(distance: Double): String {
            if (distance < 1000) {
                return distance.roundToInt().toString() + "m"
            }
            // Locale.ROOT pins the output regardless of the OS locale (a German or Arabic system
            // must not change HUD/tracer label text); the comma replace stays as belt-and-braces.
            // The trailing "m" is appended here — the pattern's "k" alone used to leave the label
            // as "1.2k" while the javadoc (and the tracers) promise "1.2km".
            return String.format(Locale.ROOT, "%.1fk", distance / 1000).replace(',', '.') + "m"
        }
    }
}
