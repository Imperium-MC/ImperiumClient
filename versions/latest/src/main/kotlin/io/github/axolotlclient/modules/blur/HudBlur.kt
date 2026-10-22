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

package io.github.axolotlclient.modules.blur

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.resource.CrossFrameResourcePool
import com.mojang.renderpearl.api.textures.GpuTextureView
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.mixin.PostChainAccessor
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.util.MathUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.resources.Identifier

/**
 * Background blur for HUD elements: renders a blurred copy of the world (before any GUI is drawn)
 * into the persistent `blurred` target of the `axolotlclient:hud_blur` post chain, which
 * BoxHudEntry then blits behind its background when the entry asks for it.
 *
 * <p>The chain is fully idle (no passes, no allocation beyond the already-loaded program) unless the
 * master option is on AND at least one enabled HUD entry wants blur this frame. The chain runs at
 * the strongest radius requested that frame — the master radius or any entry's radius, whichever
 * is larger — because there is only one shared blur per frame; each entry samples the same frame
 * with the region of the screen behind it.
 */
class HudBlur : AbstractModule() {
    @JvmField
    val enabled = BooleanOption("enabled", false)
    @JvmField
    val radius = IntegerOption("radius", "hudBlur.radius.tooltip", 8, 1, 24)
    @JvmField
    val category: OptionCategory = OptionCategory.create("hudBlur")
    private val postChainId: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "hud_blur")

    override fun init() {
        category.add(enabled, radius)

        AxolotlClient.config().rendering.add(category)
    }

    /**
     * Runs once per frame right before the GUI is drawn: the world (and only the world) is in the
     * main target here, so the blurred copy never contains HUD or menu pixels.
     */
    @Suppress("DEPRECATION")
    fun render(pool: CrossFrameResourcePool) {
        blurredView = null

        if (!enabled.get() || client.level == null || !Minecraft.getInstance().isGameLoadFinished) {
            return
        }

        var max = MathUtil.clamp(radius.get(), 1, 24)
        for (entry in HudManagerCommon.getInstance()!!.getEntries()) {
            if (entry is BoxHudEntry && entry.isEnabled() && !entry.isHidden() && entry.wantsBlur()) {
                max = maxOf(max, entry.blurRadius())
            }
        }
        if (max <= 0) {
            return
        }

        blurRadius = max.toFloat()

        try {
            val chain: PostChain? = client.getShaderManager().getPostChain(postChainId, LevelTargetBundle.MAIN_TARGETS)
            if (chain != null) {
                val target = client.gameRenderer.mainRenderTarget()
                chain.process(target, pool)
                // Uniform is set in PostPassMixin
                framesProcessed++
                blurredView = findBlurredTarget(chain)
            }
        } catch (e: Exception) {
            // A post chain failing to compile on an exotic setup must never take the frame down.
            if (!failureLogged) {
                failureLogged = true
                AxolotlClientCommon.getInstance().logger.warn(
                    "Failed to run the HUD blur post chain, disabling HUD blur reporting (entries keep their flat background).",
                    e
                )
            }
        }
    }

    companion object {
        private var blurRadius = 8f
        private var blurredView: GpuTextureView? = null
        private var framesProcessed = 0
        private var failureLogged = false
        private val Instance = HudBlur()

        @JvmStatic
        fun getInstance(): HudBlur = Instance

        /**
         * The radius written into the `HudBlurConfig` uniform block each frame (see PostPassMixin).
         */
        @JvmStatic
        fun radius(): Float = blurRadius

        /**
         * The persistent blurred frame of this frame's pass; `null` while the pass is idle or
         * failed, in which case HUD entries draw no blur (just their plain background).
         */
        @JvmStatic
        fun getBlurredView(): GpuTextureView? = blurredView

        /**
         * Frames the blur chain actually processed; gametest evidence that the pass runs and stops.
         */
        @JvmStatic
        fun framesProcessed(): Int = framesProcessed

        private fun findBlurredTarget(chain: PostChain): GpuTextureView? {
            for (entry in (chain as PostChainAccessor).`axolotlclient$persistentTargets`().entries) {
                val target: RenderTarget = entry.value
                if ("blurred" == entry.key.getPath() && target != null) {
                    return target.getColorTextureView()
                }
            }
            return null
        }
    }
}
