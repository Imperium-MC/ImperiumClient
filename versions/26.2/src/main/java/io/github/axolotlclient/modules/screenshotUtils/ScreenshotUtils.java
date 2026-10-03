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

package io.github.axolotlclient.modules.screenshotUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption;
import io.github.axolotlclient.api.API;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.CommonUtil;
import io.github.axolotlclient.util.notifications.Notifications;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.*;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScreenshotUtils extends AbstractModule {

	@Getter
	private static final ScreenshotUtils Instance = new ScreenshotUtils();
	private final Map<BooleanSupplier, Action> actions = CommonUtil.make(new LinkedHashMap<>(), actions -> {
		actions.put(() -> true, new Action("copyAction", ChatFormatting.AQUA,
			"copy_image",
			ScreenshotCopying::copy));

		actions.put(() -> true, new Action("deleteAction", ChatFormatting.LIGHT_PURPLE,
			"delete_image",
			(file) -> {
				try {
					Files.delete(file);
					io.github.axolotlclient.util.Util.addMessageToChatHud(
						Component.literal(I18n.get("screenshot_deleted")
							.replace("<name>", file.getFileName().toString())));
				} catch (Exception e) {
					AxolotlClientCommon.getInstance().getLogger().warn("Couldn't delete Screenshot " + file.getFileName().toString());
				}
			}));

		actions.put(() -> true, new Action("openAction", ChatFormatting.WHITE,
			"open_image",
			(file) -> Util.getPlatform().openUri(file.toUri())));

		actions.put(() -> true, new Action("viewInGalleryAction", ChatFormatting.LIGHT_PURPLE, "view_in_gallery",
			file -> {
				try {
					ImageInstance instance = new ImageInstance.LocalImpl(file);
					//noinspection ResultOfMethodCallIgnored
					client.submit(() -> client.gui.setScreen(ImageScreen.create(null, CompletableFuture.completedFuture(instance), true)));
				} catch (Exception ignored) {
					io.github.axolotlclient.util.Util.addMessageToChatHud(Component.translatable("screenshot.gallery.view.error"));
				}
			}));

		actions.put(() -> API.getInstance().isAuthenticated(), new Action("uploadAction", ChatFormatting.AQUA,
			"upload_image",
			ImageShare.getInstance()::uploadImage));
	});
	private final OptionCategory category = OptionCategory.create("screenshotUtils");
	private final BooleanOption enabled = new BooleanOption("enabled", false);
	private final EnumOption<Mode> mode = new EnumOption<>("screenshot_utils.mode", Mode.class, Mode.CHAT);
	private final StringArrayOption autoExec = new StringArrayOption("autoExec", CommonUtil.make(new ArrayList<String>(), names -> {
		names.add("off");
		actions.forEach((_, action) -> names.add(action.translationKey()));
	}).toArray(String[]::new), "off");
	public final ColorOption toastBorderColor = new ColorOption("screenshot_utils.mode.toast.border_color", Colors.WHITE);
	public final AxoKeybinding screenshotCropBinding = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "screenshot_utils.screenshot_and_crop");

	@Override
	public void init() {
		category.add(enabled, mode, autoExec, new GenericOption("imageViewer", "openViewer", () ->
			client.gui.setScreen(new GalleryScreen(client.gui.screen()))), toastBorderColor);

		AxolotlClient.config().general.add(category);
		screenshotCropBinding.br$registerOnConsumeClick(() ->
			Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), img -> {
				var instance = new ImageInstance.Memory(img);
				var parent = client.gui.screen();
				client.gui.setScreen(new CropImageScreen(parent, instance, true));
			}));
	}

	public MutableComponent onScreenshotTaken(MutableComponent text, File shot) {
		if (enabled.get()) {
			Component t = getUtilsText(shot.toPath());
			if (t != null) {
				return text.append("\n").append(t);
			}
		}
		return text;
	}

	private @Nullable Component getUtilsText(Path file) {
		boolean autoex = !autoExec.get().equals("off");
		var mode = this.mode.get();
		if (mode.isToast) {
			try {
				Notifications.getInstance().addStatus(new ScreenshotToast(new ImageInstance.LocalImpl(file)));
			} catch (IOException e) {
				Notifications.getInstance().addStatus("screenshotUtils", "failed_to_load_toast");
			}
		}
		if (autoex) {
			actions.forEach((condition, action) -> {
				if (condition.getAsBoolean() && autoExec.get().equals(action.translationKey())) {
					CompletableFuture.runAsync(action.getClickEvent(file)::doAction, CompletableFuture.delayedExecutor(2, TimeUnit.MILLISECONDS, AxoMinecraftClient.getInstance()));
				}
			});
		}
		if (autoex || !mode.isChat) return null;

		MutableComponent message = Component.empty();
		actions.forEach((condition, action) -> {
			if (condition.getAsBoolean()) {
				message.append(action.getText(file)).append(" ");
			}
		});
		return message;
	}

	@AllArgsConstructor
	private enum Mode {
		CHAT(true, false),
		TOAST(false, true),
		CHAT_AND_TOAST(true, true);
		private final boolean isChat, isToast;

		@Override
		public String toString() {
			return "screenshot_utils.mode." + super.toString().toLowerCase(Locale.ROOT);
		}
	}

	public interface OnActionCall {

		void doAction(Path file);
	}

	public record Action(String translationKey, ChatFormatting formatting, String hoverTextKey,
						 OnActionCall clickEvent) {

		public Component getText(Path file) {
			return Component.translatable(translationKey).setStyle(Style.EMPTY.withColor(formatting)
				.withClickEvent(getClickEvent(file)).withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverTextKey))));
		}

		public CustomClickEvent getClickEvent(Path file) {
			return new CustomClickEvent(clickEvent, file);
		}
	}

	public static class CustomClickEvent implements ClickEvent {

		private final OnActionCall action;
		private final Path file;

		public CustomClickEvent(OnActionCall action, Path file) {
			this.action = action;
			this.file = file;
		}

		public void doAction() {
			if (file != null) {
				action.doAction(file);
			} else {
				AxolotlClientCommon.getInstance().getLogger().warn("How'd you manage to do this? "
					+ "Now there's a screenshot ClickEvent without a File attached to it!");
			}
		}

		@Override
		public @NotNull Action action() {
			return Action.OPEN_FILE;
		}
	}
}
