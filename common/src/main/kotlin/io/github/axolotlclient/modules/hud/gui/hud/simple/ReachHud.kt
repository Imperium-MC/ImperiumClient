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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.function.BiConsumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.entity.AxoEntity
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.math.Vec3
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

// https://github.com/AxolotlClient/AxolotlClient-mod/blob/4ae2678bfe9e0908be1a7a34e61e689c8005ae0a/src/main/java/io/github/axolotlclient/modules/hud/gui/hud/ReachDisplayHud.java
// https://github.com/DarkKronicle/KronHUD/blob/703b87a7c938ba25da9105d731b70d3bc66efd1e/src/main/java/io/github/darkkronicle/kronhud/gui/hud/simple/ReachHud.java
class ReachHud : SimpleTextHudEntry(true) {
    private val decimalPlaces = IntegerOption("decimalplaces", 0, 0, 15)

    private var currentDist: String? = null
    private var lastTime = 0L

    override fun init() {
        Events.PLAYER_ATTACK.register(
            BiConsumer { attacking, receiving ->
                val distance = getAttackDistance(attacking, receiving)

                val format = StringBuilder("0")
                if (decimalPlaces.get() > 0) {
                    format.append(".")
                    format.append("0".repeat(Math.max(0, decimalPlaces.get())))
                }
                val formatter = DecimalFormat(format.toString())
                formatter.setRoundingMode(RoundingMode.HALF_UP)
                currentDist = formatter.format(distance)
                lastTime = Platform.getMeasuringTimeMs()
            }
        )
    }

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(decimalPlaces)
        return options
    }

    override fun getValue(): String {
        if (currentDist == null) {
            return "0"
        } else if (lastTime + 2000 < Platform.getMeasuringTimeMs()) {
            currentDist = null
            return "0"
        }
        return currentDist!!
    }

    override fun getPlaceholderValue(): String = "3.45"

    override fun getLabel(): String = AxoI18n.translate("blocks")

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "reachhud")

        private fun getAttackDistance(attacking: AxoEntity, receiving: AxoEntity): Double {
            val recHitboxRadii = receiving.`br$getBoundingBoxHalfDimensions`()
            val attPos = attacking.`br$getEyePos`(1.0f)
                .sub(receiving.`br$getPos`().add(0.0, (receiving.`br$getHeight`() / 2f).toDouble(), 0.0)).negate()
            return Math.max(distToBox(attPos, recHitboxRadii), 0.0)
        }

        // The distance to a box defined by its radii (half side length). Also known as the distance field. Remember?
        // See https://iquilezles.org/articles/distfunctions/
        private fun distToBox(pos: Vec3, boxRadii: Vec3): Double {
            val q = pos.abs().sub(boxRadii)
            return q.max(0.0).len() + Math.min(Math.max(q.x(), Math.max(q.y(), q.z())), 0.0)
        }
    }
}
