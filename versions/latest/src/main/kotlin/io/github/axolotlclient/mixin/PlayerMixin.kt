/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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
import io.github.axolotlclient.modules.particles.Particles
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Abilities
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Player::class)
abstract class PlayerMixin protected constructor(
    entityType: EntityType<out LivingEntity>,
    level: Level
) : Avatar(entityType, level) {

    @Shadow
    abstract fun crit(entityHit: Entity)

    @Shadow
    abstract fun magicCrit(entityHit: Entity)

    @Inject(
        method = ["attack"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z")]
    )
    private fun onAttack(target: Entity, ci: CallbackInfo) {
        Events.PLAYER_ATTACK.invoker().accept(this as Player, target)
    }

    @Inject(method = ["attackVisualEffects"], at = [At("TAIL")])
    private fun onAttackVisualEffects(
        target: Entity, crit: Boolean, sweepAttack: Boolean, fullyCharged: Boolean,
        isStabAttack: Boolean, enchantedDamage: Float, ci: CallbackInfo
    ) {
        if (Particles.getInstance().getAlwaysOn(ParticleTypes.CRIT) && !crit) {
            crit(target)
        }
        if (Particles.getInstance().getAlwaysOn(ParticleTypes.ENCHANTED_HIT) && enchantedDamage == 0.0f) {
            magicCrit(target)
        }
    }

    @WrapOperation(
        method = ["getFlyingSpeed"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F")]
    )
    private fun `axolotlclient$flyBoost`(abilities: Abilities, original: Operation<Float>): Float {
        val speed = original.call(abilities)
        if (this !== Minecraft.getInstance().player || !abilities.flying) {
            return speed
        }

        val hud = HudManagerCommon.getInstance()!!.get(ToggleSprintHud.ID) as ToggleSprintHud?
        if (hud == null || !hud.flyBoostActive()) {
            return speed
        }

        return speed * hud.flyBoostMultiplier.get()
    }
}
