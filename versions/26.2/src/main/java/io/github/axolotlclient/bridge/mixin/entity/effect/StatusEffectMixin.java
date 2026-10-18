/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.bridge.mixin.entity.effect;

import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffect;
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl;
import io.github.axolotlclient.bridge.render.AxoSprite;
import io.github.axolotlclient.bridge.util.AxoText;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MobEffect.class)
public abstract class StatusEffectMixin implements AxoStatusEffect {
	@Shadow
	public abstract String getDescriptionId();

	@Override
	public AxoSprite br$getSprite() {
		var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect) (Object) this);
		return (AxoSpriteImpl) (minecraft, guiGraphics, x, y, w, h, color) ->
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				Hud.getMobEffectSprite(holder),
				x, y, w, h, color
			);
	}

	@Override
	public AxoText br$getDisplayName() {
		return AxoText.translatable(getDescriptionId());
	}
}
