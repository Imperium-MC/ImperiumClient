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
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.hud.simple.ToggleSprintHud
import net.minecraft.client.Minecraft
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LivingEntity::class)
abstract class LivingEntityMixin protected constructor(type: EntityType<*>, world: Level) : Entity(type, world) {

    @Inject(method = ["handleDamageEvent"], at = [At("TAIL")])
    private fun onDamage(damageSource: DamageSource, ci: CallbackInfo) {
        // The erased cast mirrors the Java `(Entity) this instanceof Player`: after mixin
        // application `this` IS a LivingEntity (and Player extends that chain), but the
        // compiler cannot see the merged hierarchy from the stub superclass.
        if ((this as Any) is Player) {
            Events.PLAYER_HURT.invoker().accept(this as Player, damageSource.entity)
        }
    }

    /**
     * toggle_sneak_sprint/fly_while_swimming (Alpine parity): while swimming, the same fly
     * boost multiplier applies to the swim impulse. `travelInWater` (declared here on
     * LivingEntity) hands the water speed straight to `moveRelative`; scaling that one
     * call, gated to the local player, touches nothing else in the water physics. (The call
     * site's owner is LivingEntity even though moveRelative is declared on Entity — javac
     * emits the enclosing class as the invoke owner.)
     *
     * Wave-3 note: MixinExtras 0.5.5 requires the call's receiver as the handler's first
     * argument for this wrap (it crashed startup otherwise — "expected LivingEntity at
     * index 0"), and the receiver is also the first argument of `Operation.call`.
     */
    @WrapOperation(
        method = ["travelInWater"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V")]
    )
    private fun `axolotlclient$swimBoost`(
        self: LivingEntity, speed: Float, input: Vec3, original: Operation<Void>
    ) {
        var s = speed
        if (this === Minecraft.getInstance().player) {
            val hud = HudManagerCommon.getInstance()!!.get(ToggleSprintHud.ID) as ToggleSprintHud?
            if (hud != null && hud.flyWhileSwimming.get() && hud.flyBoostActive()) {
                s *= hud.flyBoostMultiplier.get()
            }
        }
        original.call(self, s, input)
    }
}
