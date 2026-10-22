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

package io.github.axolotlclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.simple.ToggleSprintHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

	public LivingEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void onDamage(DamageSource damageSource, CallbackInfo ci) {
		if ((Entity)this instanceof Player p) {
			Events.PLAYER_HURT.invoker().accept(p, damageSource.getEntity());
		}
	}

	/**
	 * toggle_sneak_sprint/fly_while_swimming (Alpine parity): while swimming, the same fly
	 * boost multiplier applies to the swim impulse. {@code travelInWater} (declared here on
	 * LivingEntity) hands the water speed straight to {@code moveRelative}; scaling that one
	 * call, gated to the local player, touches nothing else in the water physics. (The call
	 * site's owner is LivingEntity even though moveRelative is declared on Entity — javac
	 * emits the enclosing class as the invoke owner.)
	 */
	@WrapOperation(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
	private void axolotlclient$swimBoost(float speed, Vec3 input, Operation<Void> original) {
		if ((Object) this == Minecraft.getInstance().player) {
			ToggleSprintHud hud = (ToggleSprintHud) HudManager.getInstance().get(ToggleSprintHud.ID);
			if (hud != null && hud.flyWhileSwimming.get() && hud.flyBoostActive()) {
				speed *= hud.flyBoostMultiplier.get();
			}
		}
		original.call(speed, input);
	}
}
