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

package io.github.axolotlclient;

import io.github.axolotlclient.imperium.menu.ImperiumMenuScreen;
import io.github.axolotlclient.imperium.menu.MenuEntry;
import io.github.axolotlclient.modules.hud.HudEditScreen;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.modules.hud.gui.hud.vanilla.TitleHud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

public class ImperiumMenuGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		// Keybinds only fire in-game with no screen open, so test the menu from a world like a player would.
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("imperium-ingame-hud");

			context.getInput().pressKey(InputConstants.KEY_RSHIFT);
			context.waitForScreen(ImperiumMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("imperium-menu-all");

			int total = context.computeOnClient(mc -> menu(mc).visibleEntries().size());
			check(total > 20, "menu should list the client's modules, got " + total);

			context.getInput().typeChars("fps");
			context.waitTicks(3);
			int fpsIndex = context.computeOnClient(mc -> indexOf(menu(mc), "fps"));
			check(fpsIndex >= 0, "searching \"fps\" should show the FPS HUD card");
			context.takeScreenshot("imperium-menu-search");

			// The run directory keeps config between runs, so pin the starting state.
			context.runOnClient(mc -> fpsHud().setEnabled(false));
			boolean before = context.computeOnClient(mc -> menu(mc).visibleEntries().get(fpsIndex).enabled());
			check(!before, "FPS HUD should start disabled");
			clickGui(context, context.computeOnClient(mc -> menu(mc).cardBodyCenter(fpsIndex)));
			context.waitTicks(3);
			boolean after = context.computeOnClient(mc -> menu(mc).visibleEntries().get(fpsIndex).enabled());
			check(after, "clicking the disabled FPS card should enable it");
			context.takeScreenshot("imperium-menu-toggled");

			clickGui(context, context.computeOnClient(mc -> menu(mc).cardGearCenter(fpsIndex)));
			context.waitFor(mc -> !(mc.gui.screen() instanceof ImperiumMenuScreen));
			context.waitTicks(5);
			context.takeScreenshot("imperium-menu-fps-settings");

			context.setScreen(() -> null);
			context.waitTicks(5);
			context.takeScreenshot("imperium-ingame-after-toggle");

			context.runOnClient(mc -> {
				var titles = HudManager.getInstance().get(TitleHud.ID);
				titles.setEnabled(true);
				titles.onBoundsUpdate();
				mc.gui.hud.setTimes(0, 200, 0);
				mc.gui.hud.setTitle(Component.literal("Ave, Imperator").withStyle(ChatFormatting.GOLD));
				mc.gui.hud.setSubtitle(Component.literal("Welcome to Rome").withStyle(ChatFormatting.RED));
			});
			context.waitTicks(5);
			context.takeScreenshot("imperium-title-hud");
			context.runOnClient(mc -> mc.gui.hud.clearTitles());

			// 26.3 numbers mouse buttons SDL-style (left = 1); a left drag must move the element, not open its settings.
			context.runOnClient(mc -> {
				fpsHud().setEnabled(true);
				fpsHud().onBoundsUpdate();
			});
			context.setScreen(() -> new HudEditScreen());
			context.waitTicks(5);
			int[] start = context.computeOnClient(mc -> {
				var bounds = fpsHud().getTrueBounds();
				return new int[]{bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2};
			});
			double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
			context.getInput().setCursorPos(start[0] * scale, start[1] * scale);
			// The editor picks move/resize from the hovered element while rendering, so let a frame see the hover first.
			context.waitTicks(2);
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			context.getInput().setCursorPos((start[0] + 40) * scale, (start[1] + 30) * scale);
			context.waitTicks(2);
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof HudEditScreen), "left-dragging a HUD element should not leave the HUD editor");
			int movedX = context.computeOnClient(mc -> fpsHud().getTrueBounds().x() + fpsHud().getTrueBounds().width() / 2);
			check(Math.abs(movedX - start[0]) >= 20, "dragging the FPS HUD should move it (from x=" + start[0] + " to " + movedX + ")");
			context.takeScreenshot("imperium-hud-editor-dragged");
		}
	}

	private static HudEntry fpsHud() {
		return HudManager.getInstance().getEntries().stream()
			.filter(e -> e.getId().toString().endsWith("fpshud"))
			.findFirst().orElseThrow(() -> new AssertionError("FPS HUD not registered"));
	}

	private static ImperiumMenuScreen menu(Minecraft mc) {
		if (mc.gui.screen() instanceof ImperiumMenuScreen screen) return screen;
		throw new AssertionError("expected ImperiumMenuScreen, got " + mc.gui.screen());
	}

	private static int indexOf(ImperiumMenuScreen screen, String query) {
		var entries = screen.visibleEntries();
		for (int i = 0; i < entries.size(); i++) {
			MenuEntry e = entries.get(i);
			if (e.category().name().equals("HUD") && e.name().toLowerCase().contains(query)) return i;
		}
		return -1;
	}

	// TestInput positions the cursor in window pixels; menu layout is in GUI-scaled units.
	private static void clickGui(ClientGameTestContext context, int[] guiPos) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiPos[0] * scale, guiPos[1] * scale);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
