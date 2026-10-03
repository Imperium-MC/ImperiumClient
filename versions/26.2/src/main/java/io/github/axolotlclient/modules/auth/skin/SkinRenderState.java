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

package io.github.axolotlclient.modules.auth.skin;

import io.github.axolotlclient.util.IdentifiablePiPRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public record SkinRenderState(boolean classicVariant,
							  Identifier skinTexture,
							  @Nullable Identifier cape,
							  float rotationX,
							  float rotationY,
							  float pivotY,
							  int x0,
							  int y0,
							  int x1,
							  int y1,
							  float scale,
							  @Nullable ScreenRectangle scissorArea,
							  @Nullable ScreenRectangle bounds,
							  SkinRenderer renderer) implements PictureInPictureRenderState, IdentifiablePiPRenderState<SkinRenderer> {

	public SkinRenderState(boolean classicVariant,
						   Identifier skinTexture,
						   @Nullable Identifier cape,
						   float rotationX,
						   float rotationY,
						   float pivotY,
						   int x0,
						   int y0,
						   int x1,
						   int y1,
						   float scale,
						   @Nullable ScreenRectangle scissorArea,
						   SkinRenderer renderer) {
		this(classicVariant, skinTexture, cape, rotationX, rotationY, pivotY, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), renderer);
	}
}
