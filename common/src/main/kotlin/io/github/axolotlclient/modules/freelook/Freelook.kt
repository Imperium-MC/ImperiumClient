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

package io.github.axolotlclient.modules.freelook

import java.util.ArrayDeque

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption
import io.github.axolotlclient.bridge.AxoPerspective
import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption

class Freelook : AbstractCommonModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", false)
    private val category = OptionCategory.create("freelook")
    private val mode = StringArrayOption(
        "mode", arrayOf("snap_perspective", "freelook"), "freelook"
    ) { FeatureDisablerCommon.getInstance()!!.update() }
    private val invert = BooleanOption("invert", false)
    private val perspective = EnumOption(
        "perspective", AxoPerspective::class.java, AxoPerspective.THIRD_PERSON_BACK
    )
    private val toggle = BooleanOption("toggle", "freelook.toggle.tooltip", false)
    private val perspectiveAlt = EnumOption(
        "perspective.alt", AxoPerspective::class.java,
        AxoPerspective.THIRD_PERSON_FRONT
    )
    private val toggleAlt = BooleanOption("toggle.alt", false)
    private val active = WrappedValue()
    private val activeAlt = WrappedValue()
    private var yaw = 0f
    private var pitch = 0f
    private val previousPerspectives: ArrayDeque<AxoPerspective> = ArrayDeque()

    override fun init() {
        category.add(enabled, mode, perspective, invert, toggle)
        category.add(perspectiveAlt, toggleAlt)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)
    }

    override fun tick() {
        if (!enabled.get() || client.`br$getScreen`() != null) return
        tickSet(toggle, KEY, perspective, active)
        tickSet(toggleAlt, KEY_ALT, perspectiveAlt, activeAlt)
    }

    private fun tickSet(toggle: BooleanOption, key: AxoKeybinding, perspective: EnumOption<AxoPerspective>, active: WrappedValue) {
        if (toggle.get()) {
            if (key.`br$consumeClick`()) {
                if (active.value) {
                    stop(active)
                } else {
                    start(perspective.get(), active)
                }
            }
        } else {
            if (key.`br$isPressed`()) {
                if (!active.value) {
                    start(perspective.get(), active)
                }
            } else if (active.value) {
                stop(active)
            }
        }
    }

    private fun stop(active: WrappedValue) {
        active.value = false
        client.`br$notifyLevelRenderer`()
        setAxoPerspective(previousPerspectives.pop())
    }

    private fun start(perspective: AxoPerspective, active: WrappedValue) {
        previousPerspectives.push(client.`br$getGameOptions`().`br$getCameraType`())
        active.value = true
        setAxoPerspective(perspective)

        var camera: AxoEntity? = client.`br$getCameraEntity`()

        if (camera == null) camera = client.`br$getPlayer`()
        if (camera == null) return

        yaw = camera.`br$getYaw`()
        pitch = camera.`br$getPitch`()
    }

    private fun setAxoPerspective(perspective: AxoPerspective) {
        client.`br$getGameOptions`().`br$setCameraType`(perspective)
    }

    fun consumeRotation(dx: Double, dy: Double): Boolean {
        if (!(active.value || activeAlt.value) || !enabled.get() || mode.get() != "freelook") return false

        var newDy = dy
        if (!invert.get()) newDy = -newDy
        if (client.`br$getGameOptions`().`br$getCameraType`().isMirrored ||
            client.`br$getGameOptions`().`br$getCameraType`().isFirstPerson
        ) {
            newDy *= -1
        }

        yaw += (dx * 0.15f).toFloat()
        pitch += (newDy * 0.15f).toFloat()

        if (pitch > 90) {
            pitch = 90f
        } else if (pitch < -90) {
            pitch = -90f
        }

        client.`br$notifyLevelRenderer`()
        return true
    }

    fun yaw(defaultValue: Float): Float {
        if (!(active.value || activeAlt.value) || !enabled.get() || mode.get() != "freelook") return defaultValue

        return yaw
    }

    fun pitch(defaultValue: Float): Float {
        if (!(active.value || activeAlt.value) || !enabled.get() || mode.get() != "freelook") return defaultValue

        return pitch
    }

    fun needsDisabling(): Boolean = mode.get() == "freelook"

    fun isActive(): Boolean = active.value || activeAlt.value

    private class WrappedValue {
        var value = false
    }

    companion object {
        private val INSTANCE = Freelook()
        private val KEY = AxoKeybinding.create(AxoKeys.KEY_V, "key.freelook")
        private val KEY_ALT = AxoKeybinding.create(null, "key.freelook.alt")

        @JvmStatic
        fun getInstance(): Freelook = INSTANCE
    }
}
