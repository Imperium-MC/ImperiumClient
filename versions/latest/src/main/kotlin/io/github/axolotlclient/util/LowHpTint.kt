/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfigCommon.LowHpTintStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import kotlin.math.roundToInt

/**
 * low_hp_tint (Alpine parity): a red vignette (or flat tint) over the screen when the
 * camera entity's health drops below the configured threshold. The intensity ramps
 * linearly from nothing at the threshold to the tint colour's full alpha at 1 hp.
 *
 * Drawn at the head of `Hud.extractCameraOverlays`, the same section the vanilla
 * vignette/spyglass/pumpkin overlays live in, so screens-that-draw-the-world behave
 * consistently with the vanilla overlays.
 */
object LowHpTint {

    private val VIGNETTE_LOCATION: Identifier = Identifier.withDefaultNamespace("textures/misc/vignette.png")

    /**
     * 0 at (or above) the threshold, ramping to 1 at 1 hp. Pure function so the scaling
     * can be exercised directly.
     */
    @JvmStatic
    fun intensityFor(health: Float, threshold: Int): Float {
        if (health >= threshold) {
            return 0.0f
        }
        val span = maxOf(1.0f, threshold - 1.0f)
        return Mth.clamp((threshold - health) / span, 0.0f, 1.0f)
    }

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, cameraEntity: net.minecraft.world.entity.Entity) {
        if (!AxolotlClient.config().lowHpTint.get()) {
            return
        }
        val living = cameraEntity as? LivingEntity ?: return
        if (!living.isAlive) {
            return
        }
        if (cameraEntity is Player && (cameraEntity.isSpectator || cameraEntity.abilities.invulnerable)) {
            return
        }

        val factor = intensityFor(living.health, AxolotlClient.config().lowHpThreshold.get())
        if (factor <= 0.0f) {
            return
        }

        val base = AxolotlClient.config().lowHpTintColor.get().toInt()
        val strength = (ARGB.alpha(base) * factor).roundToInt()
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        if (AxolotlClient.config().lowHpTintStyle.get() == LowHpTintStyle.VIGNETTE) {
            // The VIGNETTE pipeline blends multiplicatively (dst *= 1 - texture*colour), so the
            // colour's channels are a per-channel darkening mask — the channels the tint colour
            // is strong in stay untouched, the rest fade to black at the screen edges. This is
            // the same mechanism the vanilla red world-border vignette uses.
            val mask = ARGB.color(
                255,
                darkening(strength, ARGB.red(base)),
                darkening(strength, ARGB.green(base)),
                darkening(strength, ARGB.blue(base))
            )
            graphics.blit(
                RenderPipelines.VIGNETTE, VIGNETTE_LOCATION, 0, 0, 0.0f, 0.0f,
                width, height, width, height, mask
            )
        } else {
            val tint = (strength shl 24) or (base and 0xFFFFFF)
            graphics.fill(0, 0, width, height, tint)
        }
    }

    /**
     * How much a channel is darkened: the stronger the tint colour is in this channel, the
     * less it is faded (a red tint keeps red and fades green/blue).
     */
    private fun darkening(strength: Int, colorChannel: Int): Int {
        return strength * (255 - colorChannel) / 255
    }
}
