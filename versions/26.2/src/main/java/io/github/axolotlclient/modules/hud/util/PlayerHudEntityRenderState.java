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

package io.github.axolotlclient.modules.hud.util;

import io.github.axolotlclient.util.IdentifiablePiPRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PlayerHudEntityRenderState(EntityRenderState renderState,
										 Vector3f translation,
										 Quaternionf rotation,
										 @Nullable Quaternionf overrideCameraAngle,
										 int x0,
										 int y0,
										 int x1,
										 int y1,
										 float scale,
										 @Nullable ScreenRectangle scissorArea,
										 @Nullable ScreenRectangle bounds,
										 PlayerHudEntityRenderer renderer) implements PictureInPictureRenderState, IdentifiablePiPRenderState<PlayerHudEntityRenderer> {

	public PlayerHudEntityRenderState(
		EntityRenderState entityRenderState,
		Vector3f vector3f,
		Quaternionf quaternionf,
		@Nullable Quaternionf quaternionf2,
		int i,
		int j,
		int k,
		int l,
		float f,
		@Nullable ScreenRectangle screenRectangle,
		PlayerHudEntityRenderer renderer
	) {
		this(entityRenderState, vector3f, quaternionf, quaternionf2, i, j, k, l, f, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle), renderer);
	}

}
