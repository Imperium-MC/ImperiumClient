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

package io.github.axolotlclient;

import io.github.axolotlclient.api.API;
import io.github.axolotlclient.api.APIOptions;
import io.github.axolotlclient.api.Options;
import io.github.axolotlclient.api.StatusUpdateProviderImpl;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.impl.Bridge;
import io.github.axolotlclient.modules.ModuleLoader;
import io.github.axolotlclient.modules.auth.Auth;
import io.github.axolotlclient.modules.blur.MotionBlur;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.hud.PackDisplayHud;
import io.github.axolotlclient.modules.hypixel.HypixelMods;
import io.github.axolotlclient.modules.particles.Particles;
import io.github.axolotlclient.modules.screenshotUtils.ScreenshotUtils;
import io.github.axolotlclient.modules.scrollableTooltips.ScrollableTooltips;
import io.github.axolotlclient.util.BadgeFeatureRenderer;
import io.github.axolotlclient.util.FeatureDisabler;
import io.github.axolotlclient.util.FeatureDisablerCommon;
import io.github.axolotlclient.util.notifications.Notifications;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry;

public class AxolotlClient extends AxolotlClientCommon implements ClientModInitializer {

	private void addBuiltinModules() {
		registerModule(HudManager.getInstance());
		registerModule(HypixelMods.getInstance());
		registerModule(MotionBlur.getInstance());
		registerModule(ScrollableTooltips.getInstance());
		registerModule(Particles.getInstance());
		registerModule(ScreenshotUtils.getInstance());
		registerModule(Auth.getInstance());
	}

	private void addExternalModules() {
		ModuleLoader.loadExternalModules().forEach(this::registerModule);
	}

	@Override
	public void onInitializeClient() {
		Bridge.init();

		addBuiltinModules();
		addExternalModules();

		init(Notifications.getInstance());
		new API(new StatusUpdateProviderImpl());

		Events.END_RESOURCE_RELOAD.register(() -> {
			PackDisplayHud hud = (PackDisplayHud) HudManager.getInstance().get(PackDisplayHud.ID);
			if (hud != null) {
				hud.update();
			}
		});
		FeatureRendererRegistry.register(BadgeFeatureRenderer.TYPE, BadgeFeatureRenderer::new);
		getLogger().debug("Debug Output enabled, Logs will be quite verbose!");
		getLogger().info("AxolotlClient Initialized");
	}

	@Override
	protected FeatureDisablerCommon getFeatureDisabler() {
		return FeatureDisabler.getInstance();
	}

	@Override
	protected AxolotlClientConfigCommon createConfig() {
		return new io.github.axolotlclient.config.AxolotlClientConfig();
	}

	@Override
	public Options getApiOptions() {
		return APIOptions.getInstance();
	}

	public static io.github.axolotlclient.config.AxolotlClientConfig config() {
		return (io.github.axolotlclient.config.AxolotlClientConfig) getInstance().getConfig();
	}
}
