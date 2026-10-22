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

package io.github.axolotlclient.modules.snaplook

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.modules.freelook.Freelook
import io.github.axolotlclient.util.options.ForceableBooleanOption
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Snap Look (Alpine parity): hold a key to instantly snap the camera 180 degrees
 * (look behind you); releasing the key returns to the exact pre-snap view.
 *
 * <p>This is a sibling of [Freelook] that reuses its camera rotation path
 * (the `CameraMixin` yaw hooks and the `EntityMixin` rotation
 * intercept) instead of adding its own mixins. While snapping, mouse rotation is
 * consumed and discarded, so the player entity never turns; that is what makes
 * the release restore the exact pre-snap yaw without any stored camera state.
 * Pitch is never modified.</p>
 *
 * <p>Composition with Freelook: while a Freelook is actively overriding the
 * camera (freelook mode + active), this module defers entirely, so holding both
 * keys cannot corrupt the view. If the snap key is still held when Freelook
 * releases, the snap activates on the next tick.</p>
 */
class SnapLook : AbstractCommonModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    private val category = OptionCategory.create("snaplook")
    private val returnOnRelease = BooleanOption("returnOnRelease", true)

    // Alpine parity: smooth_transition eases the 180-degree snap in and out over
    // animation_duration milliseconds (0 ms snaps instantly, the previous behaviour).
    @JvmField
    val smoothTransition = BooleanOption("smooth_transition", false)
    @JvmField
    val animationDuration = IntegerOption("animation_duration", 200, 0, 1000)
    private var active = false

    /** Offset the in/out animation started from, so an interrupted snap continues from it. */
    private var animationFromOffset = 0f
    private var animationStartMs = 0L

    override fun init() {
        category.add(enabled, returnOnRelease, smoothTransition, animationDuration)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
    }

    override fun tick() {
        if (!enabled.get() || client.`br$getScreen`() != null) return

        if (returnOnRelease.get()) {
            if (KEY.`br$isPressed`()) {
                if (!active) {
                    start()
                }
            } else if (active) {
                stop()
            }
        } else if (KEY.`br$consumeClick`()) {
            if (active) {
                stop()
            } else {
                start()
            }
        }
    }

    private fun start() {
        active = true
        animationFromOffset = snapOffset()
        animationStartMs = Platform.getMeasuringTimeMs()
        client.`br$notifyLevelRenderer`()
    }

    private fun stop() {
        active = false
        animationFromOffset = snapOffset()
        animationStartMs = Platform.getMeasuringTimeMs()
        client.`br$notifyLevelRenderer`()
    }

    /**
     * Mirrors [Freelook.consumeRotation] in the entity
     * rotation intercept: while the snap owns the camera (snapping, or still easing
     * back after release), the mouse deltas are consumed so the entity never turns.
     */
    fun consumeRotation(): Boolean = ownsCamera()

    /**
     * Applies the 180-degree offset to a camera yaw from the same hooks Freelook
     * overrides. No-op while an active Freelook (freelook mode) owns the camera.
     */
    fun yaw(defaultYaw: Float): Float {
        if (!ownsCamera()) {
            return defaultYaw
        }

        return defaultYaw + snapOffset()
    }

    /**
     * Whether this module currently overrides the camera yaw: the key is held, or a
     * smooth transition is still easing the previous snap out.
     */
    fun isSnapping(): Boolean = ownsCamera()

    private fun ownsCamera(): Boolean =
        enabled.get() && !freelookOverridesCamera() &&
            (active || abs(snapOffset()) > 0.5f)

    /**
     * The current extra yaw in degrees. With smooth_transition off this is the plain
     * 0/180 step; with it on, a cosine ease between the offset the animation started
     * from and its target (180 while snapping, 0 after release), so an interrupted
     * snap continues from wherever it got to.
     */
    private fun snapOffset(): Float {
        if (!smoothTransition.get()) {
            return if (active) SNAP_DEGREES else 0.0f
        }
        val duration = animationDuration.get()
        if (duration <= 0) {
            return if (active) SNAP_DEGREES else 0.0f
        }
        val progress = min(1.0f, (Platform.getMeasuringTimeMs() - animationStartMs) / duration.toFloat())
        val eased = (1.0f - cos(progress * Math.PI).toFloat()) / 2.0f
        val target = if (active) SNAP_DEGREES else 0.0f
        return animationFromOffset + (target - animationFromOffset) * eased
    }

    companion object {
        private const val SNAP_DEGREES = 180.0f
        private val INSTANCE = SnapLook()
        private val KEY = AxoKeybinding.create(null, "key.snaplook")

        @JvmStatic
        fun getInstance(): SnapLook = INSTANCE

        private fun freelookOverridesCamera(): Boolean {
            val freelook = Freelook.getInstance()
            return freelook.enabled.get() && freelook.isActive() && freelook.needsDisabling()
        }
    }
}
