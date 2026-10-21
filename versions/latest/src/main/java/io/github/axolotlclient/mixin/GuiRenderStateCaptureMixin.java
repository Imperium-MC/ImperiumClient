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

import io.github.axolotlclient.util.HudFrameCache;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture side of {@code hud_frame_limit}: while a cacheable HUD entry renders,
 * {@link HudFrameCache} is armed and every element the extractor submits into the
 * {@link GuiRenderState} is recorded so it can be re-submitted unchanged on the frames
 * between records. The hooks are a single static boolean check whenever nothing is being
 * captured.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateCaptureMixin {

	@Inject(method = "addText", at = @At("HEAD"))
	private void axolotlclient$captureText(GuiTextRenderState element, CallbackInfo ci) {
		HudFrameCache.captureText(element);
	}

	@Inject(method = "addItem", at = @At("HEAD"))
	private void axolotlclient$captureItem(GuiItemRenderState element, CallbackInfo ci) {
		HudFrameCache.captureItem(element);
	}

	@Inject(method = "addGuiElement", at = @At("HEAD"))
	private void axolotlclient$captureGuiElement(GuiElementRenderState element, CallbackInfo ci) {
		HudFrameCache.captureGuiElement(element);
	}

	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
	private void axolotlclient$capturePicturesInPicture(PictureInPictureRenderState element, CallbackInfo ci) {
		HudFrameCache.capturePicturesInPicture(element);
	}
}
