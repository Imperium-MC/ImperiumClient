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

package io.github.axolotlclient.modules.zoom

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.util.MathUtil
import kotlin.math.abs
import kotlin.math.max

/**
 * Based on
 * <a href="https://github.com/LogicalGeekBoy/logical_zoom/blob/master/src/main/java/com/logicalgeekboy/logical_zoom/LogicalZoom.java">Logical Zoom</a>
 */

class Zoom : AbstractCommonModule() {
    val key: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_C, "key.zoom")
    private val zoomDivisor = FloatOption("zoomDivisor", 4f, 1f, 16f)
    private val zoomSpeed = FloatOption("zoomSpeed", 7.5f, 1f, 10f)
    private val zoomScrolling = BooleanOption("zoomScrolling", false)
    private val decreaseSensitivity = BooleanOption("decreaseSensitivity", true)
    private val smoothCamera = BooleanOption("smoothCamera", false)
    private val toggle = BooleanOption("toggle", false)
    private var toggled = false
    var active = false
        private set
    private var originalSensitivity: Double? = null
    private var originalSmoothCamera = false
    private var targetFactor = 1f
    private var divisor = 0f
    private var lastAnimatedFactor = 1f
    private var animatedFactor = 1f
    private var lastReturnedFov = 0.0
    @JvmField
    val zoom: OptionCategory = OptionCategory.create("zoom")

    fun getFov(current: Float, tickDelta: Float): Float {
        val result =
            current * (if (zoomSpeed.get() == 10f) targetFactor else MathUtil.lerp(tickDelta, lastAnimatedFactor, animatedFactor))

        if (lastReturnedFov != 0.0 && lastReturnedFov != result.toDouble()) {
            client.`br$notifyLevelRenderer`()
        }
        lastReturnedFov = result.toDouble()

        return result
    }

    fun update() {
        if (shouldStart()) {
            start()
        } else if (shouldStop()) {
            stop()
        }
    }

    private fun shouldStart(): Boolean = keyHeld() && !active

    private fun start() {
        active = true
        setDivisor(zoomDivisor.get())
        setOptions()
    }

    private fun shouldStop(): Boolean = !keyHeld() && active

    private fun stop() {
        active = false
        targetFactor = 1f
        restoreOptions()
    }

    private fun keyHeld(): Boolean {
        if (toggle.get()) {
            if (key.`br$consumeClick`()) {
                toggled = !toggled
            }
            return toggled
        }
        return key.`br$isPressed`()
    }

    private fun setDivisor(value: Float) {
        divisor = value
        targetFactor = 1f / value
    }

    fun setOptions() {
        originalSensitivity = client.`br$getGameOptions`().`br$getSensitivity`()

        if (smoothCamera.get()) {
            originalSmoothCamera = client.`br$getGameOptions`().`br$getSmoothCamera`()
            client.`br$getGameOptions`().`br$setSmoothCamera`(true)
        }

        updateSensitivity()
    }

    fun restoreOptions() {
        client.`br$getGameOptions`().`br$setSensitivity`(originalSensitivity!!)
        client.`br$getGameOptions`().`br$setSmoothCamera`(originalSmoothCamera)
    }

    private fun updateSensitivity() {
        if (decreaseSensitivity.get()) {
            client.`br$getGameOptions`().`br$setSensitivity`(originalSensitivity!! / (divisor * divisor))
        }
    }

    fun scroll(amount: Double): Boolean {
        if (active && zoomScrolling.get() && amount != 0.0) {
            setDivisor(max(1.0, (divisor + (amount / abs(amount))).toDouble()).toFloat())
            updateSensitivity()
            return true
        }

        return false
    }

    override fun init() {
        zoom.add(zoomDivisor, zoomSpeed, zoomScrolling, decreaseSensitivity, smoothCamera, toggle)

        AxolotlClientCommon.getInstance().getConfig().rendering.add(zoom)

        active = false

        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.zoom.increase")
            .`br$registerOnConsumeClick`(Runnable { scroll((zoomSpeed.get() / 2f).toDouble()) })
        AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "key.zoom.decrease")
            .`br$registerOnConsumeClick`(Runnable { scroll((-zoomSpeed.get() / 2f).toDouble()) })
    }

    override fun tick() {
        lastAnimatedFactor = animatedFactor
        animatedFactor += (targetFactor - animatedFactor) * (zoomSpeed.get() / 10f)
    }

    companion object {
        private val Instance = Zoom()

        @JvmStatic
        fun getInstance(): Zoom = Instance
    }
}
