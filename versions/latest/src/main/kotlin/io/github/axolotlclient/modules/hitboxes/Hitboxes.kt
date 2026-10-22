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

package io.github.axolotlclient.modules.hitboxes

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.modules.AbstractModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoProperties
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.min

/**
 * Hitboxes (wave 5): F3+B-style entity hitboxes on a keybind, outside the debug screen and with
 * configurable colours.
 */
class Hitboxes : AbstractModule() {
    @JvmField
    val toggleKey: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.hitboxes.toggle")

    @JvmField
    val enabled = BooleanOption("enabled", false)
    @JvmField
    val showEyeLine = BooleanOption("show_eye_line", true)
    @JvmField
    val playerColor = ColorOption("hitboxes.player_color", Color(0xFFFFFFFF.toInt()))
    @JvmField
    val mobColor = ColorOption("hitboxes.mob_color", Color(0xFFFFFFFF.toInt()))
    @JvmField
    val itemColor = ColorOption("hitboxes.item_color", Color(0xFF00FFFF.toInt()))
    @JvmField
    val eyeLineColor = ColorOption("hitboxes.eye_line_color", Color(0xFFFF0000.toInt()))

    private val category = OptionCategory.create("hitboxes")

    override fun init() {
        // Own gizmo collection window every frame the module is on (see CannonTrajectory
        // and WorldEditCui for the same BEFORE_GIZMOS pattern).
        LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { context ->
            if (!enabled.get() || client.level == null || client.player == null) {
                return@BeforeGizmos
            }
            context.levelRenderer().collectPerFrameRenderThreadGizmos().use { emit() }
        })
        category.add(enabled, showEyeLine, playerColor, mobColor, itemColor, eyeLineColor)
        AxolotlClient.config().general.add(category)

        toggleKey.`br$registerOnConsumeClick`(Runnable {
            enabled.toggle()
            AxolotlClientCommon.getInstance().saveConfig()
        })
    }

    /**
     * Emits the hitbox gizmos for every renderable entity. Called at the tail of
     * LevelRenderer#collectPerFrameRenderThreadGizmos, while that frame's collector is
     * active — the same route the vanilla F3+B renderer takes.
     */
    fun emit() {
        val minecraft = Minecraft.getInstance()
        if (!enabled.get() || minecraft.level == null || minecraft.player == null) {
            return
        }
        val partialTick = minecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
        for (entity in minecraft.level!!.entitiesForRendering()) {
            if (entity.isInvisible) {
                continue
            }
            if (entity === minecraft.cameraEntity && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
                continue
            }
            showHitbox(entity, partialTick)
        }
    }

    private fun showHitbox(entity: Entity, partialTick: Float) {
        val position = entity.position()
        val renderPosition = entity.getPosition(partialTick)
        val delta = renderPosition.subtract(position)
        val color = colorOf(entity).toInt()

        onTop(Gizmos.cuboid(entity.boundingBox.move(delta), GizmoStyle.stroke(color)))
        onTop(Gizmos.point(renderPosition, color, 2.0f))

        val vehicle = entity.vehicle
        if (vehicle != null) {
            val half = min(vehicle.bbWidth, entity.bbWidth) / 2.0f
            val seat = vehicle.getPassengerRidingPosition(entity).add(delta)
            onTop(
                Gizmos.cuboid(
                    AABB(
                        seat.x - half, seat.y, seat.z - half,
                        seat.x + half, seat.y + 0.0625, seat.z + half
                    ), GizmoStyle.stroke(0xFFFFFF00.toInt())
                )
            )
        }

        if (entity is LivingEntity && showEyeLine.get()) {
            val box = entity.boundingBox.move(delta)
            val eyeY = box.minY + entity.eyeHeight
            onTop(
                Gizmos.cuboid(
                    AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ),
                    GizmoStyle.stroke(eyeLineColor.get().toInt())
                )
            )

            val eyes = renderPosition.add(0.0, entity.eyeHeight.toDouble(), 0.0)
            val look = eyes.add(entity.getViewVector(partialTick).scale(2.0))
            onTop(Gizmos.arrow(eyes, look, 0xFF0000FF.toInt()))
        }

        if (entity is EnderDragon) {
            for (part in entity.subEntities) {
                val partPosition = part.position()
                val partRender = part.getPosition(partialTick)
                onTop(
                    Gizmos.cuboid(
                        part.boundingBox.move(partRender.subtract(partPosition)),
                        GizmoStyle.stroke(ARGB.colorFromFloat(1.0f, 0.25f, 1.0f, 0.0f))
                    )
                )
            }
        }
    }

    private fun colorOf(entity: Entity): Color = when (entity) {
        is Player -> playerColor.get()
        is ItemEntity -> itemColor.get()
        else -> mobColor.get()
    }

    companion object {
        private val Instance = Hitboxes()

        @JvmStatic
        fun getInstance(): Hitboxes = Instance

        private fun onTop(properties: GizmoProperties) {
            if (properties != null) {
                properties.setAlwaysOnTop()
            }
        }
    }
}
