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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.mixin.TntEntityRendererMixin;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Explosion boxes (Alpine "factions explosion_boxes" parity, reimplemented for Legions):
 * while a primed TNT is within {@code range} of the camera, a translucent cube is drawn
 * around it sized to the vanilla explosion radius (4 blocks), for the whole fuse — the box
 * follows the TNT until it detonates, which is exactly the blast-radius preview cannon
 * crews need when patching walls.
 *
 * <p>Rendering uses the same world-space approach as the block-outline fill
 * ({@code DrawUtil.drawOutlines}): camera-relative vertices submitted through a
 * {@code submitCustomGeometry} call into a dedicated quads {@link RenderType} — the pose
 * machinery copies the matrix at submit time, so the box lands at the entity's world
 * position. The hook is a separate mixin ({@link ExplosionBoxRendererMixin}, registered
 * alongside {@link TntEntityRendererMixin}); hooking the TNT renderer means the box lives
 * and dies with the fuse automatically — no entity tracking, no timers.</p>
 *
 * <p>TNT trajectory preview (Alpine's cannon_playback / cannon_view) lives in
 * {@link CannonTrajectory} + {@link TntTrajectory}; its predicted landing box reads this
 * module's radius option so both visual systems agree on the blast size.</p>
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

	private final OptionCategory category = OptionCategory.create("explosionboxes");

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
		category.add(enabled, radius, range, color, opacity);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
	}

	/**
	 * Called by {@link ExplosionBoxRendererMixin} at the tail of {@code TntRenderer.submit}
	 * — the pose stack sits at the TNT's camera-relative position, and the renderer only
	 * runs while the fuse burns, which is precisely the box's lifetime.
	 */
	public void submitBox(PoseStack poseStack, SubmitNodeCollector collector) {
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

		float r = radius.get();
		int argb = colorWithOpacity();
		poseStack.pushPose();
		poseStack.translate(0, 0.5, 0); // entity origin is the TNT's foot position
		collector.submitCustomGeometry(poseStack, QUADS, (pose, consumer) -> fillBoxQuads(consumer, pose, -r, r, argb));
		poseStack.popPose();
	}

	/** RGB from the color option, alpha from the opacity option. */
	private int colorWithOpacity() {
		int rgb = color.get().toInt() & 0x00FFFFFF;
		int alpha = Math.round(opacity.get() * 255f / 100f);
		return (alpha << 24) | rgb;
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
