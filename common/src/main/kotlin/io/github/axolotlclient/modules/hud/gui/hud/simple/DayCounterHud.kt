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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry

class DayCounterHud : SimpleTextHudEntry() {
    override fun getPlaceholderValue(): String = AxoI18n.translate("daycounterhud.days", 35)

    override fun getValue(): String {
        if (client.`br$getWorld`() == null) {
            return getPlaceholderValue()
        }

        return AxoI18n.translate("daycounterhud.days", client.`br$getWorld`()!!.`br$getTimeOfDay`() / 24000)
    }

    override fun getId(): AxoIdentifier = ID

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "daycounterhud")
    }
}
