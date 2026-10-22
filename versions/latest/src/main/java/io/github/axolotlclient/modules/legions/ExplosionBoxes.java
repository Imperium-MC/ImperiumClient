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
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.mixin.TntEntityRendererMixin;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

/**
 * Explosion boxes (Alpine "factions explosion_boxes" parity, reimplemented for Legions):
 * while a primed TNT is within {@code range} of the camera, a translucent cube is drawn
 * around it sized to the vanilla explosion radius (4 blocks), for the whole fuse — the box
 * follows the TNT until it detonates, which is exactly the blast-radius preview cannon
 * crews need when patching walls.
 *
 * <p>Wave 3 completes the Alpine option set: {@code block_center} snaps the box to the
 * block grid (blast predictions land where the chunks actually break), {@code timeout}
 * keeps the box on screen after the detonation so the crater's blast cube is still
 * readable, {@code line_thickness} strokes every box with an outline of that width, and
 * creepers get their own boxes (power 3, 6 when charged) with a separate color.</p>
 *
 * <p>Rendering is two-layered. The live fill rides the entity renderers: a mixin into
 * {@code TntRenderer.submit} (see {@link ExplosionBoxRendererMixin}) and one into
 * {@code LivingEntityRenderer.submit} for creepers (see {@code CreeperBoxRendererMixin})
 * submit translucent world-space quads through {@code submitCustomGeometry} into a
 * dedicated {@link RenderType}, exactly like the block-outline fill. Outlines and the
 * post-detonation linger instead ride the gizmo path ({@code LevelRenderEvents.
 * BEFORE_GIZMOS}, same as {@link CannonTrajectory}): the renderer hooks record every box
 * they draw into {@link #recentBoxes}, and the gizmo pass strokes fresh entries, then
 * keeps fading entries alive for {@code timeout} milliseconds after their entity stopped
 * rendering — which is exactly what "the TNT just detonated" looks like to the tracker.</p>
 */
@NullMarked
public class ExplosionBoxes extends AbstractModule {

	private static final ExplosionBoxes instance = new ExplosionBoxes();

	/** Half-extent of the preview cube in blocks; vanilla TNT power is 4. */
	public final IntegerOption radius = new IntegerOption("explosionboxes.radius", 4, 1, 8);
	/** Only draw boxes for TNT within this camera distance. */
	public final IntegerOption range = new IntegerOption("explosionboxes.range", 64, 8, 256);
	public final ColorOption color = new ColorOption("explosionboxes.color", new Color(255, 85, 85, 77));
	/** Box opacity in percent; overrides the color's alpha at draw time (5-100). */
	public final IntegerOption opacity = new IntegerOption("explosionboxes.opacity", 30, 5, 100);
	public final BooleanOption enabled = new BooleanOption("enabled", true);
	// wave 3 (Alpine factions/explosion_boxes completion):
	/** Snap box centers to the block grid so the cube aligns with chunk geometry. */
	public final BooleanOption blockCenter = new BooleanOption("explosionboxes.block_center", false);
	/** How long a box stays after its entity stopped rendering (detonation), in ms. */
	public final IntegerOption timeout = new IntegerOption("explosionboxes.timeout", 1000, 0, 10_000);
	/** Width of the outline drawn around every box (gizmo stroke width). */
	public final FloatOption lineThickness = new FloatOption("explosionboxes.line_thickness", 2.0F, 0.5F, 8.0F);
	/** Also draw blast boxes around creepers (power 3, 6 when charged). */
	public final BooleanOption creepers = new BooleanOption("explosionboxes.creepers", true);
	public final ColorOption creeperColor = new ColorOption("explosionboxes.creeper_color",
		new Color(85, 255, 85, 77));

	/** A drawn box: world-space center, half-extent, ARGB fill color and the last frame seen. */
	public record Box(double x, double y, double z, float radius, int fillArgb, long stamp) {
	}

	/**
	 * Boxes seen recently, newest information first. Render-thread confined in production
	 * (both the entity submit hooks and the gizmo pass run there); copy-on-write so the
	 * gametest can poke it from the test thread without races.
	 */
	private final List<Box> recentBoxes = new CopyOnWriteArrayList<>();

	/** Cap so a cannon volley cannot grow the list unbounded between prunes. */
	private static final int MAX_TRACKED = 64;
	/** A box whose entity still renders refreshes its stamp every frame; this is that window. */
	public static final long LIVE_WINDOW_MS = 250;

	private final OptionCategory category = OptionCategory.create("explosionboxes");

	/** Boxes submitted by the most recent gizmo pass (gametest evidence). */
	private int lastDrawnBoxes;

	/**
	 * Same construction as {@code DrawUtil.QUADS} (the block-outline fill): GUI snippet
	 * pipeline with depth testing and view-offset layering, submitted as world geometry.
	 */
	private static final RenderType QUADS = RenderType.create("explosion_box_quads",
		RenderSetup.builder(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "explosion_box_quads"))
				.withDepthStencilState(DepthStencilState.DEFAULT).build())
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup());

	private ExplosionBoxes() {
	}

	public static ExplosionBoxes getInstance() {
		return instance;
	}

	@Override
	public void init() {
		category.add(enabled, radius, range, color, opacity, blockCenter, timeout, lineThickness, creepers,
			creeperColor);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!enabled.get()) {
				lastDrawnBoxes = 0;
				return;
			}
			lastDrawnBoxes = submitTrackedBoxes();
		});
	}

	/**
	 * Called by {@link ExplosionBoxRendererMixin} at the tail of {@code TntRenderer.submit}
	 * — the pose stack sits at the TNT's camera-relative position, and the renderer only
	 * runs while the fuse burns, which is precisely the box's lifetime.
	 */
	public void submitBox(TntRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		submitFill(state, poseStack, collector, radius.get(), colorWithOpacity(color.get(), opacity.get()));
	}

	/**
	 * Called by {@code CreeperBoxRendererMixin} at the head of
	 * {@code LivingEntityRenderer.submit} for creeper states: same world-space fill in the
	 * creeper color, sized to the creeper's actual blast power (3, or 6 when charged).
	 */
	public void submitCreeperBox(CreeperRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (!creepers.get()) {
			return;
		}
		float power = state.isPowered ? 6.0F : 3.0F;
		submitFill(state, poseStack, collector, power, colorWithOpacity(creeperColor.get(), opacity.get()));
	}

	private void submitFill(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
							float halfExtent, int argb) {
		if (!enabled.get()) {
			return;
		}
		// The dispatcher-translated pose is camera-relative, so its translation IS the
		// camera-to-entity distance — no world queries needed on the render path.
		var matrix = poseStack.last().pose();
		double dx = matrix.m30(), dy = matrix.m31(), dz = matrix.m32();
		if (dx * dx + dy * dy + dz * dz > (double) range.get() * range.get()) {
			return;
		}

		// Entity origin is the feet position; the blast cube is centered half a block up.
		double cx = snap(blockCenter.get(), state.x);
		double cy = snap(blockCenter.get(), state.y + 0.5);
		double cz = snap(blockCenter.get(), state.z);

		poseStack.pushPose();
		poseStack.translate(0, 0.5, 0);
		collector.submitCustomGeometry(poseStack, QUADS,
			(pose, consumer) -> fillBoxQuads(consumer, pose, -halfExtent, halfExtent, argb));
		poseStack.popPose();

		track(new Box(cx, cy, cz, halfExtent, argb, System.currentTimeMillis()));
	}

	/** RGB from the color option, alpha from the opacity option (both static for tests). */
	static int colorWithOpacity(Color optionColor, int opacityPercent) {
		int rgb = optionColor.toInt() & 0x00FFFFFF;
		int alpha = Math.round(opacityPercent * 255f / 100f);
		return (alpha << 24) | rgb;
	}

	/** Block-grid snapping: {@code block_center} locks the cube to chunk geometry. */
	public static double snap(boolean blockCenter, double value) {
		return blockCenter ? Math.floor(value) + 0.5 : value;
	}

	/**
	 * Records a drawn box, refreshing a co-located entry instead of piling up one per
	 * frame: while the fuse burns the same box is re-submitted every frame, and the
	 * refreshed stamp is exactly what marks it "live" in {@link #submitTrackedBoxes}.
	 */
	public void track(Box box) {
		long now = box.stamp();
		for (int i = 0; i < recentBoxes.size(); i++) {
			Box seen = recentBoxes.get(i);
			double dx = seen.x() - box.x(), dy = seen.y() - box.y(), dz = seen.z() - box.z();
			if (dx * dx + dy * dy + dz * dz < 0.25 && seen.radius() == box.radius()) {
				recentBoxes.set(i, box);
				return;
			}
		}
		recentBoxes.add(box);
		while (recentBoxes.size() > MAX_TRACKED) {
			recentBoxes.remove(0);
		}
		pruneExpired(now);
	}

	/** Drops entries older than the linger window; returns how many were dropped. */
	public int pruneExpired(long now) {
		int before = recentBoxes.size();
		recentBoxes.removeIf(box -> isExpired(now - box.stamp(), timeout.get()));
		return before - recentBoxes.size();
	}

	/** A box is "live" while its entity keeps refreshing it every frame. */
	public static boolean isLive(long ageMs) {
		return ageMs < LIVE_WINDOW_MS;
	}

	/** A box is expired once it outlives the configured post-detonation linger. */
	public static boolean isExpired(long ageMs, int timeoutMs) {
		return ageMs > timeoutMs;
	}

	/**
	 * Fade factor for a lingering box: 1 right after the entity vanished, reaching 0 at
	 * the end of the timeout window.
	 */
	public static float lingerFade(long ageMs, int timeoutMs) {
		if (timeoutMs <= 0) {
			return 0.0F;
		}
		float fade = 1.0F - (float) ageMs / timeoutMs;
		return Math.max(0.0F, Math.min(1.0F, fade));
	}

	/**
	 * The gizmo pass: strokes every live box with {@code line_thickness}, and keeps
	 * lingering (detonated) boxes filled while they fade out over the timeout window.
	 * Returns the number of boxes submitted (gametest evidence).
	 */
	int submitTrackedBoxes() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		pruneExpired(now);
		float width = lineThickness.get();
		int timeoutMs = timeout.get();
		int drawn = 0;
		try (var ignored = client.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			for (Box box : recentBoxes) {
				long age = now - box.stamp();
				boolean live = isLive(age);
				if (!live && isExpired(age, timeoutMs)) {
					continue;
				}
				float r = box.radius();
				AABB bounds = new AABB(box.x() - r, box.y() - r, box.z() - r,
					box.x() + r, box.y() + r, box.z() + r);
				// The outline is the load-bearing part for lingering boxes; make it
				// clearly visible regardless of the fill's translucency.
				int stroke = (0xE0 << 24) | (box.fillArgb() & 0x00FFFFFF);
				if (live) {
					Gizmos.cuboid(bounds, GizmoStyle.stroke(stroke, width));
				} else {
					float fade = lingerFade(age, timeoutMs);
					Gizmos.cuboid(bounds, GizmoStyle.strokeAndFill(stroke, width, multipliedArgb(box.fillArgb(), fade)));
				}
				drawn++;
			}
		}
		return drawn;
	}

	/** Scales only the alpha channel of an ARGB color by {@code factor}. */
	static int multipliedArgb(int argb, float factor) {
		int alpha = Math.round(ARGB.alpha(argb) * factor);
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** Number of boxes submitted by the most recent gizmo pass (gametest evidence). */
	public int getLastDrawnBoxes() {
		return lastDrawnBoxes;
	}

	/** Test/inspection access to the tracked boxes. */
	public List<Box> getRecentBoxes() {
		return new ArrayList<>(recentBoxes);
	}

	/** Clears tracked boxes (gametest isolation). */
	public void clearTracked() {
		recentBoxes.clear();
	}

	/**
	 * The six cube faces as quads, vertex order matching {@code DrawUtil.fillOutlineQuads}
	 * (the block-outline fill): two quads per axis pair, consistent winding per face.
	 */
	private static void fillBoxQuads(VertexConsumer consumer, PoseStack.Pose matrix, float min, float max, int color) {
		// -Z / +Z faces
		consumer.addVertex(matrix, max, min, min).setColor(color);
		consumer.addVertex(matrix, min, min, min).setColor(color);
		consumer.addVertex(matrix, min, max, min).setColor(color);
		consumer.addVertex(matrix, max, max, min).setColor(color);

		consumer.addVertex(matrix, min, min, max).setColor(color);
		consumer.addVertex(matrix, max, min, max).setColor(color);
		consumer.addVertex(matrix, max, max, max).setColor(color);
		consumer.addVertex(matrix, min, max, max).setColor(color);

		// -X / +X faces
		consumer.addVertex(matrix, min, max, max).setColor(color);
		consumer.addVertex(matrix, min, max, min).setColor(color);
		consumer.addVertex(matrix, min, min, min).setColor(color);
		consumer.addVertex(matrix, min, min, max).setColor(color);

		consumer.addVertex(matrix, max, max, min).setColor(color);
		consumer.addVertex(matrix, max, max, max).setColor(color);
		consumer.addVertex(matrix, max, min, max).setColor(color);
		consumer.addVertex(matrix, max, min, min).setColor(color);

		// bottom / top faces
		consumer.addVertex(matrix, max, min, min).setColor(color);
		consumer.addVertex(matrix, max, min, max).setColor(color);
		consumer.addVertex(matrix, min, min, max).setColor(color);
		consumer.addVertex(matrix, min, min, min).setColor(color);

		consumer.addVertex(matrix, max, max, max).setColor(color);
		consumer.addVertex(matrix, max, max, min).setColor(color);
		consumer.addVertex(matrix, min, max, min).setColor(color);
		consumer.addVertex(matrix, min, max, max).setColor(color);
	}
}
