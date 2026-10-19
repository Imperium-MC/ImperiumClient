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

import io.github.axolotlclient.modules.hitboxes.Hitboxes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitboxes (wave 5): emits the entity hitbox gizmos at the tail of
 * {@code LevelRenderer#collectPerFrameRenderThreadGizmos}, while that frame's render-thread
 * gizmo collector is active — the same route the vanilla F3+B debug renderer feeds. Own mixin
 * class, additive with the chunk-borders injection already registered on this method.
 */
@Mixin(LevelRenderer.class)
public abstract class HitboxGizmosMixin {

	@Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("TAIL"))
	private void axolotlclient$entityHitboxes(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
		Hitboxes.getInstance().emit();
	}
}
