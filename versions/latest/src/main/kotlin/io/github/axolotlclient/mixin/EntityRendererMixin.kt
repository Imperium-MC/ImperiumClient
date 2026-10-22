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

package io.github.axolotlclient.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.vertex.PoseStack
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.api.requests.UserRequest
import io.github.axolotlclient.modules.hypixel.LevelHead
import io.github.axolotlclient.modules.hypixel.NickHider
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod
import io.github.axolotlclient.util.duck.SubmitNodeCollectorExtension
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(EntityRenderer::class)
abstract class EntityRendererMixin {

    @WrapOperation(
        method = ["submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        )]
    )
    private fun `axolotlclient$modifiyName`(
        instance: SubmitNodeCollector, stack: PoseStack, vec3: Vec3, offset: Int, componentIn: Component,
        b: Boolean, light: Int, cameraRenderState: CameraRenderState, original: Operation<Void>,
        entityState: EntityRenderState
    ) {
        var component = componentIn
        if (AxolotlClient.config() != null && entityState is AvatarRenderState) {
            val mc = Minecraft.getInstance()
            if (mc.player != null) {
                val level: Level? = Minecraft.getInstance().level
                if (level == null) return
                val player = level.getEntity(entityState.id)
                    ?: return // some mods seem to create players without adding them to a level for gui rendering. why?
                val self = player.uuid == mc.player!!.uuid
                if (self && NickHider.getInstance().hideOwnName.get()) {
                    component = NickHider.getInstance()
                        .editComponent(component, player.name.string, NickHider.getInstance().hiddenNameSelf.get()) as Component
                } else if (!self && NickHider.getInstance().hideOtherNames.get()) {
                    component = NickHider.getInstance()
                        .editComponent(component, player.name.string, NickHider.getInstance().hiddenNameOthers.get()) as Component
                }
            }
        }
        original.call(instance, stack, vec3, offset, component, b, light, cameraRenderState)
    }

    @Inject(
        method = ["submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            ordinal = 1,
            shift = At.Shift.AFTER
        )]
    )
    fun `axolotlclient$addBadges`(
        entityRenderState: EntityRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState, offset: Int, ci: CallbackInfo
    ) {
        if (!entityRenderState.isDiscrete && entityRenderState is AvatarRenderState) {
            if (AxolotlClient.config().showBadges.get()) {
                val level = Minecraft.getInstance().level ?: return
                val entity = level.getEntity(entityRenderState.id)
                if (entity is Player && UserRequest.getOnline(entity.stringUUID)) {
                    (submitNodeCollector as SubmitNodeCollectorExtension).`axolotlclient$submitBadge`(
                        poseStack, entityRenderState.nameTagAttachment, offset, entityRenderState.nameTag!!,
                        !entityRenderState.isDiscrete, entityRenderState.lightCoords, camera
                    )
                }
            }
        }
    }

    @Inject(
        method = ["submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"],
        at = [At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V")]
    )
    private fun addLevel(
        entityState: EntityRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState, offset: Int, ci: CallbackInfo
    ) {
        val currentServer = Minecraft.getInstance().currentServer
        if (currentServer != null && currentServer.ip.endsWith("hypixel.net") && entityState is AvatarRenderState) {
            val level = Minecraft.getInstance().level ?: return
            val entity = level.getEntity(entityState.id)
            if (entity is Player) {
                if (BedwarsMod.getInstance().isEnabled && BedwarsMod.getInstance().inGame() && BedwarsMod.getInstance().bedwarsLevelHead.get()) {
                    val text = BedwarsMod.getInstance().game.get().getLevelHead(entity)
                    if (text != null) {
                        var y = if (entityState.showExtraEars) -20 else -10

                        if (LevelHead.getInstance().background.get()) {
                            y -= 2
                        }

                        (submitNodeCollector as SubmitNodeCollectorExtension).`axolotlclient$submitLevelHead`(
                            poseStack, entityState.nameTagAttachment, y,
                            Component.literal(text).withStyle { s -> s.withColor(LevelHead.getInstance().textColor.get().toInt()) },
                            !entityState.isDiscrete, entityState.lightCoords, camera
                        )
                    }
                } else if (LevelHead.getInstance().enabled.get()) {
                    val text = LevelHead.getInstance().getDisplayString(entity.stringUUID)

                    var y = if (entityState.showExtraEars) -20 else -10

                    if (LevelHead.getInstance().background.get()) {
                        y -= 2
                    }

                    (submitNodeCollector as SubmitNodeCollectorExtension).`axolotlclient$submitLevelHead`(
                        poseStack, entityState.nameTagAttachment, y,
                        Component.literal(text).withStyle { s -> s.withColor(LevelHead.getInstance().textColor.get().toInt()) },
                        !entityState.isDiscrete, entityState.lightCoords, camera
                    )
                }
            }
        }
    }
}
