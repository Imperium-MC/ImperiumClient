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
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import io.github.axolotlclient.api.API;
import io.github.axolotlclient.api.APIOptions;
import io.github.axolotlclient.api.FriendsScreen;
import io.github.axolotlclient.api.NewsScreen;
import io.github.axolotlclient.api.chat.ChatListScreen;
import io.github.axolotlclient.api.requests.GlobalDataRequest;
import io.github.axolotlclient.modules.auth.Auth;
import io.github.axolotlclient.modules.auth.AuthWidget;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.util.ThreadExecuter;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

	@Shadow
	@Nullable
	private RealmsNotificationsScreen realmsNotificationsScreen;

	@Shadow
	private FriendsButton friends;

	protected TitleScreenMixin() {
		super(Component.empty());
	}

	@Inject(method = "createNormalMenuOptions", at = @At("HEAD"))
	private void addButtonsPre(int y, int rowHeight, CallbackInfoReturnable<Integer> cir) {
		int leftButtonY = 10;
		if (Auth.getInstance().showButton.get()) {
			var button = addRenderableWidget(new AuthWidget(10, leftButtonY));
			leftButtonY += button.getHeight() + 4;
		}
		if (APIOptions.getInstance().addShortcutButtons.get()) {
			int shortcutButtonY = leftButtonY;
			Runnable addApiButtons = () -> minecraft.execute(() -> {
				var bY = shortcutButtonY;
				if (!AxolotlClient.config().titleScreenFriendsButtonMode.get().isIcon()) {
					addRenderableWidget(Button.builder(Component.translatable("api.friends"),
						_ -> minecraft.gui.setScreen(new FriendsScreen(this))).bounds(10, bY, 50, 20).build());
					bY += 24;
				}
				addRenderableWidget(Button.builder(Component.translatable("api.chats"),
					_ -> minecraft.gui.setScreen(new ChatListScreen(this))).bounds(10, bY, 50, 20).build());
			});
			if (API.getInstance().isSocketConnected()) {
				addApiButtons.run();
			} else {
				API.addStartupListener(addApiButtons, API.ListenerType.ONCE);
			}
		}

		ThreadExecuter.scheduleTask(() -> GlobalDataRequest.get().thenAccept(data -> {
			int buttonY = 10;
			if (APIOptions.getInstance().updateNotifications.get() &&
				data.success() &&
				data.latestVersion().isNewerThan(AxolotlClient.VERSION)) {
				addRenderableWidget(Button.builder(Component.translatable("api.new_version_available"),
						ConfirmLinkScreen.confirmLink(this, "https://modrinth.com/mod/axolotlclient/versions"))
					.bounds(width - 90, buttonY, 80, 20).build());
				buttonY += 24;
			}
			if (APIOptions.getInstance().displayNotes.get() &&
				data.success() && !data.notes().isEmpty()) {
				addRenderableWidget(Button.builder(Component.translatable("api.notes"), _ ->
						minecraft.gui.setScreen(new NewsScreen(this)))
					.bounds(width - 90, buttonY, 80, 20).build());
			}
		}));
	}

	@Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$disableRealmsNotifications(CallbackInfoReturnable<Boolean> cir) {
		if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
			this.realmsNotificationsScreen = null;
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "createNormalMenuOptions",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;", ordinal = 3))
	private Button.Builder axolotlclient$noRealmsbutOptionsButton(Component message, Button.OnPress onPress, Operation<Button.Builder> original) {
		if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
			message = Component.translatable("config");
			onPress = _ -> minecraft.gui.setScreen(new HudEditScreen(this));
		}
		return original.call(message, onPress);
	}

	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"), index = 1)
	private String axolotlclient$setVersionText(String s) {
		return "Minecraft " + SharedConstants.getCurrentVersion().name() + "/AxolotlClient "
			+ AxolotlClient.VERSION;
	}

	@Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
	private void axolotlclient$noRealmsIcons(CallbackInfoReturnable<Boolean> cir) {
		if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;", ordinal = 3))
	private GuiEventListener modifyButtons(TitleScreen instance, GuiEventListener listener, Operation<GuiEventListener> original, @Local(name = "language") SpriteIconButton language, @Local(name = "accessibility") SpriteIconButton accessibility, @Local(name = "topPos") LocalIntRef topPos) {
		if (!AxolotlClient.config().titleScreenFriendsButtonMode.get().isIcon()) {
			topPos.set(topPos.get() + 12-24);
			language.setPosition(this.width / 2 - 100 - 24, topPos.get());
			accessibility.setPosition(this.width / 2 + 104, topPos.get());
			removeWidget(friends);
			if (listener instanceof Button options) {
				options.setY(topPos.get());
			}
		}
		return original.call(instance, listener);
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void checkAPIStart(CallbackInfo ci) {
		if (!API.getInstance().isSocketConnected() && !Auth.getInstance().getCurrent().isOffline()) {
			API.getInstance().startup(Auth.getInstance().getCurrent());
		}
	}
}
