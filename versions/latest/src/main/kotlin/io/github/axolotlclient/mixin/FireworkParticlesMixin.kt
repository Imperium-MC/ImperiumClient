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
import com.llamalad7.mixinextras.sugar.Share
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.client.particle.FireworkParticles
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.core.particles.ParticleOptions
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(FireworkParticles.Starter::class)
abstract class FireworkParticlesMixin {

    @WrapOperation(
        method = ["createParticle"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"
        )]
    )
    private fun fixDisregardingNullability(
        instance: ParticleEngine, particleData: ParticleOptions, x: Double, y: Double, z: Double,
        xSpeed: Double, ySpeed: Double, zSpeed: Double, original: Operation<Particle?>,
        @Share("created_particle") c: LocalRef<Particle?>
    ): Particle? {
        val p = original.call(instance, particleData, x, y, z, xSpeed, ySpeed, zSpeed)
        c.set(p)
        return p
    }

    @Inject(
        method = ["createParticle"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            shift = At.Shift.AFTER
        )],
        cancellable = true
    )
    private fun fixDisregardingNullabilityCancel(
        x: Double, y: Double, z: Double, xSpeed: Double, ySpeed: Double, zSpeed: Double,
        colors: IntList, fadeColors: IntList, trail: Boolean, twinkle: Boolean, ci: CallbackInfo,
        @Share("created_particle") c: LocalRef<Particle?>
    ) {
        if (c.get() == null) {
            ci.cancel()
        }
    }
}
