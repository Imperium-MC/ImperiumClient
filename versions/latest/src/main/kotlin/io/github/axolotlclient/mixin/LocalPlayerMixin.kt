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
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.simple.ToggleSprintHud
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Abilities
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/**
 * toggle_sneak_sprint/vertical_boost (Alpine parity): while flying, the climb/sink impulse
 * in `LocalPlayer#aiStep` reads `Abilities#getFlyingSpeed()` directly — the one
 * call in that method — instead of going through `Player#getFlyingSpeed()` the
 * horizontal boost hooks. Wrapping it extends the fly boost to vertical flight.
 */
@Mixin(LocalPlayer::class)
abstract class LocalPlayerMixin {

    @WrapOperation(
        method = ["aiStep"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F")]
    )
    private fun `axolotlclient$verticalFlyBoost`(abilities: Abilities, original: Operation<Float>): Float {
        val speed = original.call(abilities)
        if (!abilities.flying) {
            return speed
        }
        val hud = HudManager.getInstance().get(ToggleSprintHud.ID) as ToggleSprintHud?
            ?: return speed
        if (!hud.verticalBoost.get() || !hud.flyBoostActive()) {
            return speed
        }
        return speed * hud.flyBoostMultiplier.get()
    }
}
