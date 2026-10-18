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

package io.github.axolotlclient.mixin;

import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.modules.particles.Particles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends Avatar {

	protected PlayerMixin(EntityType<? extends @NotNull LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Shadow
	public abstract void crit(Entity entityHit);

	@Shadow
	public abstract void magicCrit(Entity entityHit);

	@Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private void onAttack(Entity target, CallbackInfo ci) {
		Events.PLAYER_ATTACK.invoker().accept((Player) (Object) this, target);
	}

	@Inject(method = "attackVisualEffects", at = @At("TAIL"))
	private void onAttackVisualEffects(Entity target, boolean crit, boolean sweepAttack, boolean fullyCharged, boolean isStabAttack, float enchantedDamage, CallbackInfo ci) {
		if (Particles.getInstance().getAlwaysOn(ParticleTypes.CRIT) && !crit) {
			crit(target);
		}
		if (Particles.getInstance().getAlwaysOn(ParticleTypes.ENCHANTED_HIT) && enchantedDamage == 0) {
			magicCrit(target);
		}
	}
}
