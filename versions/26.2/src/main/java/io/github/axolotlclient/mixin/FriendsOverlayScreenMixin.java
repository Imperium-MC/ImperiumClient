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

import java.util.function.Supplier;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.api.FriendsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FriendsOverlayScreen.class)
public abstract class FriendsOverlayScreenMixin extends Screen {

	@Shadow
	private @Nullable TabNavigationBar tabNavigationBar;
	@Shadow
	@Final
	private @Nullable Screen backgroundScreen;
	@Unique
	private Button axolotlclientButton;

	private FriendsOverlayScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/friends/FriendsOverlayScreen;repositionElements()V"))
	private void addButton(CallbackInfo ci) {
		if (tabNavigationBar == null) return;
		axolotlclientButton = addRenderableWidget(new Button(tabNavigationBar.getRight() + 4,
			tabNavigationBar.getY(), 20, 20,
			Component.translatable("config"),
			_ -> minecraft.gui.setScreen(backgroundScreen instanceof FriendsScreen ? backgroundScreen : new FriendsScreen(backgroundScreen)), Supplier::get) {
			@Override
			public void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
				extractDefaultSprite(graphics);
				graphics.blit(RenderPipelines.GUI_TEXTURED, (Identifier) AxolotlClientCommon.BADGE_PATH, this.getX() + 2, this.getY() + 2, 0, 0, this.width - 4, this.height - 4, this.width - 4, this.height - 4);
			}
		});
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void repositionButton(CallbackInfo ci) {
		if (axolotlclientButton != null && tabNavigationBar != null) {
			axolotlclientButton.setPosition(tabNavigationBar.getRight() + 4, tabNavigationBar.getY());
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void includeButtonClicks(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (axolotlclientButton != null && axolotlclientButton.isMouseOver(event.x(), event.y())) {
			if (axolotlclientButton.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
		}
	}
}
