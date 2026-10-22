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

package io.github.axolotlclient.modules.render

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.modules.AbstractCommonModule

class BeaconBeam : AbstractCommonModule() {
    private val showBeaconBeams = BooleanOption("showBeaconBeams", true)
    private val showEndGatewayBeams = BooleanOption("showEndGatewayBeams", true)

    private val beams = OptionCategory.create("beams")

    override fun init() {
        beams.add(showBeaconBeams)

        if (!BridgeVersion.V1_8.isCurrent()) {
            beams.add(showEndGatewayBeams)
        }

        AxolotlClientCommon.getInstance().getConfig().rendering.add(beams)
    }

    fun showBeam(endGateway: Boolean): Boolean =
        if (endGateway) showEndGatewayBeams.get() else showBeaconBeams.get()

    companion object {
        private val Instance = BeaconBeam()

        @JvmStatic
        fun getInstance(): BeaconBeam = Instance
    }
}
