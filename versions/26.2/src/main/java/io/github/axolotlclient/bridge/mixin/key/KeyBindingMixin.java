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

package io.github.axolotlclient.bridge.mixin.key;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKey;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An abstract representation of a keybind
 */
@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin implements AxoKeybinding {
	@Shadow
	public abstract boolean isDown();

	@Shadow
	protected InputConstants.Key key;

	@Shadow
	public abstract boolean consumeClick();

	@Shadow
	private int clickCount;
	@Unique
	private List<Runnable> axolotlclient$onClicked = null;

	@Unique
	private List<Runnable> axolotlclient$onConsumeClick = null;

	@Unique
	private List<Runnable> axolotlclient$onReleased = null;

	@Inject(method = "setDown", at = @At("HEAD"))
	private void dispatchHandlers(boolean pressed, CallbackInfo ci) {
		List<Runnable> handlers = pressed ? this.axolotlclient$onClicked : this.axolotlclient$onReleased;
		if (handlers != null) {
			handlers.forEach(Runnable::run);
		}
	}

	@Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)V", at = @At("TAIL"))
	private void registerClickHandler(String string, InputConstants.Type type, int i, KeyMapping.Category category, CallbackInfo ci) {
		Events.TICK.register(() -> {
			while (axolotlclient$onConsumeClick != null && consumeClick()) {
				axolotlclient$onConsumeClick.forEach(Runnable::run);
			}
		});
	}

	@Override
	public void br$registerOnClicked(Runnable runnable) {
		if (axolotlclient$onClicked == null) {
			axolotlclient$onClicked = new ArrayList<>();
		}
		axolotlclient$onClicked.add(runnable);
	}

	@Override
	public void br$registerOnReleased(Runnable runnable) {
		if (axolotlclient$onReleased == null) {
			axolotlclient$onReleased = new ArrayList<>();
		}
		axolotlclient$onReleased.add(runnable);
	}

	@Override
	public void br$registerOnConsumeClick(Runnable runnable) {
		if (axolotlclient$onConsumeClick == null) {
			axolotlclient$onConsumeClick = new ArrayList<>();
		}
		axolotlclient$onConsumeClick.add(runnable);
	}

	@Override
	public boolean br$isPressed() {
		return isDown();
	}

	@Override
	public AxoKey br$getBoundKey() {
		return key;
	}

	@Override
	public boolean br$consumeClick() {
		return consumeClick();
	}

	@Override
	public void br$click() {
		clickCount++;
	}
}
