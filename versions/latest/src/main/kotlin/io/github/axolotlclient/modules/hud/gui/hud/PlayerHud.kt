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

import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.PlayerDirectionChangeEvent
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.util.PlayerHudEntityRenderState
import io.github.axolotlclient.modules.hud.util.PlayerHudEntityRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class PlayerHud : PlayerHudCommon() {

    private var reusedPlayerRendererState: LivingEntityRenderState? = null
    private var renderer: PlayerHudEntityRenderer? = null

    init {
        Events.PLAYER_DIRECTION_CHANGE.register(java.util.function.Consumer { event: PlayerDirectionChangeEvent -> onPlayerDirectionChange(event) })
    }

    fun onPlayerDirectionChange(event: PlayerDirectionChangeEvent) {
        yawOffset += (event.yaw - event.prevYaw) / 2
    }

    override fun tick() {
        super.tick()
        val client = Minecraft.getInstance()
        if (client.player != null && client.player!!.isVisuallySwimming) {
            val rawPitch = if (client.player!!.isInWater) client.player!!.xRot else 0.0f
            val pitch = Mth.lerp(client.player!!.getSwimAmount(1.0f), 0.0f, rawPitch)
            val height = client.player!!.bbHeight
            // sin = opposite / hypotenuse
            val offset = (sin(Math.toRadians(pitch.toDouble())) * height).toFloat() * 20
            yOffset = -offset
            if (pitch > 0) {
                yOffset -= (((1 / (1 + exp((pitch / 4).toDouble()))) - .5) * 40).toFloat()
            }
        } else if (client.player != null && client.player!!.isFallFlying) {
            // Elytra!
            val j = client.player!!.fallFlyingTicks + 1f
            val k = Mth.clamp(j * j / 100.0f, 0.0f, 1.0f)

            val pitch = k * (-90.0f - client.player!!.xRot) + 90
            val height = client.player!!.bbHeight
            // sin = opposite / hypotenuse
            val offset = (sin(Math.toRadians(pitch.toDouble())) * height).toFloat() * 50
            yOffset = -offset
            if (pitch < 0) {
                yOffset -= (((1 / (1 + exp((-pitch / 4).toDouble()))) - .5) * 40).toFloat()
            }
        } else {
            yOffset *= .8f
        }
    }

    override fun renderPlayer(ctx: AxoRenderContext, placeholder: Boolean, x: Double, y: Double, delta: Float) {
        val client = Minecraft.getInstance()
        val graphics = ctx as GuiGraphicsExtractor
        if (client.player == null) {
            return
        }

        val lerpY = lastYOffset + ((yOffset - lastYOffset) * delta)

        val scale = getScale() * 40

        val quaternion = Quaternionf().rotateZ(PI.toFloat())

        // Rotate to whatever is wanted. Also make sure to offset the yaw
        var deltaYaw = client.player!!.yHeadRot
        if (dynamicRotation.get()) {
            deltaYaw -= (lastYawOffset + ((yawOffset - lastYawOffset) * delta))
        }

        val quaternionf2 = Quaternionf().fromAxisAngleDeg(Vector3f(0f, 1f, 0f), deltaYaw - 180 + rotation.get().toFloat())
        quaternion.mul(quaternionf2)

        renderEntityInInventory(
            graphics,
            x.toInt(),
            y.toInt(),
            (x + getContentWidth() * getScale()).toInt(),
            (y + getContentHeight() * getScale()).toInt(),
            scale / client.player!!.scale,
            Vector3f(0f, (client.player!!.bbHeight / 2f) - lerpY / 40, 0f),
            quaternion,
            quaternionf2,
            client.player!!,
            delta
        )
    }

    private fun renderEntityInInventory(
        guiGraphicsExtractor: GuiGraphicsExtractor,
        i: Int,
        j: Int,
        k: Int,
        l: Int,
        f: Float,
        vector3f: Vector3f,
        quaternionf: Quaternionf,
        quaternionf2: Quaternionf?,
        livingEntity: net.minecraft.world.entity.LivingEntity,
        delta: Float
    ) {
        val mc = Minecraft.getInstance()
        val entityRenderDispatcher = mc.entityRenderDispatcher
        if (renderer == null) {
            renderer = PlayerHudEntityRenderer(entityRenderDispatcher)
        }
        @Suppress("UNCHECKED_CAST")
        val entityRenderer = entityRenderDispatcher.getRenderer(livingEntity) as
            net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.LivingEntity, LivingEntityRenderState>
        if (reusedPlayerRendererState == null) {
            reusedPlayerRendererState = entityRenderer.createRenderState()
        }
        val state = reusedPlayerRendererState!!
        entityRenderer.extractRenderState(livingEntity, state, delta)
        state.nameTag = null
        guiGraphicsExtractor.guiRenderState.addPicturesInPictureState(
            PlayerHudEntityRenderState(
                state, vector3f, quaternionf, quaternionf2, i, j, k, l, f,
                guiGraphicsExtractor.scissorStack.peek(), renderer!!
            )
        )
    }

    override fun isPerformingAction(): Boolean {
        // inspired by tr7zw's mod
        val player = Minecraft.getInstance().player!!
        return player.isCrouching || player.isSprinting || player.isFallFlying || player.abilities.flying ||
            player.isUnderWater || player.isVisuallySwimming || player.isPassenger || player.isUsingItem ||
            player.isHandsBusy || player.hurtTime > 0 || player.isOnFire
    }
}
