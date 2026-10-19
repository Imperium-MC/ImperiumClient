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

package io.github.axolotlclient.imperium.emotes;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.imperium.menu.CarvedTheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The radial emote wheel overlay (W5-A). Hold the wheel keybind in-game and the defined emotes
 * fan out around a carved hub; point (or click) at a slot and release the key to send it through
 * {@link EmoteDispatch}. ESC cancels without sending.
 *
 * <p>Drawing follows the ImperiumMenuScreen conventions: {@link CarvedTheme} stone panels,
 * raised tiles, bronze hairlines and engraved capitals — the wheel reads as carved into the same
 * imperial identity, world visible behind it (transparent in-game background, no pause).</p>
 *
 * <p>Selection is the standard radial gesture: the mouse vector from the wheel centre picks the
 * nearest slot once it leaves the hub dead-zone, so nothing is selected while the pointer rests
 * in the middle. Defined emotes are distributed evenly starting at 12 o'clock; empty config
 * slots simply do not appear.</p>
 */
public class EmoteWheelScreen extends Screen {

	private static final int SLOT_SIZE = 26;
	private static final int HUB_SIZE = 68;
	/** Pointer distance from centre below which nothing is selected. */
	private static final double DEAD_ZONE = 34;

	private final KeyMapping keybind;
	private final List<Emote> emotes;

	private int selected = -1;

	public EmoteWheelScreen(KeyMapping keybind, List<Emote> emotes) {
		super(Component.translatable("imperiumclient.emotes.title"));
		this.keybind = keybind;
		this.emotes = List.copyOf(emotes);
	}

	// ── geometry (pure enough to assert from tests) ──

	private int centerX() {
		return width / 2;
	}

	private int centerY() {
		return height / 2;
	}

	private int radius() {
		return Math.max(46, Math.min(84, Math.min(width, height) / 4));
	}

	/** Slot i's angle in radians, 12 o'clock, clockwise, defined slots spread evenly. */
	double slotAngle(int index) {
		return -Math.PI / 2 + 2 * Math.PI * index / emotes.size();
	}

	@VisibleForTesting
	public int[] slotCenter(int index) {
		int r = radius();
		return new int[]{centerX() + (int) Math.round(Math.cos(slotAngle(index)) * r),
			centerY() + (int) Math.round(Math.sin(slotAngle(index)) * r)};
	}

	/**
	 * The slot the given pointer position selects, or -1 in the dead zone / outside the wheel's
	 * reach. Slot boundaries sit halfway between neighbours.
	 */
	@VisibleForTesting
	public int slotAt(double mouseX, double mouseY) {
		if (emotes.isEmpty()) {
			return -1;
		}
		double dx = mouseX - centerX(), dy = mouseY - centerY();
		if (Math.hypot(dx, dy) < DEAD_ZONE) {
			return -1;
		}
		double angle = Math.atan2(dy, dx); // -PI..PI, 0 = 3 o'clock
		// map onto [0, size) clockwise from 12 o'clock
		double slice = 2 * Math.PI / emotes.size();
		double offset = (angle + Math.PI / 2 + 2 * Math.PI) % (2 * Math.PI);
		int index = (int) Math.floor(offset / slice);
		return index >= 0 && index < emotes.size() ? index : -1;
	}

	private boolean inSlot(int index, double mouseX, double mouseY) {
		if (index < 0 || index >= emotes.size()) {
			return false;
		}
		int[] center = slotCenter(index);
		return mouseX >= center[0] - SLOT_SIZE / 2 && mouseX < center[0] + SLOT_SIZE / 2
			&& mouseY >= center[1] - SLOT_SIZE / 2 && mouseY < center[1] + SLOT_SIZE / 2;
	}

	// ── rendering ──

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// in-game UI: transparent background so the wheel floats over the world
		super.extractBackground(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		selected = slotAt(mouseX, mouseY);

		// bronze hairline from the hub edge to the selected slot
		if (selected >= 0) {
			int[] target = slotCenter(selected);
			double angle = Math.atan2(target[1] - centerY(), target[0] - centerX());
			int sx = centerX() + (int) Math.round(Math.cos(angle) * (HUB_SIZE / 2 + 2));
			int sy = centerY() + (int) Math.round(Math.sin(angle) * (HUB_SIZE / 2 + 2));
			int ex = target[0] - (int) Math.round(Math.cos(angle) * (SLOT_SIZE / 2 + 2));
			int ey = target[1] - (int) Math.round(Math.sin(angle) * (SLOT_SIZE / 2 + 2));
			fillLine(graphics, sx, sy, ex, ey, CarvedTheme.BRONZE_DARK);
		}

		// slots: raised relief tiles, gilded when selected
		for (int i = 0; i < emotes.size(); i++) {
			Emote emote = emotes.get(i);
			int[] center = slotCenter(i);
			int x = center[0] - SLOT_SIZE / 2, y = center[1] - SLOT_SIZE / 2;
			CarvedTheme.raisedTile(graphics, x, y, SLOT_SIZE, SLOT_SIZE, i == selected, true);
			// accent pip: the emote's own colour, set into the tile's corner like the panel studs
			graphics.fill(x + 3, y + 3, x + 5, y + 5, emote.color());
			graphics.fill(x + 3, y + 3, x + 5, y + 4, 0x55FFFFFF); // catch-light on the pip's top edge
			// engraved initial of the emote name
			String initial = emote.name().substring(0, 1).toUpperCase(Locale.ROOT);
			CarvedTheme.engravedText(graphics, font, initial,
				x + SLOT_SIZE / 2 - font.width(initial) / 2, y + SLOT_SIZE / 2 - 4, i == selected ? CarvedTheme.MARBLE : CarvedTheme.BRONZE_LIGHT);
		}

		// hub: carved panel naming the pointed-at emote (or the wheel itself)
		int hx = centerX() - HUB_SIZE / 2, hy = centerY() - HUB_SIZE / 2;
		CarvedTheme.carvedPanel(graphics, hx, hy, HUB_SIZE, HUB_SIZE);
		String label = selected >= 0 ? emotes.get(selected).name().toUpperCase(Locale.ROOT)
			: Component.translatable("imperiumclient.emotes.title").getString().toUpperCase(Locale.ROOT);
		int maxW = HUB_SIZE - 12;
		String fitted = fitCaps(label, maxW);
		int textX = centerX() - CarvedTheme.capsWidth(font, fitted) / 2;
		int textY = centerY() - font.lineHeight / 2;
		CarvedTheme.engravedCaps(graphics, font, fitted, textX, textY, selected >= 0 ? CarvedTheme.BRONZE_LIGHT : CarvedTheme.MUTED);
	}

	/** Truncates with an engraved ellipsis so long emote names cannot spill out of the hub. */
	private String fitCaps(String label, int maxWidth) {
		if (CarvedTheme.capsWidth(font, label) <= maxWidth) {
			return label;
		}
		String prefix = label;
		while (prefix.length() > 1 && CarvedTheme.capsWidth(font, prefix + "...") > maxWidth) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix + "...";
	}

	private static void fillLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
		// axis-aligned or shallow diagonals as a 2px stepped line — pixel-grid, no anti-aliasing
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps == 0) {
			graphics.fill(x1, y1, x1 + 2, y1 + 2, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int x = x1 + (x2 - x1) * i / steps;
			int y = y1 + (y2 - y1) * i / steps;
			graphics.fill(x, y, x + 1, y + 2, color);
		}
	}

	// ── input ──

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = 0; i < emotes.size(); i++) {
				if (inSlot(i, event.x(), event.y())) {
					send(i);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean keyReleased(@NotNull KeyEvent event) {
		if (keybind.matches(event)) {
			send(selected);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public void onClose() {
		// ESC (and any other close path): cancel without sending
		closeWheel();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@VisibleForTesting
	public int selectedSlot() {
		return selected;
	}

	@VisibleForTesting
	public List<Emote> wheelEmotes() {
		return emotes;
	}

	/** Dispatches the given slot (no-op for -1) through the dispatcher seam and closes. */
	private void send(int index) {
		Emote emote = index >= 0 && index < emotes.size() ? emotes.get(index) : null;
		closeWheel();
		if (emote != null) {
			EmoteDispatch.dispatch(emote);
		}
	}

	private void closeWheel() {
		if (minecraft != null) {
			minecraft.gui.setScreen(null);
		}
	}
}
