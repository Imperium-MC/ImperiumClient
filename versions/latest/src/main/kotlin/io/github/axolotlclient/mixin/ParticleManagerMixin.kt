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
import io.github.axolotlclient.modules.particles.Particles
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.util.RandomSource
import org.jetbrains.annotations.Nullable
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ParticleEngine::class)
abstract class ParticleManagerMixin {

    @Inject(
        method = ["makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun `axolotlclient$afterCreation`(
        parameters: ParticleOptions, x: Double, y: Double, z: Double, velocityX: Double,
        velocityY: Double, velocityZ: Double, cir: CallbackInfoReturnable<Particle?>
    ) {
        if (!Particles.getInstance().getShowParticle(parameters.type)
            || Particles.getInstance().isDisabled(parameters.type)
        ) {
            cir.setReturnValue(null)
            cir.cancel()
        }
    }

    @WrapOperation(
        method = ["makeParticle"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleProvider;createParticle(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/util/RandomSource;)Lnet/minecraft/client/particle/Particle;"
        )]
    )
    private fun applyParticleOptions(
        instance: ParticleProvider<*>, t: ParticleOptions, level: ClientLevel, x: Double, y: Double,
        z: Double, xSpeed: Double, ySpeed: Double, zSpeed: Double, randomSource: RandomSource,
        original: Operation<Particle?>
    ): Particle? {
        val p = original.call(instance, t, level, x, y, z, xSpeed, ySpeed, zSpeed, randomSource)
        if (p is SingleQuadParticle) {
            Particles.getInstance().applyOptions(p, t.type)
        }
        return p
    }
}
