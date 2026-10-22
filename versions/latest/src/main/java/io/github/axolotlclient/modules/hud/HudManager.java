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

package io.github.axolotlclient.modules.hud;

import io.github.axolotlclient.imperium.menu.ImperiumMenuScreen;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud;
import io.github.axolotlclient.modules.hud.gui.hud.PackDisplayHud;
import io.github.axolotlclient.modules.hud.gui.hud.PlayerHud;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.*;
import io.github.axolotlclient.util.HudFrameCache;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;

import java.util.Comparator;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
public class HudManager extends HudManagerCommon {
	// getInstance() is inherited from HudManagerCommon (its Kotlin companion JvmStatic is
	// final and cannot be static-hidden); this field only forces eager singleton construction.
	@SuppressWarnings("unused")
	private final static HudManager instance = new HudManager();

	/**
	 * Class-loading accessor: touching this method runs the eager static above. The inherited
	 * companion getInstance() resolves against HudManagerCommon WITHOUT loading this class, so
	 * callers that must guarantee construction (module registration) go through here instead -
	 * the FeatureDisabler/Kotlin-companion trap, third sighting.
	 */
	public static HudManager construct() {
		return instance;
	}

	@Override
	protected void openScreen() {
		Minecraft.getInstance().gui.setScreen(new ImperiumMenuScreen(null));
	}

	@Override
	protected void addExtraHud() {
		setDebugScreenShown(() -> Minecraft.getInstance().gui.hud.getDebugOverlay().showDebugScreen());
		add(new ActionBarHud());
		add(new BossBarHud());
		add(new CrosshairHud());
		add(new DebugCountersHud());
		add(new HotbarHud());
		add(new ScoreboardHud());
		add(new TitleHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.CooldownsHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.BlockCoordinatesHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.EntityCountHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.ObsidianCountHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.PlaytimeHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.PotionCountHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.StopwatchHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.WaypointsHud());
		add(new io.github.axolotlclient.modules.minimap.MinimapHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.BoostsHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.LegionHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.BreadcrumbsHud());
		add(new io.github.axolotlclient.modules.hud.gui.hud.simple.NowPlayingHud());
		add(new KeystrokeHud());
		add(new PackDisplayHud());
		add(new PlayerHud());
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		final var mc = Profiler.get();
		mc.push("Hud render");
		if (hudsEnabled() && !(Minecraft.getInstance().gui.screen() instanceof HudEditScreen)) {
			// hud_frame_limit: cacheable entries (see HudEntry.dynamicContent) re-record at
			// most N times per second and are re-submitted from the recording in between;
			// dynamic entries always render live. Mirrors HudManagerCommon.render's z-sorted
			// loop, partitioned so the cache can wrap each cacheable entry's render.
			var ordered = getEntries();
			ordered.sort(Comparator.comparingInt(HudEntry::getZLevel));
			HudFrameCache.render(context, delta, ordered);
		}
		mc.pop();
	}

	@Override
	public void closeScreen() {
		var screen = Minecraft.getInstance().gui.screen();
		if (screen != null) {
			screen.onClose();
		}
	}
}
