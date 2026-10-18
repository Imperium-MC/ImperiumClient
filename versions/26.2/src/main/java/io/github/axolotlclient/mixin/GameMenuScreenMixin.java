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

import java.util.Objects;
import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import io.github.axolotlclient.api.API;
import io.github.axolotlclient.api.APIOptions;
import io.github.axolotlclient.api.ChatsSidebar;
import io.github.axolotlclient.api.FriendsScreen;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.modules.hypixel.HypixelAbstractionLayer;
import io.github.axolotlclient.modules.hypixel.HypixelMods;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

	protected GameMenuScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "createPauseMenu", at = @At("TAIL"))
	private void axolotlclient$addButtons(CallbackInfo ci, @Local(name = "gridLayout") GridLayout widget) {
		if (API.getInstance().isAuthenticated()) {
			int buttonY = height - 30;
			if (APIOptions.getInstance().addShortcutButtons.get()) {
				addRenderableWidget(Button.builder(Component.translatable("api.friends"),
						_ -> minecraft.gui.setScreen(new FriendsScreen(this)))
					.bounds(10, buttonY, 75, 20).build());
				buttonY -= 25;
			}
			addRenderableWidget(Button.builder(Component.translatable("api.chats"),
					_ -> minecraft.gui.setScreen(new ChatsSidebar(this)))
				.bounds(10, buttonY, 75, 20).build());
		}
		if (AxolotlClientConfigCommon.instance().gameMenuScreenOptionButtonMode.get().showButton()) {
			addRenderableWidget(new Button(widget.getX() + widget.getWidth(),
				widget.getY() + 50, 20, 20,
				Component.translatable("config"),
				_ -> minecraft.gui.setScreen(new HudEditScreen(this)), Supplier::get) {
				@Override
				public void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
					extractDefaultSprite(graphics);
					graphics.blit(RenderPipelines.GUI_TEXTURED, (Identifier) AxolotlClientCommon.BADGE_PATH, this.getX() + 2, this.getY() + 2, 0, 0, this.width - 4, this.height - 4, this.width - 4, this.height - 4);
				}
			});
		}
	}

	@ModifyArg(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;", ordinal = 1), index = 1)
	private Button.OnPress axolotlclient$clearFeatureRestrictions(Button.OnPress onPress) {
		return (buttonWidget) -> {
			if (Objects.equals(HypixelMods.getInstance().cacheMode.get(),
				HypixelMods.HypixelApiCacheMode.ON_CLIENT_DISCONNECT)) {
				HypixelAbstractionLayer.getInstance().clearPlayerData();
			}
			onPress.onPress(buttonWidget);
		};
	}

	@WrapMethod(method = "lambda$createPauseMenu$11")
	private void confirmDisconnect(Button button, Operation<Void> original) {
		if (minecraft.getCurrentServer() != null && AxolotlClient.config().confirmDisconnect.get()) {
			minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) original.call(button);
				else minecraft.gui.setScreen(null);
			}, Component.translatable("confirm_disconnect.title"), Component.translatable("confirm_disconnect.message", minecraft.getCurrentServer().ip)));
		} else {
			original.call(button);
		}
	}
}
