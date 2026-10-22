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
import io.github.axolotlclient.modules.autoutils.AutoWalk
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.hud.simple.ToggleSprintHud
import net.minecraft.client.Minecraft
import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.entity.player.Input
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(KeyboardInput::class)
abstract class KeyboardInputMixin {

    @WrapOperation(
        method = ["tick"],
        at = [At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;")]
    )
    private fun toggleModifiers(
        up: Boolean, down: Boolean, left: Boolean, right: Boolean, jump: Boolean, shift: Boolean,
        sprint: Boolean, original: Operation<Input>
    ): Input {
        var s = shift
        var sp = sprint
        var u = up
        val hud = HudManagerCommon.getInstance()!!.get(ToggleSprintHud.ID) as ToggleSprintHud
        if (hud.isEnabled()) {
            s = s || (hud.sneakToggled.get() && Minecraft.getInstance().gui.screen() == null)
            sp = sp || hud.sprintToggled.get()
        }
        // Auto-Walk's limited-tier module ORs its simulated forward key in here (wave 4):
        // the walk rides the ordinary input pipeline, exactly like ToggleSprint's modifiers.
        u = u || AutoWalk.getInstance().isWalking()
        return original.call(u, down, left, right, jump, s, sp)
    }
}
