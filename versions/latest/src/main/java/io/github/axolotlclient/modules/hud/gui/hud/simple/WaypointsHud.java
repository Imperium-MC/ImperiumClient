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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.api.SimpleTextInputScreen;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.waypoints.Waypoint;
import io.github.axolotlclient.modules.waypoints.WaypointManager;
import io.github.axolotlclient.modules.waypoints.WaypointsScreen;
import io.github.axolotlclient.util.Util;
import io.github.axolotlclient.util.options.GenericOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Waypoint HUD (Alpine "waypoints" module, parity spec M2): every enabled waypoint of the
 * current dimension renders as one stacked label line — a direction arrow that rotates with
 * the player's view, the waypoint name in its color, and the distance in blocks.
 *
 * <p>The codebase has no world-to-screen projection math for HUD entries, so Alpine's 3D
 * tracers are approximated with compass-style on-screen arrows (CompassHud fallback noted
 * in the parity spec).</p>
 *
 * <p>This entry is also the module's runtime brain, because HudManagerCommon ticks and
 * inits every registered entry: {@link #init()} binds the add-waypoint key,
 * {@link #tick()} edge-triggers the death waypoint, and {@link #postConfigLoad()} re-parses
 * the persisted waypoint list after the config (or profile) loads.</p>
 */
public class WaypointsHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "waypointshud");
	private static final String PLACEHOLDER = "Waypoints";
	private static final int ARROW_BOX = 11;
	private static final int LINE_SPACING = 1;

	public final BooleanOption showDistance = new BooleanOption("waypoints.show_distance", true);
	public final BooleanOption showArrow = new BooleanOption("waypoints.show_arrow", true);
	public final BooleanOption deathWaypoints = new BooleanOption("waypoints.death_points", true);

	private final AxoKeybinding addWaypointKey = AxoKeybinding.create(
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_B), "key.waypoints.add");

	private final Minecraft client = (Minecraft) super.client;
	private boolean wasDeadOrDying;

	public WaypointsHud() {
		super(60, 13, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void init() {
		addWaypointKey.br$registerOnConsumeClick(() -> openAddScreen());
	}

	@Override
	public void postConfigLoad() {
		WaypointManager.getInstance().loadFromOption();
	}

	@Override
	public boolean tickable() {
		return true;
	}

	/**
	 * Death detection: no client-side death event exists in the bridge, so this
	 * edge-triggers on the alive -> dead transition. Runs while the entry is enabled,
	 * matching Alpine's module toggle semantics.
	 */
	@Override
	public void tick() {
		LocalPlayer player = client.player;
		if (player == null) {
			wasDeadOrDying = false;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDeadOrDying && deathWaypoints.get()) {
			WaypointManager.getInstance().setDeathPoint(player.getX(), player.getY(), player.getZ(),
				currentDimension(player));
			Util.addMessageToChatHud(Component.translatable("waypoints.death.set"));
		}
		wasDeadOrDying = dead;
	}

	public static String currentDimension(LocalPlayer player) {
		return player.level().dimension().identifier().toString();
	}

	/**
	 * Adds a waypoint named {@code name} at the player's current position and dimension.
	 * Shared by the keybind input screen and the list screen's add button.
	 */
	public static void addWaypointAtCurrentPosition(String name) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || name == null || name.isBlank()) {
			return;
		}
		Waypoint waypoint = new Waypoint(name.trim(), player.getX(), player.getY(), player.getZ(),
			currentDimension(player), WaypointManager.getInstance().nextColor(), true, false);
		WaypointManager.getInstance().add(waypoint);
		Util.addMessageToChatHud(Component.translatable("waypoints.added", waypoint.name()));
	}

	private void openAddScreen() {
		client.execute(() -> {
			if (client.player == null) {
				return;
			}
			client.gui.setScreen(new SimpleTextInputScreen(
				(Screen) client.gui.screen(),
				Component.translatable("waypoints.add.title"),
				Component.translatable("waypoints.add.name"),
				WaypointsHud::addWaypointAtCurrentPosition));
		});
	}

	private record RenderRow(Waypoint waypoint, double distance, float relativeAngleRad) {}

	private List<RenderRow> collectRows() {
		LocalPlayer player = client.player;
		if (player == null) {
			return List.of();
		}
		List<RenderRow> rows = new ArrayList<>();
		String dimension = currentDimension(player);
		double px = player.getX(), py = player.getY(), pz = player.getZ();
		float yaw = player.getYRot();
		for (Waypoint wp : WaypointManager.getInstance().getForDimension(dimension)) {
			if (!wp.enabled()) {
				continue;
			}
			double dx = wp.x() - px, dy = wp.y() - py, dz = wp.z() - pz;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// MC yaw convention: 0 = +Z; facing vector is (-sin(yaw), cos(yaw)).
			double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
			float relative = (float) ((targetYaw - yaw) % 360);
			if (relative > 180) {
				relative -= 360;
			} else if (relative < -180) {
				relative += 360;
			}
			rows.add(new RenderRow(wp, distance, (float) Math.toRadians(relative)));
		}
		return rows;
	}

	@Override
	public void renderComponent(AxoRenderContext context, float delta) {
		List<RenderRow> rows = collectRows();
		if (rows.isEmpty()) {
			if (getContentWidth() != 0 || getContentHeight() != 0) {
				setContentWidth(0);
				setContentHeight(0);
				onBoundsUpdate();
			}
			return;
		}

		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		int lineHeight = client.font.lineHeight;
		int width = 0;
		int height = 0;

		for (RenderRow row : rows) {
			Waypoint wp = row.waypoint();
			String label = wp.name();
			String distanceText = showDistance.get() ? " " + formatDistance(row.distance()) : "";
			int textWidth = client.font.width(label) + client.font.width(distanceText);
			int rowWidth = (showArrow.get() ? ARROW_BOX : 0) + textWidth;
			width = Math.max(width, rowWidth);

			int cursor = x;
			if (showArrow.get()) {
				drawDirectionArrow(context, x + ARROW_BOX / 2, y + lineHeight / 2, row.relativeAngleRad(), wp.color());
				cursor += ARROW_BOX;
			}
			cursor = context.br$drawString(label, cursor, y, wp.color(), shadow.get());
			if (showDistance.get()) {
				context.br$drawString(distanceText, cursor, y, textColor.get().toInt(), shadow.get());
			}

			y += lineHeight + LINE_SPACING;
			height += lineHeight + LINE_SPACING;
		}

		if (width != getContentWidth() || height != getContentHeight()) {
			setContentWidth(width);
			setContentHeight(height);
			onBoundsUpdate();
		}
	}

	/** Compact block-distance formatting: 42m below 1k, 1.2km above. */
	private static String formatDistance(double distance) {
		if (distance < 1000) {
			return (int) Math.round(distance) + "m";
		}
		return String.format("%.1fk", distance / 1000).replace(',', '.');
	}

	/**
	 * A small pixel arrow pointing "up" (towards the top of the screen = straight ahead),
	 * rotated around its center by the waypoint's relative bearing. Drawn as stacked
	 * 1px rows so it rotates as one shape; positive angles rotate clockwise on screen.
	 */
	private void drawDirectionArrow(AxoRenderContext context, int cx, int cy, float angleRad, int color) {
		context.br$pushMatrix();
		context.br$rotateMatrixAround(angleRad, cx, cy);
		context.br$fillRect(cx, cy - 4, 1, 1, color);       // tip
		context.br$fillRect(cx - 1, cy - 3, 3, 1, color);
		context.br$fillRect(cx - 2, cy - 2, 5, 1, color);   // widest row
		context.br$fillRect(cx, cy - 1, 1, 5, color);       // shaft
		context.br$popMatrix();
	}

	@Override
	public void render(AxoRenderContext context, float delta) {
		if (client.player == null) {
			return;
		}
		super.render(context, delta);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext context, float delta) {
		int x = (int) (float) getContentPos().x();
		int y = (int) (float) getContentPos().y();
		context.br$drawString(PLACEHOLDER, x, y, -1, false);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(showDistance);
		options.add(showArrow);
		options.add(deathWaypoints);
		options.add(new GenericOption("waypoints.open_list", "waypoints.open_list",
			() -> client.execute(() -> client.gui.setScreen(new WaypointsScreen(null)))));
		return options;
	}

	@Override
	public List<Option<?>> getSaveOptions() {
		// The persisted waypoint list rides along in the module config (see WaypointManager).
		List<Option<?>> options = super.getSaveOptions();
		options.add(WaypointManager.getInstance().getDataOption());
		return options;
	}
}
