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

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.SubtitlesHudHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubtitleOverlay.class)
public abstract class SubtitleOverlayMixin {

	@Shadow
	@Final
	private List<SubtitleOverlay.Subtitle> audibleSubtitles;

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", shift = At.Shift.AFTER))
	private void subtitlesHudPosition(GuiGraphicsExtractor graphics, CallbackInfo ci, @Local(name = "width") int width, @Local(name = "position") Vec3 position) {
		var subtitlesHud = (SubtitlesHudHud) HudManager.getInstance().get(SubtitlesHudHud.ID);
		if (subtitlesHud.isEnabled() && !audibleSubtitles.isEmpty()) {
			var lineHeight = 9;
			var h = (int) audibleSubtitles.stream().filter(s -> s.getClosest(position) != null).count() * (lineHeight + 1) + 2;
			var w = width + 4;
			if (!(Minecraft.getInstance().gui.screen() instanceof HudEditScreen)) {
				var updated = false;
				if (h != subtitlesHud.getContentHeight()) {
					subtitlesHud.setContentHeight(h);
					updated = true;
				}
				if (w != subtitlesHud.getContentWidth()) {
					subtitlesHud.setContentWidth(w);
					updated = true;
				}
				if (updated) {
					subtitlesHud.onBoundsUpdate();
				}
			}
			subtitlesHud.renderHud(graphics, 0);
			subtitlesHud.scale(graphics);
			graphics.br$translateMatrix(subtitlesHud.getContentX(), subtitlesHud.getContentY());
			graphics.br$translateMatrix((float) -graphics.guiWidth() + w, (float) -(graphics.guiHeight() - 35) + h - (lineHeight+1)/2f - 1);
		}
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void switchEntryBackground(GuiGraphicsExtractor instance, int x0, int y0, int x1, int y1, int col, Operation<Void> original) {
		var subtitlesHud = (SubtitlesHudHud) HudManager.getInstance().get(SubtitlesHudHud.ID);
		if (!subtitlesHud.isEnabled() || subtitlesHud.vanillaEntryBackground.get()) {
			original.call(instance, x0, y0, x1, y1, col);
		}
	}

	@WrapMethod(method = "extractRenderState")
	private void wrapExtraction(GuiGraphicsExtractor graphics, Operation<Void> original) {
		graphics.br$pushMatrix();
		original.call(graphics);
		graphics.br$popMatrix();
	}
}
