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

package io.github.axolotlclient.modules.legions;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.modules.AbstractModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * TNT trajectory prediction (Alpine "factions/cannon_view + cannon_playback" parity,
 * reimplemented for Legions): while the player holds TNT, the predicted flight arc of a TNT
 * launched along the look direction is drawn in the world, together with the blast box at the
 * predicted landing site; primed TNT already in flight within {@link #range} gets the same
 * treatment from its live position and velocity (the playback half — where this shot is
 * going to come down).
 *
 * <p>Physics is {@link TntTrajectory}, a pure integrator with the exact 26.3 {@code PrimedTnt}
 * constants (gravity 0.04 before the move, air drag 0.98 after it — javap of the mapped jar).
 * The launch speed is an option because ImperiumMC cannons impart their own charge-dependent
 * velocity; 1.5 blocks/tick matches a strong hand throw.</p>
 *
 * <p>Rendering rides the same world-space gizmo path as the WE-CUI selection and the chunk
 * borders: {@code LevelRenderEvents.BEFORE_GIZMOS} opens the frame's render-thread gizmo
 * collection window and {@code Gizmos.line}/{@code Gizmos.cuboid} submit camera-independent
 * world-space geometry, drawn with the game's line pipelines (translucent lines are
 * depth-tested but never occlude). The landing box uses {@link ExplosionBoxes}' radius option
 * so the prediction and the live box around a burning TNT read as one visual system.</p>
 */
@NullMarked
public class CannonTrajectory extends AbstractModule {

	private static final CannonTrajectory instance = new CannonTrajectory();

	/** Launch speed along the look vector, in blocks per tick. */
	public final FloatOption launchPower = new FloatOption("cannontrajectory.power", 1.5F, 0.1F, 5.0F);
	/** Ticks to simulate; the vanilla fuse burns 80, so the default covers a full flight. */
	public final IntegerOption maxTicks = new IntegerOption("cannontrajectory.max_ticks",
		TntTrajectory.DEFAULT_MAX_TICKS, 20, 200);
	/** Draw the predicted arc while holding TNT (Alpine's cannon_view trigger). */
	public final BooleanOption enabled = new BooleanOption("enabled", true);
	/** Also predict the remaining flight of primed TNT entities within range. */
	public final BooleanOption predictLiveTnt = new BooleanOption("cannontrajectory.predict_live", true);
	/** Camera distance up to which live primed TNT is predicted. */
	public final IntegerOption range = new IntegerOption("cannontrajectory.range", 64, 8, 256);
	/** Draw the blast box at the predicted landing site. */
	public final BooleanOption showLandingBox = new BooleanOption("cannontrajectory.landing_box", true);
	public final ColorOption arcColor = new ColorOption("cannontrajectory.arc_color",
		new Color(255, 238, 170, 220));
	public final ColorOption landingColor = new ColorOption("cannontrajectory.landing_color",
		new Color(255, 85, 85, 200));
	public final FloatOption lineWidth = new FloatOption("cannontrajectory.line_width", 2.0F, 0.5F, 8.0F);
	// factions/cannon_view (wave 3): a dedicated toggle keybind, Alpine-style — flip the
	// prediction on and off without opening the config (AutoFish toggle-key idiom).
	public final AxoKeybinding toggleKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.cannon_view.toggle");

	private final OptionCategory category = OptionCategory.create("cannontrajectory");

	/** Last drawn waypoint count, for the gametest's render assertions. */
	private int lastDrawnSegments;

	private CannonTrajectory() {
	}

	public static CannonTrajectory getInstance() {
		return instance;
	}

	@Override
	public void init() {
		category.add(enabled, launchPower, maxTicks, predictLiveTnt, range, showLandingBox, arcColor,
			landingColor, lineWidth);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		toggleKey.br$registerOnConsumeClick(() -> {
			enabled.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!enabled.get()) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			ClientLevel level = client.level;
			LocalPlayer player = client.player;
			if (level == null || player == null) {
				return;
			}
			List<Flight> flights = new ArrayList<>();
			if (holdingTnt(player)) {
				Vec3 eye = player.getEyePosition();
				Vec3 look = player.getLookAngle();
				flights.add(new Flight(eye.x, eye.y, eye.z,
					look.x * launchPower.get(), look.y * launchPower.get(), look.z * launchPower.get(),
					arcColor.get().toInt()));
			}
			if (predictLiveTnt.get()) {
				double maxDistSq = (double) range.get() * range.get();
				Vec3 camera = client.gameRenderer.mainCamera().position();
				for (Entity entity : level.entitiesForRendering()) {
					if (!(entity instanceof PrimedTnt tnt)) {
						continue;
					}
					if (entity.distanceToSqr(camera) > maxDistSq) {
						continue;
					}
					Vec3 pos = entity.position();
					Vec3 delta = entity.getDeltaMovement();
					// PrimedTnt's entity origin sits at its feet and its physics tick from
					// there, so the prediction integrates the origin, like the entity does.
					flights.add(new Flight(pos.x, pos.y, pos.z,
						delta.x, delta.y, delta.z, arcColor.get().toInt()));
				}
			}
			if (flights.isEmpty()) {
				lastDrawnSegments = 0;
				return;
			}
			var solid = worldSolidCheck(level);
			int segments = 0;
			try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
				for (Flight flight : flights) {
					segments += drawFlight(flight, solid);
				}
			}
			lastDrawnSegments = segments;
		});
	}

	/** Number of gizmo segments submitted by the most recent frame (gametest evidence). */
	public int getLastDrawnSegments() {
		return lastDrawnSegments;
	}

	static boolean holdingTnt(LocalPlayer player) {
		return player.getMainHandItem().is(Items.TNT) || player.getOffhandItem().is(Items.TNT);
	}

	/**
	 * The world's answer to {@link TntTrajectory.SolidCheck}: the block containing the point
	 * must render solid. Unloaded chunks answer "not solid" (the flight simply continues).
	 */
	static TntTrajectory.SolidCheck worldSolidCheck(ClientLevel level) {
		return (x, y, z) -> level.getBlockState(BlockPos.containing(x, y, z)).isSolid();
	}

	/** One predicted flight: launch state plus the color its arc is drawn with. */
	record Flight(double x, double y, double z, double vx, double vy, double vz, int color) {
	}

	/**
	 * Integrates and submits one flight: the arc as connected gizmo lines (every sampled point,
	 * one line per pair) and, on collision, the landing blast box. Returns the segment count.
	 */
	private int drawFlight(Flight flight, TntTrajectory.SolidCheck solid) {
		TntTrajectory.Result result = TntTrajectory.integrate(flight.x(), flight.y(), flight.z(),
			flight.vx(), flight.vy(), flight.vz(), maxTicks.get(), solid);
		List<TntTrajectory.Point> points = result.points();
		int segments = 0;
		int color = flight.color();
		float width = lineWidth.get();
		for (int i = 1; i < points.size(); i++) {
			TntTrajectory.Point from = points.get(i - 1);
			TntTrajectory.Point to = points.get(i);
			Gizmos.line(new Vec3(from.x(), from.y(), from.z()), new Vec3(to.x(), to.y(), to.z()), color, width);
			segments++;
		}
		if (result.collided() && showLandingBox.get()) {
			// Visual merge with ExplosionBoxes: same radius its live boxes use, so the
			// prediction frame and the burning-TNT box line up exactly.
			float radius = ExplosionBoxes.getInstance().radius.get();
			TntTrajectory.Point landing = result.landing();
			double cx = landing.x(), cy = landing.y() + 0.5, cz = landing.z();
			Gizmos.cuboid(new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius),
				GizmoStyle.stroke(landingColor.get().toInt(), width));
			segments++;
		}
		return segments;
	}
}
