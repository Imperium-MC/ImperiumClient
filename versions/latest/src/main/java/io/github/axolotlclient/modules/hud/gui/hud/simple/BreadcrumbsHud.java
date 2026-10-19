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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.legions.Breadcrumb;
import io.github.axolotlclient.modules.legions.BreadcrumbManager;
import io.github.axolotlclient.util.Util;
import io.github.axolotlclient.util.options.GenericOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Breadcrumbs HUD (Alpine "factions breadcrumbs" parity, reimplemented for Legions): every
 * crumb of the current dimension renders as one stacked distance label — name, distance in
 * blocks, and age — in the WaypointsHud style (same arrow/distance formatting).
 *
 * <p>This entry is the module's runtime brain, because HudManagerCommon ticks and inits
 * every registered entry: {@link #init()} binds the drop and clear-all keybinds, and
 * {@link #postConfigLoad()} re-parses the persisted crumb list after the config (or
 * profile) loads. Expiry is enforced lazily by {@link BreadcrumbManager#getForDimension}
 * so it keeps working with the HUD hidden.</p>
 */
public class BreadcrumbsHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "breadcrumbshud");
	private static final String PLACEHOLDER = "Breadcrumbs";
	private static final int ARROW_BOX = 11;
	private static final int LINE_SPACING = 1;
	private static final int CRUMB_COLOR = 0xFFFFC78A;

	public final BooleanOption showDistance = new BooleanOption("breadcrumbs.show_distance", true);
	public final BooleanOption showAge = new BooleanOption("breadcrumbs.show_age", true);
	public final BooleanOption showArrow = new BooleanOption("breadcrumbs.show_arrow", true);
	public final EnumOption<BreadcrumbManager.Expiry> expiry =
		new EnumOption<>("breadcrumbs.expire", BreadcrumbManager.Expiry.class, BreadcrumbManager.Expiry.MINUTES_15);

	// Default N: deliberately not J — EnvironmentTogglesGameTest rebinds sneak to J and the
	// double-tap detector fires once per KeyMapping, so a second J-bound mapping would
	// double-fire its tap detection on a single press.
	private final AxoKeybinding dropKey = AxoKeybinding.create(
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_N), "key.legions.breadcrumbs_drop");
	private final AxoKeybinding clearKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.legions.breadcrumbs_clear");

	private final Minecraft client = (Minecraft) super.client;

	public BreadcrumbsHud() {
		super(60, 13, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void init() {
		dropKey.br$registerOnConsumeClick(() -> client.execute(this::dropAtCurrentPosition));
		clearKey.br$registerOnConsumeClick(() -> client.execute(() -> {
			BreadcrumbManager.getInstance().clearAll();
			Util.addMessageToChatHud(Component.translatable("breadcrumbs.cleared"));
		}));
	}

	@Override
	public void postConfigLoad() {
		BreadcrumbManager.getInstance().loadFromOption();
	}

	/** Drops a crumb at the player's feet in the current dimension. */
	private void dropAtCurrentPosition() {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Breadcrumb crumb = BreadcrumbManager.getInstance().drop(
			player.getX(), player.getY(), player.getZ(),
			WaypointsHud.currentDimension(player));
		Util.addMessageToChatHud(Component.translatable("breadcrumbs.dropped", crumb.name()));
	}

	private record RenderRow(Breadcrumb crumb, double distance, float relativeAngleRad) {
	}

	private List<RenderRow> collectRows() {
		LocalPlayer player = client.player;
		if (player == null) {
			return List.of();
		}
		List<RenderRow> rows = new ArrayList<>();
		String dimension = WaypointsHud.currentDimension(player);
		double px = player.getX(), py = player.getY(), pz = player.getZ();
		float yaw = player.getYRot();
		for (Breadcrumb crumb : BreadcrumbManager.getInstance().getForDimension(dimension, expiry.get().maxAgeMs)) {
			double dx = crumb.x() - px, dy = crumb.y() - py, dz = crumb.z() - pz;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// MC yaw convention: 0 = +Z; facing vector is (-sin(yaw), cos(yaw)).
			double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
			float relative = (float) ((targetYaw - yaw) % 360);
			if (relative > 180) {
				relative -= 360;
			} else if (relative < -180) {
				relative += 360;
			}
			rows.add(new RenderRow(crumb, distance, (float) Math.toRadians(relative)));
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
		long now = System.currentTimeMillis();

		for (RenderRow row : rows) {
			Breadcrumb crumb = row.crumb();
			String label = crumb.name();
			StringBuilder detail = new StringBuilder();
			if (showDistance.get()) {
				detail.append(formatDistance(row.distance()));
			}
			if (showAge.get()) {
				if (!detail.isEmpty()) {
					detail.append(" ");
				}
				detail.append(formatAge(now - crumb.droppedAtEpochMs()));
			}
			String detailText = detail.isEmpty() ? "" : " " + detail;
			int textWidth = client.font.width(label) + client.font.width(detailText);
			int rowWidth = (showArrow.get() ? ARROW_BOX : 0) + textWidth;
			width = Math.max(width, rowWidth);

			int cursor = x;
			if (showArrow.get()) {
				drawDirectionArrow(context, x + ARROW_BOX / 2, y + lineHeight / 2, row.relativeAngleRad(), CRUMB_COLOR);
				cursor += ARROW_BOX;
			}
			cursor = context.br$drawString(label, cursor, y, CRUMB_COLOR, shadow.get());
			if (!detailText.isEmpty()) {
				context.br$drawString(detailText, cursor, y, textColor.get().toInt(), shadow.get());
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

	/** Compact block-distance formatting: 42m below 1k, 1.2km above (WaypointsHud style). */
	private static String formatDistance(double distance) {
		if (distance < 1000) {
			return (int) Math.round(distance) + "m";
		}
		return String.format("%.1fk", distance / 1000).replace(',', '.');
	}

	/** "5m ago" style age: seconds below a minute, minutes below an hour, hours above. */
	private static String formatAge(long ageMs) {
		long seconds = Math.max(0, ageMs / 1000);
		if (seconds < 60) {
			return seconds + "s ago";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + "m ago";
		}
		return minutes / 60 + "h ago";
	}

	/** The same rotating on-screen arrow WaypointsHud uses, in the crumb color. */
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
		options.add(hide);
		options.add(showDistance);
		options.add(showAge);
		options.add(showArrow);
		options.add(expiry);
		options.add(new GenericOption("breadcrumbs.clear_all", "breadcrumbs.clear_all",
			() -> client.execute(() -> {
				BreadcrumbManager.getInstance().clearAll();
				Util.addMessageToChatHud(Component.translatable("breadcrumbs.cleared"));
			})));
		return options;
	}

	@Override
	public List<Option<?>> getSaveOptions() {
		// The persisted crumb list rides along in the module config (see BreadcrumbManager).
		List<Option<?>> options = super.getSaveOptions();
		options.add(BreadcrumbManager.getInstance().getDataOption());
		return options;
	}
}
