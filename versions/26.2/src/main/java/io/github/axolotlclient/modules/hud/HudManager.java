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

import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud;
import io.github.axolotlclient.modules.hud.gui.hud.PackDisplayHud;
import io.github.axolotlclient.modules.hud.gui.hud.PlayerHud;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.*;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
public class HudManager extends HudManagerCommon {
	@Getter
	private final static HudManager instance = new HudManager();

	@Override
	protected void openScreen() {
		Minecraft.getInstance().gui.setScreen(new HudEditScreen());
	}

	@Override
	protected void addExtraHud() {
		add(new ActionBarHud());
		add(new BossBarHud());
		add(new CrosshairHud());
		add(new DebugCountersHud());
		add(new HotbarHud());
		add(new ScoreboardHud());
		add(new KeystrokeHud());
		add(new PackDisplayHud());
		add(new PlayerHud());
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		final var mc = Profiler.get();
		mc.push("Hud render");
		if (!(Minecraft.getInstance().gui.screen() instanceof HudEditScreen)) {
			super.render(context, delta);
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
