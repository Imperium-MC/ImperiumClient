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

package io.github.axolotlclient.modules.legions

import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.modules.AbstractModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.CreeperRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.TntRenderState
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Explosion boxes (Alpine "factions explosion_boxes" parity, reimplemented for Legions):
 * while a primed TNT is within [range] of the camera, a translucent cube is drawn
 * around it sized to the vanilla explosion radius (4 blocks), for the whole fuse — the box
 * follows the TNT until it detonates, which is exactly the blast-radius preview cannon
 * crews need when patching walls.
 *
 * Wave 3 completes the Alpine option set: `block_center` snaps the box to the
 * block grid (blast predictions land where the chunks actually break), `timeout`
 * keeps the box on screen after the detonation so the crater's blast cube is still
 * readable, `line_thickness` strokes every box with an outline of that width, and
 * creepers get their own boxes (power 3, 6 when charged) with a separate color.
 *
 * Rendering is two-layered. The live fill rides the entity renderers: a mixin into
 * `TntRenderer.submit` (see ExplosionBoxRendererMixin) and one into
 * `LivingEntityRenderer.submit` for creepers (see CreeperBoxRendererMixin)
 * submit translucent world-space quads through `submitCustomGeometry` into a
 * dedicated [RenderType], exactly like the block-outline fill. Outlines and the
 * post-detonation linger instead ride the gizmo path (LevelRenderEvents.
 * BEFORE_GIZMOS, same as CannonTrajectory): the renderer hooks record every box
 * they draw into the tracked list, and the gizmo pass strokes fresh entries, then
 * keeps fading entries alive for `timeout` milliseconds after their entity stopped
 * rendering — which is exactly what "the TNT just detonated" looks like to the tracker.
 */
class ExplosionBoxes private constructor() : AbstractModule() {

    /** Half-extent of the preview cube in blocks; vanilla TNT power is 4. */
    @JvmField
    val radius = IntegerOption("explosionboxes.radius", 4, 1, 8)

    /** Only draw boxes for TNT within this camera distance. */
    @JvmField
    val range = IntegerOption("explosionboxes.range", 64, 8, 256)

    @JvmField
    val color = ColorOption("explosionboxes.color", Color(255, 85, 85, 77))

    /** Box opacity in percent; overrides the color's alpha at draw time (5-100). */
    @JvmField
    val opacity = IntegerOption("explosionboxes.opacity", 30, 5, 100)

    @JvmField
    val enabled = BooleanOption("enabled", true)

    // wave 3 (Alpine factions/explosion_boxes completion):
    /** Snap box centers to the block grid so the cube aligns with chunk geometry. */
    @JvmField
    val blockCenter = BooleanOption("explosionboxes.block_center", false)

    /** How long a box stays after its entity stopped rendering (detonation), in ms. */
    @JvmField
    val timeout = IntegerOption("explosionboxes.timeout", 1000, 0, 10_000)

    /** Width of the outline drawn around every box (gizmo stroke width). */
    @JvmField
    val lineThickness = FloatOption("explosionboxes.line_thickness", 2.0f, 0.5f, 8.0f)

    /** Also draw blast boxes around creepers (power 3, 6 when charged). */
    @JvmField
    val creepers = BooleanOption("explosionboxes.creepers", true)

    @JvmField
    val creeperColor = ColorOption("explosionboxes.creeper_color", Color(85, 255, 85, 77))

    /**
     * A drawn box: world-space center, half-extent, ARGB fill color and the last frame seen.
     */
    @JvmRecord
    data class Box(val x: Double, val y: Double, val z: Double, val radius: Float, val fillArgb: Int, val stamp: Long)

    /**
     * Boxes seen recently, newest information first. Render-thread confined in production
     * (both the entity submit hooks and the gizmo pass run there); copy-on-write so the
     * gametest can poke it from the test thread without races.
     */
    private val recentBoxes: MutableList<Box> = CopyOnWriteArrayList()

    private val category = OptionCategory.create("explosionboxes")

    /** Boxes submitted by the most recent gizmo pass (gametest evidence). */
    private var lastDrawnBoxes = 0

    override fun init() {
        category.add(enabled, radius, range, color, opacity, blockCenter, timeout, lineThickness, creepers, creeperColor)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)

        LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos {
            if (!enabled.get()) {
                lastDrawnBoxes = 0
                return@BeforeGizmos
            }
            lastDrawnBoxes = submitTrackedBoxes()
        })
    }

    /**
     * Called by ExplosionBoxRendererMixin at the tail of `TntRenderer.submit`
     * — the pose stack sits at the TNT's camera-relative position, and the renderer only
     * runs while the fuse burns, which is precisely the box's lifetime.
     */
    fun submitBox(state: TntRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        submitFill(state, poseStack, collector, radius.get().toFloat(), colorWithOpacity(color.get(), opacity.get()))
    }

    /**
     * Called by CreeperBoxRendererMixin at the head of
     * `LivingEntityRenderer.submit` for creeper states: same world-space fill in the
     * creeper color, sized to the creeper's actual blast power (3, or 6 when charged).
     */
    fun submitCreeperBox(state: CreeperRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        if (!creepers.get()) {
            return
        }
        val power = if (state.isPowered) 6.0f else 3.0f
        submitFill(state, poseStack, collector, power, colorWithOpacity(creeperColor.get(), opacity.get()))
    }

    private fun submitFill(
        state: EntityRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, halfExtent: Float, argb: Int
    ) {
        if (!enabled.get()) {
            return
        }
        // The dispatcher-translated pose is camera-relative, so its translation IS the
        // camera-to-entity distance — no world queries needed on the render path.
        val matrix = poseStack.last().pose()
        val dx = matrix.m30().toDouble()
        val dy = matrix.m31().toDouble()
        val dz = matrix.m32().toDouble()
        if (dx * dx + dy * dy + dz * dz > range.get().toDouble() * range.get()) {
            return
        }

        // Entity origin is the feet position; the blast cube is centered half a block up.
        val cx = snap(blockCenter.get(), state.x)
        val cy = snap(blockCenter.get(), state.y + 0.5)
        val cz = snap(blockCenter.get(), state.z)

        poseStack.pushPose()
        poseStack.translate(0.0, 0.5, 0.0)
        collector.submitCustomGeometry(
            poseStack, QUADS
        ) { pose: PoseStack.Pose, consumer: VertexConsumer -> fillBoxQuads(consumer, pose, -halfExtent, halfExtent, argb) }
        poseStack.popPose()

        track(Box(cx, cy, cz, halfExtent, argb, System.currentTimeMillis()))
    }

    /**
     * Records a drawn box, refreshing a co-located entry instead of piling up one per
     * frame: while the fuse burns the same box is re-submitted every frame, and the
     * refreshed stamp is exactly what marks it "live" in submitTrackedBoxes.
     */
    fun track(box: Box) {
        val now = box.stamp
        for (i in recentBoxes.indices) {
            val seen = recentBoxes[i]
            val dx = seen.x - box.x
            val dy = seen.y - box.y
            val dz = seen.z - box.z
            if (dx * dx + dy * dy + dz * dz < 0.25 && seen.radius == box.radius) {
                recentBoxes[i] = box
                return
            }
        }
        recentBoxes.add(box)
        while (recentBoxes.size > MAX_TRACKED) {
            recentBoxes.removeAt(0)
        }
        pruneExpired(now)
    }

    /** Drops entries older than the linger window; returns how many were dropped. */
    fun pruneExpired(now: Long): Int {
        val before = recentBoxes.size
        recentBoxes.removeIf { box -> isExpired(now - box.stamp, timeout.get()) }
        return before - recentBoxes.size
    }

    /**
     * The gizmo pass: strokes every live box with `line_thickness`, and keeps
     * lingering (detonated) boxes filled while they fade out over the timeout window.
     * Returns the number of boxes submitted (gametest evidence).
     */
    fun submitTrackedBoxes(): Int {
        val client = Minecraft.getInstance()
        if (client.level == null) {
            return 0
        }
        val now = System.currentTimeMillis()
        pruneExpired(now)
        val width = lineThickness.get()
        val timeoutMs = timeout.get()
        var drawn = 0
        client.levelRenderer.collectPerFrameRenderThreadGizmos().use {
            for (box in recentBoxes) {
                val age = now - box.stamp
                val live = isLive(age)
                if (!live && isExpired(age, timeoutMs)) {
                    continue
                }
                val r = box.radius.toDouble()
                val bounds = AABB(box.x - r, box.y - r, box.z - r, box.x + r, box.y + r, box.z + r)
                // The outline is the load-bearing part for lingering boxes; make it
                // clearly visible regardless of the fill's translucency.
                val stroke = (0xE0 shl 24) or (box.fillArgb and 0x00FFFFFF)
                if (live) {
                    Gizmos.cuboid(bounds, GizmoStyle.stroke(stroke, width))
                } else {
                    val fade = lingerFade(age, timeoutMs)
                    Gizmos.cuboid(bounds, GizmoStyle.strokeAndFill(stroke, width, multipliedArgb(box.fillArgb, fade)))
                }
                drawn++
            }
        }
        return drawn
    }

    /** Number of boxes submitted by the most recent gizmo pass (gametest evidence). */
    fun getLastDrawnBoxes(): Int = lastDrawnBoxes

    /** Test/inspection access to the tracked boxes. */
    fun getRecentBoxes(): List<Box> = ArrayList(recentBoxes)

    /** Clears tracked boxes (gametest isolation). */
    fun clearTracked() {
        recentBoxes.clear()
    }

    companion object {
        private val instance = ExplosionBoxes()

        /** Cap so a cannon volley cannot grow the list unbounded between prunes. */
        private const val MAX_TRACKED = 64

        /** A box whose entity still renders refreshes its stamp every frame; this is that window. */
        const val LIVE_WINDOW_MS = 250L

        /**
         * Same construction as `DrawUtil.QUADS` (the block-outline fill): GUI snippet
         * pipeline with depth testing and view-offset layering, submitted as world geometry.
         */
        private val QUADS = RenderType.create(
            "explosion_box_quads",
            RenderSetup.builder(
                RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "explosion_box_quads"))
                    .withDepthStencilState(DepthStencilState.DEFAULT).build()
            )
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .createRenderSetup()
        )

        @JvmStatic
        fun getInstance(): ExplosionBoxes = instance

        /** RGB from the color option, alpha from the opacity option (both static for tests). */
        @JvmStatic
        fun colorWithOpacity(optionColor: Color, opacityPercent: Int): Int {
            val rgb = optionColor.toInt() and 0x00FFFFFF
            val alpha = (opacityPercent * 255f / 100f).roundToInt()
            return (alpha shl 24) or rgb
        }

        /** Block-grid snapping: `block_center` locks the cube to chunk geometry. */
        @JvmStatic
        fun snap(blockCenter: Boolean, value: Double): Double =
            if (blockCenter) kotlin.math.floor(value) + 0.5 else value

        /** A box is "live" while its entity keeps refreshing it every frame. */
        @JvmStatic
        fun isLive(ageMs: Long): Boolean = ageMs < LIVE_WINDOW_MS

        /** A box is expired once it outlives the configured post-detonation linger. */
        @JvmStatic
        fun isExpired(ageMs: Long, timeoutMs: Int): Boolean = ageMs > timeoutMs

        /**
         * Fade factor for a lingering box: 1 right after the entity vanished, reaching 0 at
         * the end of the timeout window.
         */
        @JvmStatic
        fun lingerFade(ageMs: Long, timeoutMs: Int): Float {
            if (timeoutMs <= 0) {
                return 0.0f
            }
            val fade = 1.0f - ageMs.toFloat() / timeoutMs
            return max(0.0f, min(1.0f, fade))
        }

        /** Scales only the alpha channel of an ARGB color by `factor`. */
        @JvmStatic
        fun multipliedArgb(argb: Int, factor: Float): Int {
            val alpha = (ARGB.alpha(argb) * factor).roundToInt()
            return (alpha shl 24) or (argb and 0x00FFFFFF)
        }

        /**
         * The six cube faces as quads, vertex order matching `DrawUtil.fillOutlineQuads`
         * (the block-outline fill): two quads per axis pair, consistent winding per face.
         */
        private fun fillBoxQuads(consumer: VertexConsumer, matrix: PoseStack.Pose, min: Float, max: Float, color: Int) {
            // -Z / +Z faces
            consumer.addVertex(matrix, max, min, min).setColor(color)
            consumer.addVertex(matrix, min, min, min).setColor(color)
            consumer.addVertex(matrix, min, max, min).setColor(color)
            consumer.addVertex(matrix, max, max, min).setColor(color)

            consumer.addVertex(matrix, min, min, max).setColor(color)
            consumer.addVertex(matrix, max, min, max).setColor(color)
            consumer.addVertex(matrix, max, max, max).setColor(color)
            consumer.addVertex(matrix, min, max, max).setColor(color)

            // -X / +X faces
            consumer.addVertex(matrix, min, max, max).setColor(color)
            consumer.addVertex(matrix, min, max, min).setColor(color)
            consumer.addVertex(matrix, min, min, min).setColor(color)
            consumer.addVertex(matrix, min, min, max).setColor(color)

            consumer.addVertex(matrix, max, max, min).setColor(color)
            consumer.addVertex(matrix, max, max, max).setColor(color)
            consumer.addVertex(matrix, max, min, max).setColor(color)
            consumer.addVertex(matrix, max, min, min).setColor(color)

            // bottom / top faces
            consumer.addVertex(matrix, max, min, min).setColor(color)
            consumer.addVertex(matrix, max, min, max).setColor(color)
            consumer.addVertex(matrix, min, min, max).setColor(color)
            consumer.addVertex(matrix, min, min, min).setColor(color)

            consumer.addVertex(matrix, max, max, max).setColor(color)
            consumer.addVertex(matrix, max, max, min).setColor(color)
            consumer.addVertex(matrix, min, max, min).setColor(color)
            consumer.addVertex(matrix, min, max, max).setColor(color)
        }
    }
}
