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

package io.github.axolotlclient.modules.hitboxes;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.modules.AbstractModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hitboxes (wave 5): F3+B-style entity hitboxes on a keybind, outside the debug screen and with
 * configurable colours. 26.3 renders the debug hitboxes as gizmos
 * ({@code EntityHitboxDebugRenderer#showHitboxes}); this module re-emits the same shapes — the
 * interpolated bounding box, the origin point, the eye-height line with its view arrow, the
 * riding seat box and the ender dragon part boxes — from the render-thread gizmo collection the
 * chunk borders already use, so it works without F3 and without vanilla's fixed colours.
 *
 * <p>Vanilla's own rules are kept: invisible entities are skipped, and the camera entity is
 * skipped while in first person (its box would fill the screen). Emitted gizmos are marked
 * always-on-top — the same visibility guarantee the chunk-borders module uses, and the only
 * gizmo pass this frame graph reliably draws outside the debug screen.</p>
 */
public class Hitboxes extends AbstractModule {

	private static final Hitboxes Instance = new Hitboxes();

	public final AxoKeybinding toggleKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.hitboxes.toggle");

	public final BooleanOption enabled = new BooleanOption("enabled", false);
	public final BooleanOption showEyeLine = new BooleanOption("show_eye_line", true);
	public final ColorOption playerColor = new ColorOption("hitboxes.player_color", new Color(0xFFFFFFFF));
	public final ColorOption mobColor = new ColorOption("hitboxes.mob_color", new Color(0xFFFFFFFF));
	public final ColorOption itemColor = new ColorOption("hitboxes.item_color", new Color(0xFF00FFFF));
	public final ColorOption eyeLineColor = new ColorOption("hitboxes.eye_line_color", new Color(0xFFFF0000));

	private final OptionCategory category = OptionCategory.create("hitboxes");

	public static Hitboxes getInstance() {
		return Instance;
	}

	@Override
	public void init() {
		category.add(enabled, showEyeLine, playerColor, mobColor, itemColor, eyeLineColor);
		AxolotlClient.config().general.add(category);

		toggleKey.br$registerOnConsumeClick(() -> {
			enabled.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});
	}

	/**
	 * Emits the hitbox gizmos for every renderable entity. Called at the tail of
	 * {@code LevelRenderer#collectPerFrameRenderThreadGizmos}, while that frame's collector is
	 * active — the same route the vanilla F3+B renderer takes.
	 */
	public void emit() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!enabled.get() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity.isInvisible()) {
				continue;
			}
			if (entity == minecraft.getCameraEntity() && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}
			showHitbox(entity, partialTick);
		}
	}

	private void showHitbox(Entity entity, float partialTick) {
		Vec3 position = entity.position();
		Vec3 renderPosition = entity.getPosition(partialTick);
		Vec3 delta = renderPosition.subtract(position);
		int color = colorOf(entity).toInt();

		onTop(Gizmos.cuboid(entity.getBoundingBox().move(delta), GizmoStyle.stroke(color)));
		onTop(Gizmos.point(renderPosition, color, 2.0F));

		Entity vehicle = entity.getVehicle();
		if (vehicle != null) {
			float half = Math.min(vehicle.getBbWidth(), entity.getBbWidth()) / 2.0F;
			Vec3 seat = vehicle.getPassengerRidingPosition(entity).add(delta);
			onTop(Gizmos.cuboid(new AABB(seat.x - half, seat.y, seat.z - half,
				seat.x + half, seat.y + 0.0625, seat.z + half), GizmoStyle.stroke(0xFFFFFF00)));
		}

		if (entity instanceof LivingEntity && showEyeLine.get()) {
			AABB box = entity.getBoundingBox().move(delta);
			double eyeY = box.minY + entity.getEyeHeight();
			onTop(Gizmos.cuboid(new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ),
				GizmoStyle.stroke(eyeLineColor.get().toInt())));

			Vec3 eyes = renderPosition.add(0.0, entity.getEyeHeight(), 0.0);
			Vec3 look = eyes.add(entity.getViewVector(partialTick).scale(2.0));
			onTop(Gizmos.arrow(eyes, look, 0xFF0000FF));
		}

		if (entity instanceof EnderDragon dragon) {
			for (var part : dragon.getSubEntities()) {
				Vec3 partPosition = part.position();
				Vec3 partRender = part.getPosition(partialTick);
				onTop(Gizmos.cuboid(part.getBoundingBox().move(partRender.subtract(partPosition)),
					GizmoStyle.stroke(ARGB.colorFromFloat(1.0F, 0.25F, 1.0F, 0.0F))));
			}
		}
	}

	private Color colorOf(Entity entity) {
		if (entity instanceof Player) {
			return playerColor.get();
		}
		if (entity instanceof ItemEntity) {
			return itemColor.get();
		}
		return mobColor.get();
	}

	private static void onTop(GizmoProperties properties) {
		if (properties != null) {
			properties.setAlwaysOnTop();
		}
	}
}
