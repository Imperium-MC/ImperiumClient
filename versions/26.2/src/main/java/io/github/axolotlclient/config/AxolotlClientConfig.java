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

package io.github.axolotlclient.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.ui.ConfigUI;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.RecreatableScreen;
import io.github.axolotlclient.AxolotlClientConfigCommon;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.config.screen.CreditsScreen;
import io.github.axolotlclient.config.screen.ProfilesScreen;
import io.github.axolotlclient.mixin.OverlayTextureAccessor;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class AxolotlClientConfig extends AxolotlClientConfigCommon {
	@Getter @AllArgsConstructor
	public enum TitleScreenFriendsButtonMode {
		ICON(true),
		BUTTON(false),
		;
		private final boolean isIcon;


		@Override
		public String toString() {
			return "title_screen_friends_button_mode."+ super.toString().toLowerCase(Locale.ROOT);
		}
	}

	public final BooleanOption lowShield = new BooleanOption("lowShield", false);

	public final BooleanOption customLoadingScreenColor = new BooleanOption("custom_loading_bg_color", false);
	public final ColorOption loadingScreenColor = new ColorOption("loadingBgColor", new Color(239, 50, 61, 255));

	public final GenericOption openCredits = new GenericOption("Credits", "Open Credits", () ->
		Minecraft.getInstance().gui.setScreen(new CreditsScreen(Minecraft.getInstance().gui.screen()))
	);

	public final EnumOption<TitleScreenFriendsButtonMode> titleScreenFriendsButtonMode = new EnumOption<>("title_screen_friends_button_mode", TitleScreenFriendsButtonMode.class, TitleScreenFriendsButtonMode.ICON);

	@Getter
	private final List<Option<?>> options = new ArrayList<>();

	public AxolotlClientConfig() {
		general.add(customLoadingScreenColor);
		general.add(loadingScreenColor);
		general.add(titleScreenFriendsButtonMode);
		general.add(openCredits);

		ConfigUI.getInstance().runWhenLoaded(() -> {
			general.getOptions().removeIf(o -> "configStyle".equals(o.getName()));
			String[] themes = ConfigUI.getInstance().getStyleNames().stream().map(s -> "configStyle." + s)
				.filter(s -> AxolotlClientCommon.SHADERS_SUPPORTED || !s.startsWith("rounded"))
				.toArray(String[]::new);
			if (themes.length > 1) {
				general.add(new StringArrayOption("configStyle", themes,
					"configStyle." + ConfigUI.getInstance().getCurrentStyle().getName(), s -> {
					ConfigUI.getInstance().setStyle(s.split("\\.")[1]);

					AxoMinecraftClient.getInstance().execute(() -> {
						Screen newScreen = RecreatableScreen.tryRecreate(Minecraft.getInstance().gui.screen());
						if (newScreen != null) {
							Minecraft.getInstance().gui.setScreen(newScreen);
						}
					});
				}) {
					@Override
					public void fromSerializedValue(String value) {
						super.fromSerializedValue(value);
						changeListener.onChange(get());
					}
				});
				AxolotlClient.getInstance().getConfigManager().load();
			} else {
				AxolotlClient.getInstance().getConfigManager().load();
			}
		});

		rendering.add(lowShield);

		general.add(new GenericOption("profiles.title", "profiles.configure", () ->
			Minecraft.getInstance().gui.setScreen(new ProfilesScreen(Minecraft.getInstance().gui.screen()))), false);
	}

	@Override
	protected void updateWindowTitle(boolean useCustom) {
		Minecraft.getInstance().updateTitle();
	}

	@Override
	protected void updateHitColor(Color value) {
		//noinspection resource
		DynamicTexture texture = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).axolotlclient$getTexture();
		texture.getPixels().fillRect(0, 0, 16, 8, ClientColors.ARGB.invertAlpha(value.toInt()));
		texture.upload();
	}
}
